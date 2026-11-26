use std::net::Ipv4Addr;

/// Checks if an IPv4 address belongs to the Tailscale CGNAT subnet (100.64.0.0/10).
pub fn is_tailscale_ip(ip: Ipv4Addr) -> bool {
    let octets = ip.octets();
    // 100.64.0.0 to 100.127.255.255
    octets[0] == 100 && (octets[1] >= 64 && octets[1] <= 127)
}

/// Checks if an IPv4 address belongs to standard private LAN subnets (RFC 1918).
pub fn is_private_lan_ip(ip: Ipv4Addr) -> bool {
    let octets = ip.octets();
    octets[0] == 10
        || (octets[0] == 172 && (16..=31).contains(&octets[1]))
        || (octets[0] == 192 && octets[1] == 168)
}

/// Checks if an address is strictly allowed for server binding.
pub fn is_allowed_bind_ip(ip: Ipv4Addr) -> bool {
    ip.is_unspecified() || ip.is_loopback() || is_tailscale_ip(ip) || is_private_lan_ip(ip)
}

/// Discover active local network IPv4 address (e.g. Wi-Fi / Ethernet).
pub fn discover_local_ipv4() -> Option<Ipv4Addr> {
    let socket = std::net::UdpSocket::bind("0.0.0.0:0").ok()?;
    socket.connect("8.8.8.8:80").ok()?;
    match socket.local_addr().ok()?.ip() {
        std::net::IpAddr::V4(ipv4) => Some(ipv4),
        _ => None,
    }
}

/// Ask Tailscale for its currently assigned IPv4 address.
pub async fn discover_tailscale_ipv4() -> Option<Ipv4Addr> {
    let candidates = [
        std::env::var_os("ProgramFiles")
            .map(std::path::PathBuf::from)
            .map(|root| root.join("Tailscale").join("tailscale.exe")),
        std::env::var_os("ProgramFiles(x86)")
            .map(std::path::PathBuf::from)
            .map(|root| root.join("Tailscale").join("tailscale.exe")),
        std::env::var_os("LOCALAPPDATA")
            .map(std::path::PathBuf::from)
            .map(|root| root.join("Tailscale").join("tailscale.exe")),
        Some(std::path::PathBuf::from("tailscale.exe")),
    ];

    for candidate in candidates.into_iter().flatten() {
        let mut command = tokio::process::Command::new(&candidate);
        command.args(["ip", "--4"]).kill_on_drop(true);
        #[cfg(windows)]
        command.creation_flags(0x0800_0000); // CREATE_NO_WINDOW
        if let Ok(Ok(output)) =
            tokio::time::timeout(std::time::Duration::from_millis(500), command.output()).await
            && output.status.success()
        {
            let found = String::from_utf8_lossy(&output.stdout)
                .lines()
                .filter_map(|line| line.trim().parse::<Ipv4Addr>().ok())
                .find(|ip| is_tailscale_ip(*ip));
            if found.is_some() {
                return found;
            }
        }
    }

    None
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_tailscale_ip_range() {
        assert!(is_tailscale_ip(Ipv4Addr::new(100, 64, 0, 1)));
        assert!(is_tailscale_ip(Ipv4Addr::new(100, 104, 22, 18)));
        assert!(is_tailscale_ip(Ipv4Addr::new(100, 127, 255, 254)));

        assert!(!is_tailscale_ip(Ipv4Addr::new(100, 63, 255, 255)));
        assert!(!is_tailscale_ip(Ipv4Addr::new(100, 128, 0, 1)));
        assert!(!is_tailscale_ip(Ipv4Addr::new(192, 168, 1, 100)));
        assert!(!is_tailscale_ip(Ipv4Addr::new(0, 0, 0, 0)));
    }

    #[test]
    fn test_allowed_bind_ip() {
        assert!(is_allowed_bind_ip(Ipv4Addr::new(127, 0, 0, 1)));
        assert!(is_allowed_bind_ip(Ipv4Addr::new(100, 100, 50, 2)));
        assert!(is_allowed_bind_ip(Ipv4Addr::new(192, 168, 0, 1)));
        assert!(is_allowed_bind_ip(Ipv4Addr::new(10, 0, 0, 1)));
        assert!(is_allowed_bind_ip(Ipv4Addr::new(0, 0, 0, 0)));
        assert!(!is_allowed_bind_ip(Ipv4Addr::new(8, 8, 8, 8)));
    }
}

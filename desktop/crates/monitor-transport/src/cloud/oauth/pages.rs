fn format_oauth_success_html(email: &str) -> String {
    let email_escaped = email
        .replace('&', "&amp;")
        .replace('<', "&lt;")
        .replace('>', "&gt;");
    format!(
        r#"<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Monitor Cloud - Sign-In Successful</title>
<style>
body {{ background: #0b0f19; color: #f8fafc; font-family: system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0; }}
.box {{ background: #131b2e; border: 1px solid #1e293b; border-radius: 20px; padding: 40px; text-align: center; box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.7); max-width: 440px; }}
.badge {{ background: #065f46; color: #34d399; font-weight: 600; font-size: 13px; padding: 6px 14px; border-radius: 9999px; display: inline-block; margin-bottom: 16px; }}
h1 {{ font-size: 22px; margin: 0 0 12px; color: #38bdf8; }}
p {{ font-size: 14px; color: #94a3b8; line-height: 1.6; margin: 0 0 24px; }}
.account {{ font-weight: 600; color: #38bdf8; word-break: break-all; }}
.note {{ font-size: 13px; color: #64748b; border-top: 1px solid #1e293b; padding-top: 16px; }}
</style>
</head>
<body>
<div class="box">
<div class="badge">&#10003; Connected to Cloud</div>
<h1>Signed in to Monitor Cloud</h1>
<p>Authenticated as <span class="account">{}</span>.<br>Your Windows workstation is authenticated and synced to your private Cloudflare broker.</p>
<div class="note">You can safely close this browser tab now.</div>
</div>
</body>
</html>"#,
        email_escaped
    )
}

fn format_oauth_error_html(error_message: &str) -> String {
    let err_escaped = error_message
        .replace('&', "&amp;")
        .replace('<', "&lt;")
        .replace('>', "&gt;");
    format!(
        r#"<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Monitor Cloud - Sign-In Error</title>
<style>
body {{ background: #0b0f19; color: #f8fafc; font-family: system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0; }}
.box {{ background: #131b2e; border: 1px solid #7f1d1d; border-radius: 20px; padding: 40px; text-align: center; box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.7); max-width: 440px; }}
.badge {{ background: #7f1d1d; color: #f87171; font-weight: 600; font-size: 13px; padding: 6px 14px; border-radius: 9999px; display: inline-block; margin-bottom: 16px; }}
h1 {{ font-size: 22px; margin: 0 0 12px; color: #f87171; }}
p {{ font-size: 14px; color: #94a3b8; line-height: 1.6; margin: 0 0 24px; }}
.error-box {{ background: #0b0f19; border: 1px solid #334155; border-radius: 8px; padding: 10px 14px; font-family: monospace; font-size: 12px; color: #fca5a5; word-break: break-all; margin-bottom: 20px; }}
.note {{ font-size: 13px; color: #64748b; border-top: 1px solid #1e293b; padding-top: 16px; }}
</style>
</head>
<body>
<div class="box">
<div class="badge">&#10007; Sign-In Failed</div>
<h1>Authentication Failed</h1>
<p>Google Cloud authentication could not be completed.</p>
<div class="error-box">{}</div>
<div class="note">Please close this tab and retry from the Monitor system tray.</div>
</div>
</body>
</html>"#,
        err_escaped
    )
}

/// Host-side (Responder) Noise IK Handshake Handler.
pub struct HostHandshakeResponder {
    handshake: snow::HandshakeState,
    remote_static: Option<[u8; 32]>,
}

impl std::fmt::Debug for HostHandshakeResponder {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("HostHandshakeResponder")
            .field("remote_static", &self.remote_static.map(|k| hex_encode(&k)))
            .finish()
    }
}

impl HostHandshakeResponder {
    /// Prepares a new responder session using the host's static private key.
    pub fn new(host_keypair: &NoiseKeypair, prologue: &[u8]) -> Result<Self, CoreError> {
        let params: NoiseParams = NOISE_PATTERN
            .parse()
            .map_err(|e| CoreError::Internal(format!("Invalid Noise pattern: {e}")))?;
        let mut builder = Builder::new(params);
        builder = builder.local_private_key(&host_keypair.private_key);
        builder = builder.prologue(prologue);

        let handshake = builder
            .build_responder()
            .map_err(|e| CoreError::Internal(format!("Failed to build Noise responder: {e}")))?;

        Ok(Self {
            handshake,
            remote_static: None,
        })
    }

    /// Processes handshake message 1 from initiator (Android viewer).
    /// Extracts the remote static public key for host-side authorization.
    pub fn read_handshake_msg1(&mut self, msg1: &[u8]) -> Result<Vec<u8>, CoreError> {
        let mut payload = vec![0u8; msg1.len()];
        let len = self
            .handshake
            .read_message(msg1, &mut payload)
            .map_err(|e| CoreError::Internal(format!("Noise handshake msg1 failed: {e}")))?;
        payload.truncate(len);

        // Capture peer static public key (initiator's K2)
        if let Some(remote_pk) = self.handshake.get_remote_static() {
            let mut pk = [0u8; 32];
            if remote_pk.len() == 32 {
                pk.copy_from_slice(remote_pk);
                self.remote_static = Some(pk);
            }
        }

        Ok(payload)
    }

    /// Returns the remote peer's static public key if successfully authenticated.
    pub fn remote_static_public_key(&self) -> Option<[u8; 32]> {
        self.remote_static
    }

    /// Generates handshake message 2, completing the handshake and transitioning into Transport mode.
    pub fn write_handshake_msg2(
        self,
        payload: &[u8],
        session_id: String,
        epoch: u64,
    ) -> Result<(Vec<u8>, SecureSession), CoreError> {
        self.write_handshake_msg2_with_data_channel_keys(payload, session_id, epoch, [0; 32], [0; 32])
    }

    pub fn write_handshake_msg2_with_data_channel_keys(
        mut self,
        payload: &[u8],
        session_id: String,
        epoch: u64,
        send_key: [u8; 32],
        recv_key: [u8; 32],
    ) -> Result<(Vec<u8>, SecureSession), CoreError> {
        let mut out = vec![0u8; 1024];
        let len = self
            .handshake
            .write_message(payload, &mut out)
            .map_err(|e| CoreError::Internal(format!("Noise handshake msg2 failed: {e}")))?;
        out.truncate(len);

        let transport = self
            .handshake
            .into_transport_mode()
            .map_err(|e| CoreError::Internal(format!("Failed to switch to transport mode: {e}")))?;

        let session = SecureSession::new(transport, session_id, epoch, self.remote_static)
            .with_data_channel_keys(send_key, recv_key);
        Ok((out, session))
    }
}

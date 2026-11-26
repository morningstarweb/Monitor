/// Viewer-side (Initiator) Noise IK Handshake Handler (used in Android and integration tests).
pub struct ViewerHandshakeInitiator {
    handshake: snow::HandshakeState,
    remote_static: [u8; 32],
}

impl std::fmt::Debug for ViewerHandshakeInitiator {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("ViewerHandshakeInitiator")
            .field("remote_static", &hex_encode(&self.remote_static))
            .finish()
    }
}

impl ViewerHandshakeInitiator {
    /// Prepares a new initiator session targeting the host's known static public key.
    pub fn new(
        viewer_keypair: &NoiseKeypair,
        host_public_key: &[u8; 32],
        prologue: &[u8],
    ) -> Result<Self, CoreError> {
        let params: NoiseParams = NOISE_PATTERN
            .parse()
            .map_err(|e| CoreError::Internal(format!("Invalid Noise pattern: {e}")))?;
        let mut builder = Builder::new(params);
        builder = builder.local_private_key(&viewer_keypair.private_key);
        builder = builder.remote_public_key(host_public_key);
        builder = builder.prologue(prologue);

        let handshake = builder
            .build_initiator()
            .map_err(|e| CoreError::Internal(format!("Failed to build Noise initiator: {e}")))?;

        Ok(Self {
            handshake,
            remote_static: *host_public_key,
        })
    }

    /// Generates handshake message 1 to send to the host.
    pub fn write_handshake_msg1(&mut self, payload: &[u8]) -> Result<Vec<u8>, CoreError> {
        let mut out = vec![0u8; 1024];
        let len = self
            .handshake
            .write_message(payload, &mut out)
            .map_err(|e| CoreError::Internal(format!("Noise handshake msg1 write failed: {e}")))?;
        out.truncate(len);
        Ok(out)
    }

    /// Processes handshake message 2 from the host and transitions into Transport mode.
    pub fn read_handshake_msg2(
        mut self,
        msg2: &[u8],
        session_id: String,
        epoch: u64,
    ) -> Result<(Vec<u8>, SecureSession), CoreError> {
        let mut payload = vec![0u8; msg2.len()];
        let len = self
            .handshake
            .read_message(msg2, &mut payload)
            .map_err(|e| CoreError::Internal(format!("Noise handshake msg2 read failed: {e}")))?;
        payload.truncate(len);

        let transport = self
            .handshake
            .into_transport_mode()
            .map_err(|e| CoreError::Internal(format!("Failed to switch to transport mode: {e}")))?;

        let session = SecureSession::new(transport, session_id, epoch, Some(self.remote_static));
        Ok((payload, session))
    }
}

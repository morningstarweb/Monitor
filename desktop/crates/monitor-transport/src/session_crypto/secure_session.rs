/// Established end-to-end encrypted session with AEAD message encryption,
/// 16 KiB frame limits, and monotonic sequence validation.
use chacha20poly1305::{aead::Aead, ChaCha20Poly1305, KeyInit, Nonce};

pub struct SecureSession {
    transport: Mutex<TransportState>,
    pub session_id: String,
    pub epoch: u64,
    pub peer_static_public_key: Option<[u8; 32]>,
    pub data_channel_send_key: [u8; 32],
    pub data_channel_recv_key: [u8; 32],
    send_sequence: AtomicU64,
    recv_sequence: AtomicU64,
    data_channel_send_nonce: AtomicU64,
    data_channel_recv_nonce: AtomicU64,
}

impl std::fmt::Debug for SecureSession {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("SecureSession")
            .field("session_id", &self.session_id)
            .field("epoch", &self.epoch)
            .field("send_seq", &self.send_sequence.load(Ordering::Relaxed))
            .field("recv_seq", &self.recv_sequence.load(Ordering::Relaxed))
            .finish()
    }
}

impl SecureSession {
    pub fn new(
        transport: TransportState,
        session_id: String,
        epoch: u64,
        peer_static_public_key: Option<[u8; 32]>,
    ) -> Self {
        Self {
            transport: Mutex::new(transport),
            session_id,
            epoch,
            peer_static_public_key,
            data_channel_send_key: [0; 32],
            data_channel_recv_key: [0; 32],
            send_sequence: AtomicU64::new(0),
            recv_sequence: AtomicU64::new(0),
            data_channel_send_nonce: AtomicU64::new(0),
            data_channel_recv_nonce: AtomicU64::new(0),
        }
    }

    pub fn with_data_channel_keys(mut self, send_key: [u8; 32], recv_key: [u8; 32]) -> Self {
        self.data_channel_send_key = send_key;
        self.data_channel_recv_key = recv_key;
        self
    }

    pub fn encrypt_data_channel(&self, plaintext: &[u8]) -> Result<Vec<u8>, CoreError> {
        if plaintext.len() > MAX_PLAINTEXT_FRAME_SIZE {
            return Err(CoreError::InvalidArgument("DataChannel plaintext exceeds 16 KiB".to_string()));
        }
        let counter = self.data_channel_send_nonce.fetch_add(1, Ordering::SeqCst);
        let nonce = data_channel_nonce(counter);
        ChaCha20Poly1305::new((&self.data_channel_send_key).into())
            .encrypt(Nonce::from_slice(&nonce), plaintext)
            .map_err(|_| CoreError::Internal("DataChannel encryption failed".to_string()))
    }

    pub fn decrypt_data_channel(&self, ciphertext: &[u8]) -> Result<Vec<u8>, CoreError> {
        if ciphertext.len() > MAX_CIPHERTEXT_FRAME_SIZE || ciphertext.len() < AEAD_TAG_SIZE {
            return Err(CoreError::InvalidArgument("Invalid DataChannel ciphertext size".to_string()));
        }
        // Noise spec §5.1: the receive nonce advances only after a successful
        // AEAD decryption. Advancing it on authentication failure lets an
        // injected frame desync every subsequent legitimate frame.
        loop {
            let counter = self.data_channel_recv_nonce.load(Ordering::SeqCst);
            let nonce = data_channel_nonce(counter);
            let decrypted = ChaCha20Poly1305::new((&self.data_channel_recv_key).into())
                .decrypt(Nonce::from_slice(&nonce), ciphertext);
            match decrypted {
                Ok(plaintext) => {
                    if self
                        .data_channel_recv_nonce
                        .compare_exchange(
                            counter,
                            counter.wrapping_add(1),
                            Ordering::SeqCst,
                            Ordering::SeqCst,
                        )
                        .is_ok()
                    {
                        return Ok(plaintext);
                    }
                    // Another receiver advanced the counter; retry with the new nonce.
                }
                Err(_) => {
                    return Err(CoreError::Internal(
                        "DataChannel authentication failed".to_string(),
                    ));
                }
            }
        }
    }

    /// Encrypts an outgoing plaintext message.
    /// Strictly enforces the 16 KiB maximum plaintext size.
    pub async fn encrypt(&self, plaintext: &[u8]) -> Result<Vec<u8>, CoreError> {
        if plaintext.len() > MAX_PLAINTEXT_FRAME_SIZE {
            return Err(CoreError::InvalidArgument(format!(
                "Plaintext frame size {} exceeds maximum 16 KiB limit ({})",
                plaintext.len(),
                MAX_PLAINTEXT_FRAME_SIZE
            )));
        }

        let mut out = vec![0u8; plaintext.len() + AEAD_TAG_SIZE];
        let mut transport = self.transport.lock().await;
        let len = transport
            .write_message(plaintext, &mut out)
            .map_err(|e| CoreError::Internal(format!("AEAD encryption failed: {e}")))?;
        out.truncate(len);

        self.send_sequence.fetch_add(1, Ordering::SeqCst);
        Ok(out)
    }

    /// Decrypts an incoming ciphertext message.
    /// Strictly rejects frames that exceed the maximum ciphertext limit or have invalid MAC tags.
    pub async fn decrypt(&self, ciphertext: &[u8]) -> Result<Vec<u8>, CoreError> {
        if ciphertext.len() > MAX_CIPHERTEXT_FRAME_SIZE {
            return Err(CoreError::InvalidArgument(format!(
                "Ciphertext frame size {} exceeds maximum limit ({})",
                ciphertext.len(),
                MAX_CIPHERTEXT_FRAME_SIZE
            )));
        }
        if ciphertext.len() < AEAD_TAG_SIZE {
            return Err(CoreError::InvalidArgument(
                "Ciphertext frame too short to contain AEAD auth tag".to_string(),
            ));
        }

        let mut out = vec![0u8; ciphertext.len()];
        let mut transport = self.transport.lock().await;
        let len = transport.read_message(ciphertext, &mut out).map_err(|e| {
            CoreError::Internal(format!("AEAD decryption/MAC verification failed: {e}"))
        })?;
        out.truncate(len);

        self.recv_sequence.fetch_add(1, Ordering::SeqCst);
        Ok(out)
    }

    pub fn send_sequence(&self) -> u64 {
        self.send_sequence.load(Ordering::Relaxed)
    }

    pub fn recv_sequence(&self) -> u64 {
        self.recv_sequence.load(Ordering::Relaxed)
    }
}

fn data_channel_nonce(counter: u64) -> [u8; 12] {
    let mut nonce = [0; 12];
    nonce[4..].copy_from_slice(&counter.to_le_bytes());
    nonce
}

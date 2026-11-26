#[derive(Debug, Clone, PartialEq, Eq)]
pub struct NoiseKeypair {
    pub private_key: [u8; 32],
    pub public_key: [u8; 32],
}

impl NoiseKeypair {
    /// Generates a cryptographically random X25519 keypair for Noise protocol.
    pub fn generate() -> Result<Self, CoreError> {
        let params: NoiseParams = NOISE_PATTERN
            .parse()
            .map_err(|e| CoreError::Internal(format!("Invalid Noise pattern: {e}")))?;
        let builder = Builder::new(params);
        let keypair = builder
            .generate_keypair()
            .map_err(|e| CoreError::Internal(format!("Failed to generate Noise keypair: {e}")))?;

        let mut priv_arr = [0u8; 32];
        let mut pub_arr = [0u8; 32];
        if keypair.private.len() != 32 || keypair.public.len() != 32 {
            return Err(CoreError::Internal(
                "Unexpected key length from snow".to_string(),
            ));
        }
        priv_arr.copy_from_slice(&keypair.private);
        pub_arr.copy_from_slice(&keypair.public);

        Ok(Self {
            private_key: priv_arr,
            public_key: pub_arr,
        })
    }
}

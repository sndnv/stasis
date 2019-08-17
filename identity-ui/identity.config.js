export default {
    identity_api_url: process.env.IDENTITY_UI_API_URL,
    cookies: {
        authentication_token: 'stasis-identity-jwt',
        context: 'stasis-identity-context',
        secure: false
    },
    authentication: {
        client_id: process.env.IDENTITY_UI_AUTH_CLIENT_ID,
        redirect_uri: process.env.IDENTITY_UI_AUTH_REDIRECT_URI,
        scope: "urn:stasis:identity:audience:manage-identity",
        state_size: process.env.IDENTITY_UI_AUTH_STATE_SIZE || 64,
        code_verifier_size: process.env.IDENTITY_UI_AUTH_VERIFIER_SIZE || 64,
        secret_derivation: {
            enabled: process.env.IDENTITY_UI_AUTH_DERIVATION_ENABLED || false,
            salt_prefix: process.env.IDENTITY_UI_AUTH_DERIVATION_SALT || 'identity',
            iterations: process.env.IDENTITY_UI_AUTH_DERIVATION_ITERATIONS || 10000,
            key_size: process.env.IDENTITY_UI_AUTH_DERIVATION_KEY_SIZE || 64,
        }
    }
};

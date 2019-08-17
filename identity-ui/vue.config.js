const DefinePlugin = require('webpack').DefinePlugin;

module.exports = {
  devServer: {
    proxy: {
      '^/api': {
        target: 'http://identity:9090',
        pathRewrite: {
          '^/api': '/'
        }
      },
      '^/oauth': {
        target: 'http://identity:9090'
      },
      '^/jwks': {
        target: 'http://identity:9090'
      }
    }
  },
  pages: {
    manage: 'src/pages/manage/main.js',
    authorize: 'src/pages/authorize/main.js'
  },
  configureWebpack: {
    plugins: [
      new DefinePlugin({
        'process.env.IDENTITY_UI_API_URL': JSON.stringify(process.env.IDENTITY_UI_API_URL),
        'process.env.IDENTITY_UI_AUTH_CLIENT_ID': JSON.stringify(process.env.IDENTITY_UI_AUTH_CLIENT_ID),
        'process.env.IDENTITY_UI_AUTH_REDIRECT_URI': JSON.stringify(process.env.IDENTITY_UI_AUTH_REDIRECT_URI),
        'process.env.IDENTITY_UI_AUTH_STATE_SIZE': JSON.stringify(process.env.IDENTITY_UI_AUTH_STATE_SIZE),
        'process.env.IDENTITY_UI_AUTH_VERIFIER_SIZE': JSON.stringify(process.env.IDENTITY_UI_AUTH_VERIFIER_SIZE),
        'process.env.IDENTITY_UI_AUTH_DERIVATION_ENABLED': JSON.stringify(process.env.IDENTITY_UI_AUTH_DERIVATION_ENABLED),
        'process.env.IDENTITY_UI_AUTH_DERIVATION_SALT': JSON.stringify(process.env.IDENTITY_UI_AUTH_DERIVATION_SALT),
        'process.env.IDENTITY_UI_AUTH_DERIVATION_ITERATIONS': JSON.stringify(process.env.IDENTITY_UI_AUTH_DERIVATION_ITERATIONS),
        'process.env.IDENTITY_UI_AUTH_DERIVATION_KEY_SIZE': JSON.stringify(process.env.IDENTITY_UI_AUTH_DERIVATION_KEY_SIZE),
      })
    ]
  },
}

const DefinePlugin = require('webpack').DefinePlugin;

module.exports = {
  devServer: {
    https: true,
    proxy: {
      '^/api': {
        target: 'https://identity:10000',
        pathRewrite: {
          '^/api': '/'
        }
      },
      '^/oauth': {
        target: 'https://identity:10000'
      },
      '^/jwks': {
        target: 'https://identity:10000'
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
        'process.env.VUE_APP_STASIS_IDENTITY_UI_API_URL': JSON.stringify(process.env.VUE_APP_STASIS_IDENTITY_UI_API_URL),
        'process.env.VUE_APP_STASIS_IDENTITY_UI_AUTH_CLIENT_ID': JSON.stringify(process.env.VUE_APP_STASIS_IDENTITY_UI_AUTH_CLIENT_ID),
        'process.env.VUE_APP_STASIS_IDENTITY_UI_AUTH_REDIRECT_URI': JSON.stringify(process.env.VUE_APP_STASIS_IDENTITY_UI_AUTH_REDIRECT_URI),
        'process.env.VUE_APP_STASIS_IDENTITY_UI_AUTH_STATE_SIZE': JSON.stringify(process.env.VUE_APP_STASIS_IDENTITY_UI_AUTH_STATE_SIZE),
        'process.env.VUE_APP_STASIS_IDENTITY_UI_AUTH_VERIFIER_SIZE': JSON.stringify(process.env.VUE_APP_STASIS_IDENTITY_UI_AUTH_VERIFIER_SIZE),
        'process.env.VUE_APP_STASIS_IDENTITY_UI_AUTH_DERIVATION_ENABLED': JSON.stringify(process.env.VUE_APP_STASIS_IDENTITY_UI_AUTH_DERIVATION_ENABLED),
        'process.env.VUE_APP_STASIS_IDENTITY_UI_AUTH_DERIVATION_SALT': JSON.stringify(process.env.VUE_APP_STASIS_IDENTITY_UI_AUTH_DERIVATION_SALT),
        'process.env.VUE_APP_STASIS_IDENTITY_UI_AUTH_DERIVATION_ITERATIONS': JSON.stringify(process.env.VUE_APP_STASIS_IDENTITY_UI_AUTH_DERIVATION_ITERATIONS),
        'process.env.VUE_APP_STASIS_IDENTITY_UI_AUTH_DERIVATION_KEY_SIZE': JSON.stringify(process.env.VUE_APP_STASIS_IDENTITY_UI_AUTH_DERIVATION_KEY_SIZE),
      })
    ]
  },
}

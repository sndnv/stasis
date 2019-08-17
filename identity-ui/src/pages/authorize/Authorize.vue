<template>
  <div id="authorize" class="authorize-container">
    <div class="row">
      <div class="col s12 offset-m2 m8">
        <div class="card grey lighten-4 z-depth-3">
          <div class="card-content">
            <span class="card-title center-align">identity</span>
            <form>
              <div class="input-field col s12">
                <i class="material-icons prefix">person_outline</i>
                <input
                  id="username"
                  type="text"
                  v-bind:class="{ 'invalid': errors.username }"
                  v-model="input.username"
                  :title="errors.username"
                />
                <label for="username" class="center-align">Username</label>
                <span v-if="errors.username" class="helper-text" :data-error="errors.username" />
              </div>
              <div class="input-field col s12">
                <i class="material-icons prefix">lock_outline</i>
                <input
                  id="password"
                  type="password"
                  v-bind:class="{ 'invalid': errors.password }"
                  v-model="input.password"
                  :title="errors.password"
                />
                <label for="password" class="center-align">Password</label>
                <span v-if="errors.password" class="helper-text" :data-error="errors.password" />
              </div>
              <div class="row center-align" v-if="requested_scopes.length > 0">
                Requested scopes:
                <div
                  v-for="current in requested_scopes"
                  v-bind:key="current"
                  class="chip teal lighten-1 white-text"
                >{{ current }}</div>
              </div>
              <div class="row center-align" v-else>
                <div class="chip lighten-1 red white-text">No scopes requested</div>
              </div>
              <div class="row center-align">
                <button
                  id="authorize-button"
                  class="btn waves-effect waves-light orange"
                  v-on:click.prevent="authorize()"
                  :disabled="requested_scopes.length <= 0 || logging_in"
                >Authorize</button>
              </div>
            </form>
            <div class="card-action center-align login-logo">
              <img width="40" height="40" src="@/assets/logo.svg" />
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style>
.authorize-container {
  height: 100%;
  display: flex;
  align-items: center;
}

.login-logo {
  padding-bottom: 0px !important;
}
</style>

<script>
import oauth from "@/api/oauth";
import M from "materialize-css/dist/js/materialize.min.js";

const urn_prefix = "urn:stasis:identity:audience:";

export default {
  name: "Authorize",
  props: ["params"],
  created: function() {
    this.requested_params = this.params;
    this.requested_scopes = (this.params.scope || "")
      .split(" ")
      .map(s => s.split(urn_prefix).pop())
      .map(s => s.trim())
      .filter(String);

    this.$router.replace(this.$route.path);
  },
  data: function() {
    return {
      logging_in: false,
      requested_params: {},
      requested_scopes: [],
      input: {
        username: "",
        password: ""
      },
      errors: {
        username: "",
        password: ""
      }
    };
  },
  methods: {
    authorize: function() {
      this.logging_in = true;
      this.errors.username = "";
      this.errors.password = "";

      if (!this.input.username) {
        this.errors.username = "Username cannot be empty";
      }

      if (!this.input.password) {
        this.errors.password = "Password cannot be empty";
      }

      if (!this.errors.username && !this.errors.password) {
        oauth
          .authorize(
            this.input.username,
            this.input.password,
            this.requested_params
          )
          .then(response => {
            if (response.success) {
              const icon = '<i class="material-icons green-text">check</i>';
              const message = `${icon} Authorization successful`;
              M.toast({ html: message });

              window.location.href = response.data.redirect_uri;
            } else {
              const component = this;
              setTimeout(function() {
                component.logging_in = false;
              }, 1000);

              if (response.error == "access_denied") {
                const icon = '<i class="material-icons red-text">close</i>';
                const message = `Invalid credentials specified`;
                this.errors.username = message;
                this.errors.password = message;
                M.toast({ html: `${icon} ${message}` });
              } else {
                const icon = '<i class="material-icons red-text">close</i>';
                const message = `${icon} ${response.error_description} (${response.error})`;
                M.toast({ html: message });
              }
            }
          });
      } else {
        this.logging_in = false;
      }
    }
  }
};
</script>

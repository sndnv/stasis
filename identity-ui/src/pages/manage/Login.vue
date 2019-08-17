<template>
  <div id="login" class="login-container">
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
              <div class="row center-align">
                <button
                  id="login-button"
                  class="btn waves-effect waves-light"
                  v-on:click.prevent="login()"
                  :disabled="logging_in"
                >Log in</button>
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
.login-container {
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

export default {
  name: "Login",
  props: {},
  data: function() {
    return {
      logging_in: false,
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
    login: function() {
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
        oauth.login(this.input.username, this.input.password).then(response => {
          if (response.success) {
            const icon = '<i class="material-icons green-text">check</i>';
            const message = `${icon} Successfully logged in as [${this.input.username}]`;
            M.toast({ html: message });

            this.$router.push(this.$route.query.redirect || { name: "home" });
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

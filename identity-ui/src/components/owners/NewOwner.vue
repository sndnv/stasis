<template>
  <div class="col s12 m6 l4 xl3">
    <div class="card green lighten-5 hoverable">
      <div class="card-content">
        <div class="row">
          <div class="input-field col s12">
            <input
              class="black-text"
              id="new-owner-username"
              type="text"
              v-bind:class="{ 'invalid': errors.new_owner.username }"
              v-model.trim="input.new_owner.username"
              :title="errors.new_owner.username"
            />
            <label class="active">Username</label>
            <span
              v-if="errors.new_owner.username"
              class="helper-text"
              :data-error="errors.new_owner.username"
            />
          </div>
        </div>
        <div class="row">
          <div class="input-field col s12">
            <input
              class="black-text"
              id="new-owner-allowed-scopes"
              type="text"
              v-bind:class="{ 'invalid': errors.new_owner.allowedScopes }"
              v-model.trim="input.new_owner.allowedScopes"
              :title="errors.new_owner.allowedScopes"
            />
            <label class="active">Allowed Scopes</label>
            <span
              v-if="errors.new_owner.allowedScopes"
              class="helper-text"
              :data-error="errors.new_owner.allowedScopes"
            />
          </div>
        </div>
        <div class="row">
          <div class="input-field col s12">
            <input
              class="black-text"
              id="new-owner-password"
              type="password"
              v-bind:class="{ 'invalid': errors.new_owner.rawPassword }"
              v-model.trim="input.new_owner.rawPassword"
              :title="errors.new_owner.rawPassword"
            />
            <label class="active">Password</label>
            <span
              v-if="errors.new_owner.rawPassword"
              class="helper-text"
              :data-error="errors.new_owner.rawPassword"
            />
          </div>
        </div>
        <div class="row">
          <div class="input-field col s12">
            <input
              class="black-text"
              id="new-owner-password-confirm"
              type="password"
              v-bind:class="{ 'invalid': errors.new_owner.passwordConfirm }"
              v-model.trim="input.new_owner.passwordConfirm"
              :title="errors.new_owner.passwordConfirm"
            />
            <label class="active">Password (confirm)</label>
            <span
              v-if="errors.new_owner.passwordConfirm"
              class="helper-text"
              :data-error="errors.new_owner.passwordConfirm"
            />
          </div>
        </div>
      </div>
      <div class="card-action">
        <a
          class="btn-small waves-effect waves-light right create-button"
          title="Create"
          v-on:click.prevent="create()"
          :disabled="creating"
        >
          <i class="material-icons">add</i>
        </a>
      </div>
    </div>
  </div>
</template>

<script>
import oauth from "@/api/oauth";
import requests from "@/api/requests";
import M from 'materialize-css/dist/js/materialize.min.js';

export default {
  name: "NewOwner",
  data: function() {
    return {
      creating: false,
      input: {
        new_owner: {
          username: "",
          allowedScopes: "",
          rawPassword: "",
          passwordConfirm: ""
        }
      },
      errors: {
        new_owner: {
          username: "",
          allowedScopes: "",
          rawPassword: "",
          passwordConfirm: ""
        }
      }
    };
  },
  methods: {
    create: function() {
      this.creating = true;

      const errors = this.errors.new_owner;

      errors.username = "";
      errors.allowedScopes = "";
      errors.rawPassword = "";
      errors.passwordConfirm = "";

      if (!this.input.new_owner.username) {
        errors.username = "Username cannot be empty";
      }

      if (!this.input.new_owner.allowedScopes) {
        errors.allowedScopes = "Allowed scopes cannot be empty";
      }

      if (!this.input.new_owner.rawPassword) {
        errors.rawPassword = "Password cannot be empty";
      }

      if (
        !this.input.new_owner.passwordConfirm ||
        this.input.new_owner.rawPassword != this.input.new_owner.passwordConfirm
      ) {
        errors.passwordConfirm = "Passwords must be provided and must match";
      }

      if (
        !errors.username &&
        !errors.allowedScopes &&
        !errors.rawPassword &&
        !errors.passwordConfirm
      ) {
        process_request_data(this.input.new_owner).then(
          new_owner_request => {
            requests.post_owner(new_owner_request).then(response => {
              this.creating = false;
              if (response.success) {
                this.$emit("owner-created", {
                  username: this.input.new_owner.username,
                  allowedScopes: this.input.new_owner.allowedScopes,
                  active: true,
                  is_new: true
                });
                this.input.new_owner.username = "";
                this.input.new_owner.allowedScopes = "";
                this.input.new_owner.rawPassword = "";
                this.input.new_owner.passwordConfirm = "";
              } else {
                const icon = '<i class="material-icons red-text">close</i>';
                const message = `${icon} ${response.error}`;
                M.toast({ html: message });
              }
            });
          },
          () => {
            const icon = '<i class="material-icons red-text">close</i>';
            const message = `${icon} Failed to derive authentication password`;
            M.toast({ html: message });
          }
        );
      } else {
        this.creating = false;
      }
    }
  }
};

function process_request_data({ username, allowedScopes, rawPassword }) {
  return oauth
    .derive_password(rawPassword, oauth.derive_salt(username))
    .then(derived_password => {
      if (Object.prototype.toString.call(allowedScopes) === "[object String]") {
        const split_scopes = allowedScopes
          .split(",")
          .map(s => s.trim())
          .filter(String);

        return {
          username,
          allowedScopes: split_scopes,
          rawPassword: derived_password
        };
      } else {
        return { username, allowedScopes, rawPassword: derived_password };
      }
    });
}
</script>

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
              v-bind:class="{ 'invalid': errors.new_owner.allowed_scopes }"
              v-model.trim="input.new_owner.allowed_scopes"
              :title="errors.new_owner.allowed_scopes"
            />
            <label class="active">Allowed Scopes</label>
            <span
              v-if="errors.new_owner.allowed_scopes"
              class="helper-text"
              :data-error="errors.new_owner.allowed_scopes"
            />
          </div>
        </div>
        <div class="row">
          <div class="input-field col s12">
            <input
              class="black-text"
              id="new-owner-password"
              type="password"
              v-bind:class="{ 'invalid': errors.new_owner.raw_password }"
              v-model.trim="input.new_owner.raw_password"
              :title="errors.new_owner.raw_password"
            />
            <label class="active">Password</label>
            <span
              v-if="errors.new_owner.raw_password"
              class="helper-text"
              :data-error="errors.new_owner.raw_password"
            />
          </div>
        </div>
        <div class="row">
          <div class="input-field col s12">
            <input
              class="black-text"
              id="new-owner-password-confirm"
              type="password"
              v-bind:class="{ 'invalid': errors.new_owner.password_confirm }"
              v-model.trim="input.new_owner.password_confirm"
              :title="errors.new_owner.password_confirm"
            />
            <label class="active">Password (confirm)</label>
            <span
              v-if="errors.new_owner.password_confirm"
              class="helper-text"
              :data-error="errors.new_owner.password_confirm"
            />
          </div>
        </div>
        <div class="row">
          <div class="input-field col s12">
            <input
              class="black-text"
              id="new-owner-subject"
              type="text"
              v-model.trim="input.new_owner.subject"
              placeholder="<default is username>"
            />
            <label class="active">Subject</label>
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
          allowed_scopes: "",
          raw_password: "",
          password_confirm: "",
          subject: ""
        }
      },
      errors: {
        new_owner: {
          username: "",
          allowed_scopes: "",
          raw_password: "",
          password_confirm: ""
        }
      }
    };
  },
  methods: {
    create: function() {
      this.creating = true;

      const errors = this.errors.new_owner;

      errors.username = "";
      errors.allowed_scopes = "";
      errors.raw_password = "";
      errors.password_confirm = "";

      if (!this.input.new_owner.username) {
        errors.username = "Username cannot be empty";
      }

      if (!this.input.new_owner.allowed_scopes) {
        errors.allowed_scopes = "Allowed scopes cannot be empty";
      }

      if (!this.input.new_owner.raw_password) {
        errors.raw_password = "Password cannot be empty";
      }

      if (
        !this.input.new_owner.password_confirm ||
        this.input.new_owner.raw_password != this.input.new_owner.password_confirm
      ) {
        errors.password_confirm = "Passwords must be provided and must match";
      }

      if (
        !errors.username &&
        !errors.allowed_scopes &&
        !errors.raw_password &&
        !errors.password_confirm
      ) {
        process_request_data(this.input.new_owner).then(
          new_owner_request => {
            requests.post_owner(new_owner_request).then(response => {
              this.creating = false;
              if (response.success) {
                this.$emit("owner-created", {
                  username: this.input.new_owner.username,
                  allowed_scopes: this.input.new_owner.allowed_scopes,
                  active: true,
                  is_new: true,
                  subject: this.input.new_owner.subject
                });
                this.input.new_owner.username = "";
                this.input.new_owner.allowed_scopes = "";
                this.input.new_owner.raw_password = "";
                this.input.new_owner.password_confirm = "";
                this.input.new_owner.subject = "";
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

function process_request_data({ username, allowed_scopes, raw_password, subject }) {
  return oauth
    .derive_password(raw_password, oauth.derive_salt(username))
    .then(derived_password => {
      if (Object.prototype.toString.call(allowed_scopes) === "[object String]") {
        const split_scopes = allowed_scopes
          .split(",")
          .map(s => s.trim())
          .filter(String);

        return {
          ...{ username, allowed_scopes: split_scopes, raw_password: derived_password },
          ...(subject === "" ? {} : {subject})
        };
      } else {
        return {
          ...{ username, allowed_scopes, raw_password: derived_password },
          ...(subject === "" ? {} : {subject})
        };
      }
    });
}
</script>

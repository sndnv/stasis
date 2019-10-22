<template>
  <div class="col s12 m6 l4 xl3">
    <div class="card green lighten-5 hoverable">
      <div class="card-content">
        <div class="row">
          <div class="input-field col s12">
            <input
              class="black-text"
              id="new-client-redirect-uri"
              type="text"
              v-bind:class="{ 'invalid': errors.new_client.redirect_uri }"
              v-model.trim="input.new_client.redirect_uri"
              :title="errors.new_client.redirect_uri"
            />
            <label class="active">Redirect URI</label>
            <span
              v-if="errors.new_client.redirect_uri"
              class="helper-text"
              :data-error="errors.new_client.redirect_uri"
            />
          </div>
        </div>
        <div class="row">
          <div class="input-field col s12">
            <input
              class="black-text"
              id="new-client-token-expiration"
              type="number"
              min="1"
              v-bind:class="{ 'invalid': errors.new_client.token_expiration }"
              v-model.number="input.new_client.token_expiration"
              :title="errors.new_client.token_expiration"
            />
            <label class="active">Token Expiration (seconds)</label>
            <span
              v-if="errors.new_client.token_expiration"
              class="helper-text"
              :data-error="errors.new_client.token_expiration"
            />
          </div>
        </div>
        <div class="row">
          <div class="input-field col s12">
            <input
              class="black-text"
              id="new-client-secret"
              type="password"
              v-bind:class="{ 'invalid': errors.new_client.raw_secret }"
              v-model.trim="input.new_client.raw_secret"
              :title="errors.new_client.raw_secret"
            />
            <label class="active">Secret</label>
            <span
              v-if="errors.new_client.raw_secret"
              class="helper-text"
              :data-error="errors.new_client.raw_secret"
            />
          </div>
        </div>
        <div class="row">
          <div class="input-field col s12">
            <input
              class="black-text"
              id="new-client-secret-confirm"
              type="password"
              v-bind:class="{ 'invalid': errors.new_client.secret_confirm }"
              v-model.trim="input.new_client.secret_confirm"
              :title="errors.new_client.secret_confirm"
            />
            <label class="active">Secret (confirm)</label>
            <span
              v-if="errors.new_client.secret_confirm"
              class="helper-text"
              :data-error="errors.new_client.secret_confirm"
            />
          </div>
        </div>
        <div class="row">
          <div class="input-field col s12">
            <input
              class="black-text"
              id="new-client-subject"
              type="text"
              v-model.trim="input.new_client.subject"
              placeholder="<default is client identifier>"
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
import requests from "@/api/requests";
import M from 'materialize-css/dist/js/materialize.min.js';

export default {
  name: "NewClient",
  data: function() {
    return {
      creating: false,
      input: {
        new_client: {
          redirect_uri: "",
          token_expiration: "",
          raw_secret: "",
          secret_confirm: "",
          subject: ""
        }
      },
      errors: {
        new_client: {
          redirect_uri: "",
          token_expiration: "",
          raw_secret: "",
          secret_confirm: ""
        }
      }
    };
  },
  methods: {
    create: function() {
      this.creating = true;

      const errors = this.errors.new_client;

      errors.redirect_uri = "";
      errors.token_expiration = "";
      errors.raw_secret = "";
      errors.secret_confirm = "";

      if (!this.input.new_client.redirect_uri) {
        errors.redirect_uri = "Redirect URI cannot be empty";
      }

      if (
        !this.input.new_client.token_expiration ||
        this.input.new_client.token_expiration <= 0
      ) {
        errors.token_expiration = "Token expiration must be a positive number";
      }

      if (!this.input.new_client.raw_secret) {
        errors.raw_secret = "Secret cannot be empty";
      }

      if (
        !this.input.new_client.secret_confirm ||
        this.input.new_client.raw_secret != this.input.new_client.secret_confirm
      ) {
        errors.secret_confirm = "Secrets must be provided and must match";
      }

      if (
        !errors.redirect_uri &&
        !errors.token_expiration &&
        !errors.raw_secret &&
        !errors.secret_confirm
      ) {
        requests
          .post_client(extract_request_fields(this.input.new_client))
          .then(response => {
            this.creating = false;
            if (response.success) {
              this.$emit("client-created", {
                id: response.data.client,
                redirect_uri: this.input.new_client.redirect_uri,
                token_expiration: this.input.new_client.token_expiration,
                active: true,
                is_new: true,
                subject: this.input.new_client.subject
              });
              this.input.new_client.redirect_uri = "";
              this.input.new_client.token_expiration = "";
              this.input.new_client.raw_secret = "";
              this.input.new_client.secret_confirm = "";
              this.input.new_client.subject = "";
            } else {
              const icon = '<i class="material-icons red-text">close</i>';
              const message = `${icon} ${response.error}`;
              M.toast({ html: message });
            }
          });
      } else {
        this.creating = false;
      }
    }
  }
};

function extract_request_fields({ redirect_uri, token_expiration, raw_secret, subject }) {
  return {
    ...{ redirect_uri, token_expiration, raw_secret },
    ...(subject === "" ? {} : {subject})
  };
}
</script>

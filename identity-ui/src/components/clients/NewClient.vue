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
              v-bind:class="{ 'invalid': errors.new_client.redirectUri }"
              v-model.trim="input.new_client.redirectUri"
              :title="errors.new_client.redirectUri"
            />
            <label class="active">Redirect URI</label>
            <span
              v-if="errors.new_client.redirectUri"
              class="helper-text"
              :data-error="errors.new_client.redirectUri"
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
              v-bind:class="{ 'invalid': errors.new_client.tokenExpiration }"
              v-model.number="input.new_client.tokenExpiration"
              :title="errors.new_client.tokenExpiration"
            />
            <label class="active">Token Expiration (seconds)</label>
            <span
              v-if="errors.new_client.tokenExpiration"
              class="helper-text"
              :data-error="errors.new_client.tokenExpiration"
            />
          </div>
        </div>
        <div class="row">
          <div class="input-field col s12">
            <input
              class="black-text"
              id="new-client-secret"
              type="password"
              v-bind:class="{ 'invalid': errors.new_client.rawSecret }"
              v-model.trim="input.new_client.rawSecret"
              :title="errors.new_client.rawSecret"
            />
            <label class="active">Secret</label>
            <span
              v-if="errors.new_client.rawSecret"
              class="helper-text"
              :data-error="errors.new_client.rawSecret"
            />
          </div>
        </div>
        <div class="row">
          <div class="input-field col s12">
            <input
              class="black-text"
              id="new-client-secret-confirm"
              type="password"
              v-bind:class="{ 'invalid': errors.new_client.secretConfirm }"
              v-model.trim="input.new_client.secretConfirm"
              :title="errors.new_client.secretConfirm"
            />
            <label class="active">Secret (confirm)</label>
            <span
              v-if="errors.new_client.secretConfirm"
              class="helper-text"
              :data-error="errors.new_client.secretConfirm"
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
import requests from "@/api/requests";
import M from 'materialize-css/dist/js/materialize.min.js';

export default {
  name: "NewClient",
  data: function() {
    return {
      creating: false,
      input: {
        new_client: {
          redirectUri: "",
          tokenExpiration: "",
          rawSecret: "",
          secretConfirm: ""
        }
      },
      errors: {
        new_client: {
          redirectUri: "",
          tokenExpiration: "",
          rawSecret: "",
          secretConfirm: ""
        }
      }
    };
  },
  methods: {
    create: function() {
      this.creating = true;

      const errors = this.errors.new_client;

      errors.redirectUri = "";
      errors.tokenExpiration = "";
      errors.rawSecret = "";
      errors.secretConfirm = "";

      if (!this.input.new_client.redirectUri) {
        errors.redirectUri = "Redirect URI cannot be empty";
      }

      if (
        !this.input.new_client.tokenExpiration ||
        this.input.new_client.tokenExpiration <= 0
      ) {
        errors.tokenExpiration = "Token expiration must be a positive number";
      }

      if (!this.input.new_client.rawSecret) {
        errors.rawSecret = "Secret cannot be empty";
      }

      if (
        !this.input.new_client.secretConfirm ||
        this.input.new_client.rawSecret != this.input.new_client.secretConfirm
      ) {
        errors.secretConfirm = "Secrets must be provided and must match";
      }

      if (
        !errors.redirectUri &&
        !errors.tokenExpiration &&
        !errors.rawSecret &&
        !errors.secretConfirm
      ) {
        requests
          .post_client(extract_request_fields(this.input.new_client))
          .then(response => {
            this.creating = false;
            if (response.success) {
              this.$emit("client-created", {
                id: response.data.client,
                redirectUri: this.input.new_client.redirectUri,
                tokenExpiration: this.input.new_client.tokenExpiration,
                active: true,
                is_new: true
              });
              this.input.new_client.redirectUri = "";
              this.input.new_client.tokenExpiration = "";
              this.input.new_client.rawSecret = "";
              this.input.new_client.secretConfirm = "";
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

function extract_request_fields({ redirectUri, tokenExpiration, rawSecret }) {
  return { redirectUri, tokenExpiration, rawSecret };
}
</script>

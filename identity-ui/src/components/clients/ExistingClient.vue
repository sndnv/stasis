<template>
  <div class="col s12 m6 l4 xl3">
    <div
      class="card hoverable existing"
      :class="client.active ? (get_current_client() === client.id ? 'yellow lighten-5' : '') : 'red lighten-5'"
      :title="get_current_client() === client.id ? 'Current Client' : ''"
      v-on:mouseover="show_controls"
      v-on:mouseout="hide_controls"
    >
      <span v-if="client.is_new" class="new badge" />
      <div class="card-content">
        <div class="row">
          <div class="input-field col s12">
            <input class="black-text client-id" type="text" :value="client.id" readonly />
            <label class="active">Identifier</label>
          </div>
        </div>
        <div class="row">
          <div class="input-field col s12">
            <input class="black-text" type="text" :value="client.redirectUri" readonly />
            <label class="active">Redirect URI</label>
          </div>
        </div>
        <div v-if="editing_secret">
          <div class="row">
            <div class="input-field col s12">
              <input
                class="black-text secret"
                type="password"
                v-bind:class="{ 'invalid': errors.existing_client.rawSecret }"
                v-model.trim="input.existing_client.rawSecret"
                :title="errors.existing_client.rawSecret"
              />
              <label class="active">Secret</label>
              <span
                v-if="errors.existing_client.rawSecret"
                class="helper-text"
                :data-error="errors.existing_client.rawSecret"
              />
            </div>
          </div>
          <div class="row">
            <div class="input-field col s12">
              <input
                class="black-text secret-confirm"
                type="password"
                v-bind:class="{ 'invalid': errors.existing_client.secretConfirm }"
                v-model.trim="input.existing_client.secretConfirm"
                :title="errors.existing_client.secretConfirm"
              />
              <label class="active">Secret (confirm)</label>
              <span
                v-if="errors.existing_client.secretConfirm"
                class="helper-text"
                :data-error="errors.existing_client.secretConfirm"
              />
            </div>
          </div>
        </div>
        <div v-else>
          <div class="row">
            <div class="input-field col s12">
              <input
                class="black-text token-expiration"
                type="number"
                min="1"
                v-model.number="client.tokenExpiration"
                :readonly="!editing"
                v-bind:class="{ 'invalid': errors.editing.tokenExpiration }"
                :title="errors.editing.tokenExpiration"
              />
              <label class="active">Token Expiration (seconds)</label>
              <span
                v-if="errors.editing.tokenExpiration"
                class="helper-text"
                :data-error="errors.editing.tokenExpiration"
              />
            </div>
          </div>
          <div class="row">
            <div class="input-field col s12">
              <label class="active">Active</label>
              <div class="switch">
                <label>
                  No
                  <input
                    :disabled="!editing || get_current_client() === client.id"
                    type="checkbox"
                    v-model="client.active"
                  />
                  <span class="lever"></span>
                  Yes
                </label>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div class="card-action" v-show="controls_showing || editing">
        <div v-if="editing">
          <a
            class="btn-small waves-effect waves-light green right save-button"
            title="Save"
            v-on:click.prevent="save_edit"
          >
            <i class="material-icons">check</i>
          </a>
          <a
            class="btn-small waves-effect waves-light red right cancel-button"
            title="Cancel"
            v-on:click.prevent="cancel_edit"
          >
            <i class="material-icons">close</i>
          </a>
        </div>
        <div v-else>
          <a
            v-show="get_current_client() !== client.id"
            class="btn-small waves-effect waves-light red right delete-button"
            :title="'Delete [' + client.id +']'"
            v-on:click.prevent="remove"
          >
            <i class="material-icons">delete</i>
          </a>
          <a
            class="btn-small waves-effect waves-light orange right edit-button"
            :title="'Edit [' + client.id +']'"
            v-on:click.prevent="edit"
          >
            <i class="material-icons">edit</i>
          </a>
          <a
            class="btn-small waves-effect waves-light teal lighten-1 right edit-secret-button"
            :title="'Edit secret for [' + client.id +']'"
            v-on:click.prevent="edit_secret"
          >
            <i class="material-icons">lock_outline</i>
          </a>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import oauth from "@/api/oauth";
import requests from "@/api/requests";
import M from 'materialize-css/dist/js/materialize.min.js';

export default {
  name: "ExistingClient",
  props: ["client"],
  data: function() {
    return {
      controls_showing: false,
      editing: false,
      editing_secret: false,
      input: {
        existing_client: {
          rawSecret: "",
          secretConfirm: ""
        }
      },
      errors: {
        editing: {
          tokenExpiration: ""
        },
        existing_client: {
          rawSecret: "",
          secretConfirm: ""
        }
      },
      original: Object.assign({}, this.client)
    };
  },
  methods: {
    get_current_client: function() {
      return oauth.get_context().client;
    },
    show_controls: function() {
      this.controls_showing = true;
    },
    hide_controls: function() {
      this.controls_showing = false;
    },
    remove: function() {
      if (confirm(`Are you sure you want to delete client [${this.client.id}]?`))
        requests.delete_client(this.client.id).then(response => {
          if (response.success) {
            this.$emit("client-deleted", this.client.id);
          } else {
            const icon = '<i class="material-icons red-text">close</i>';
            const message = `${icon} ${response.error}`;
            M.toast({ html: message });
          }
        });
    },
    edit: function() {
      this.editing = true;
    },
    edit_secret: function() {
      this.editing = true;
      this.editing_secret = true;
    },
    save_edit: function() {
      if (this.editing_secret) {
        this.update_client_credentials();
      } else {
        this.update_client();
      }
    },
    cancel_edit: function() {
      for (var attr in this.original) {
        this.client[attr] = this.original[attr];
      }

      this.input.existing_client.rawSecret = "";
      this.input.existing_client.secretConfirm = "";

      this.errors.editing.tokenExpiration = "";
      this.errors.existing_client.rawSecret = "";
      this.errors.existing_client.secretConfirm = "";

      this.editing = false;
      this.editing_secret = false;
    },
    update_client: function() {
      this.errors.editing.tokenExpiration = "";

      const updatable = extract_updatable_fields(this.client);
      const original = extract_updatable_fields(this.original);

      const client_changed =
        JSON.stringify(updatable) !== JSON.stringify(original);

      if (client_changed) {
        if (!updatable.tokenExpiration || updatable.tokenExpiration <= 0) {
          this.errors.editing.tokenExpiration =
            "Token expiration must be a positive number";
        }

        if (!this.errors.editing.tokenExpiration) {
          requests.put_client(this.client.id, updatable).then(response => {
            if (response.success) {
              this.original = Object.assign({}, this.client);
              this.editing = false;
            } else {
              const icon = '<i class="material-icons red-text">close</i>';
              const message = `${icon} ${response.error}`;
              M.toast({ html: message });
            }
          });
        }
      } else {
        this.editing = false;
      }
    },
    update_client_credentials: function() {
      this.errors.existing_client.rawSecret = "";
      this.errors.existing_client.secretConfirm = "";

      if (!this.input.existing_client.rawSecret) {
        this.errors.existing_client.rawSecret = "Secret cannot be empty";
      }

      if (
        !this.input.existing_client.secretConfirm ||
        this.input.existing_client.rawSecret !=
          this.input.existing_client.secretConfirm
      ) {
        this.errors.existing_client.secretConfirm =
          "Secrets must be provided and must match";
      }

      if (
        !this.errors.existing_client.rawSecret &&
        !this.errors.existing_client.secretConfirm
      ) {
        requests
          .put_client_credentials(this.client.id, {
            rawSecret: this.input.existing_client.rawSecret
          })
          .then(response => {
            if (response.success) {
              this.input.existing_client.rawSecret = "";
              this.input.existing_client.secretConfirm = "";
              this.editing = false;
              this.editing_secret = false;
            } else {
              const icon = '<i class="material-icons red-text">close</i>';
              const message = `${icon} ${response.error}`;
              M.toast({ html: message });
            }
          });
      }
    }
  }
};

function extract_updatable_fields({ tokenExpiration, active }) {
  return { tokenExpiration, active };
}
</script>

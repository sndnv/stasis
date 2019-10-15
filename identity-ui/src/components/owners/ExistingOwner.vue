<template>
  <div class="col s12 m6 l4 xl3">
    <div
      class="card hoverable existing"
      :class="owner.active ? (get_current_user() === owner.username ? 'yellow lighten-5' : '') : 'red lighten-5'"
      :title="get_current_user() === owner.username ? 'Current User' : ''"
      v-on:mouseover="show_controls"
      v-on:mouseout="hide_controls"
    >
      <span v-if="owner.is_new" class="new badge" />
      <div class="card-content">
        <div class="row">
          <div class="input-field col s12">
            <input class="black-text owner-username" type="text" :value="owner.username" readonly />
            <label class="active">Username</label>
          </div>
        </div>
        <div class="row">
          <div class="input-field col s12">
            <input class="black-text owner-subject" type="text" :value="owner.subject || owner.username" readonly />
            <label class="active">Subject</label>
          </div>
        </div>
        <div v-if="editing_password">
          <div class="row">
            <div class="input-field col s12">
              <input
                class="black-text password"
                type="password"
                v-bind:class="{ 'invalid': errors.existing_owner.rawPassword }"
                v-model.trim="input.existing_owner.rawPassword"
                :title="errors.existing_owner.rawPassword"
              />
              <label class="active">Password</label>
              <span
                v-if="errors.existing_owner.rawPassword"
                class="helper-text"
                :data-error="errors.existing_owner.rawPassword"
              />
            </div>
          </div>
          <div class="row">
            <div class="input-field col s12">
              <input
                class="black-text password-confirm"
                type="password"
                v-bind:class="{ 'invalid': errors.existing_owner.passwordConfirm }"
                v-model.trim="input.existing_owner.passwordConfirm"
                :title="errors.existing_owner.passwordConfirm"
              />
              <label class="active">Password (confirm)</label>
              <span
                v-if="errors.existing_owner.passwordConfirm"
                class="helper-text"
                :data-error="errors.existing_owner.passwordConfirm"
              />
            </div>
          </div>
        </div>
        <div v-else>
          <div class="row">
            <div class="input-field col s12">
              <input
                class="black-text allowed-scopes"
                type="text"
                v-model.trim="owner.allowedScopes"
                :readonly="!editing"
                v-bind:class="{ 'invalid': errors.editing.allowedScopes }"
                :title="errors.editing.allowedScopes"
              />
              <label class="active">Allowed Scopes</label>
              <span
                v-if="errors.editing.allowedScopes"
                class="helper-text"
                :data-error="errors.editing.allowedScopes"
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
                    :disabled="!editing || get_current_user() === owner.username"
                    type="checkbox"
                    v-model="owner.active"
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
            v-show="get_current_user() !== owner.username"
            class="btn-small waves-effect waves-light red right delete-button"
            :title="'Delete [' + owner.username +']'"
            v-on:click.prevent="remove"
          >
            <i class="material-icons">delete</i>
          </a>
          <a
            class="btn-small waves-effect waves-light orange right edit-button"
            :title="'Edit [' + owner.username +']'"
            v-on:click.prevent="edit"
          >
            <i class="material-icons">edit</i>
          </a>
          <a
            class="btn-small waves-effect waves-light teal lighten-1 right edit-password-button"
            :title="'Edit password for [' + owner.username +']'"
            v-on:click.prevent="edit_password"
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
  name: "ExistingOwner",
  props: ["owner"],
  data: function() {
    return {
      controls_showing: false,
      editing: false,
      editing_password: false,
      input: {
        existing_owner: {
          rawPassword: "",
          passwordConfirm: ""
        }
      },
      errors: {
        editing: {
          allowedScopes: ""
        },
        existing_owner: {
          rawPassword: "",
          passwordConfirm: ""
        }
      },
      original: Object.assign({}, this.owner)
    };
  },
  methods: {
    get_current_user: function() {
      return oauth.get_context().owner;
    },
    show_controls: function() {
      this.controls_showing = true;
    },
    hide_controls: function() {
      this.controls_showing = false;
    },
    remove: function() {
      if (
        confirm(`Are you sure you want to delete resource owner [${this.owner.username}]?`)
      )
        requests.delete_owner(this.owner.username).then(response => {
          if (response.success) {
            this.$emit("owner-deleted", this.owner.username);
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
    edit_password: function() {
      this.editing = true;
      this.editing_password = true;
    },
    save_edit: function(e) {
      if (this.editing_password) {
        this.update_owner_credentials(e);
      } else {
        this.update_owner(e);
      }
    },
    cancel_edit: function() {
      for (var attr in this.original) {
        this.owner[attr] = this.original[attr];
      }

      this.input.existing_owner.rawPassword = "";
      this.input.existing_owner.passwordConfirm = "";

      this.errors.editing.allowedScopes = "";
      this.errors.existing_owner.rawPassword = "";
      this.errors.existing_owner.passwordConfirm = "";

      this.editing = false;
      this.editing_password = false;
    },
    update_owner: function() {
      this.errors.editing.allowedScopes = "";

      const updatable = extract_updatable_fields(this.owner);
      const original = extract_updatable_fields(this.original);

      const owner_changed =
        JSON.stringify(updatable) !== JSON.stringify(original);

      if (owner_changed) {
        if (updatable.allowedScopes.length <= 0) {
          this.errors.editing.allowedScopes = "Allowed scopes cannot be empty";
        }

        if (!this.errors.editing.allowedScopes) {
          requests.put_owner(this.owner.username, updatable).then(response => {
            if (response.success) {
              this.original = Object.assign({}, this.owner);
              this.editing = "";
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
    update_owner_credentials: function() {
      this.errors.existing_owner.rawPassword = "";
      this.errors.existing_owner.passwordConfirm = "";

      if (!this.input.existing_owner.rawPassword) {
        this.errors.existing_owner.rawPassword = "Password cannot be empty";
      }

      if (
        !this.input.existing_owner.passwordConfirm ||
        this.input.existing_owner.rawPassword !=
          this.input.existing_owner.passwordConfirm
      ) {
        this.errors.existing_owner.passwordConfirm =
          "Passwords must be provided and must match";
      }

      if (
        !this.errors.existing_owner.rawPassword &&
        !this.errors.existing_owner.passwordConfirm
      ) {
        oauth
          .derive_password(
            this.input.existing_owner.rawPassword,
            oauth.derive_salt(this.owner.username)
          )
          .then(
            derived_password => {
              requests
                .put_owner_credentials(this.owner.username, {
                  rawPassword: derived_password
                })
                .then(response => {
                  if (response.success) {
                    this.input.existing_owner.rawPassword = "";
                    this.input.existing_owner.passwordConfirm = "";
                    this.editing = false;
                    this.editing_password = false;
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
      }
    }
  }
};

function extract_updatable_fields({ allowedScopes, active }) {
  if (Object.prototype.toString.call(allowedScopes) === "[object String]") {
    const splitScopes = allowedScopes
      .split(",")
      .map(s => s.trim())
      .filter(String);
    return { allowedScopes: splitScopes, active };
  } else {
    return { allowedScopes, active };
  }
}
</script>


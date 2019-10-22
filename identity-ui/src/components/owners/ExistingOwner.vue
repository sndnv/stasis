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
                v-bind:class="{ 'invalid': errors.existing_owner.raw_password }"
                v-model.trim="input.existing_owner.raw_password"
                :title="errors.existing_owner.raw_password"
              />
              <label class="active">Password</label>
              <span
                v-if="errors.existing_owner.raw_password"
                class="helper-text"
                :data-error="errors.existing_owner.raw_password"
              />
            </div>
          </div>
          <div class="row">
            <div class="input-field col s12">
              <input
                class="black-text password-confirm"
                type="password"
                v-bind:class="{ 'invalid': errors.existing_owner.password_confirm }"
                v-model.trim="input.existing_owner.password_confirm"
                :title="errors.existing_owner.password_confirm"
              />
              <label class="active">Password (confirm)</label>
              <span
                v-if="errors.existing_owner.password_confirm"
                class="helper-text"
                :data-error="errors.existing_owner.password_confirm"
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
                v-model.trim="owner.allowed_scopes"
                :readonly="!editing"
                v-bind:class="{ 'invalid': errors.editing.allowed_scopes }"
                :title="errors.editing.allowed_scopes"
              />
              <label class="active">Allowed Scopes</label>
              <span
                v-if="errors.editing.allowed_scopes"
                class="helper-text"
                :data-error="errors.editing.allowed_scopes"
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
          raw_password: "",
          password_confirm: ""
        }
      },
      errors: {
        editing: {
          allowed_scopes: ""
        },
        existing_owner: {
          raw_password: "",
          password_confirm: ""
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

      this.input.existing_owner.raw_password = "";
      this.input.existing_owner.password_confirm = "";

      this.errors.editing.allowed_scopes = "";
      this.errors.existing_owner.raw_password = "";
      this.errors.existing_owner.password_confirm = "";

      this.editing = false;
      this.editing_password = false;
    },
    update_owner: function() {
      this.errors.editing.allowed_scopes = "";

      const updatable = extract_updatable_fields(this.owner);
      const original = extract_updatable_fields(this.original);

      const owner_changed =
        JSON.stringify(updatable) !== JSON.stringify(original);

      if (owner_changed) {
        if (updatable.allowed_scopes.length <= 0) {
          this.errors.editing.allowed_scopes = "Allowed scopes cannot be empty";
        }

        if (!this.errors.editing.allowed_scopes) {
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
      this.errors.existing_owner.raw_password = "";
      this.errors.existing_owner.password_confirm = "";

      if (!this.input.existing_owner.raw_password) {
        this.errors.existing_owner.raw_password = "Password cannot be empty";
      }

      if (
        !this.input.existing_owner.password_confirm ||
        this.input.existing_owner.raw_password !=
          this.input.existing_owner.password_confirm
      ) {
        this.errors.existing_owner.password_confirm =
          "Passwords must be provided and must match";
      }

      if (
        !this.errors.existing_owner.raw_password &&
        !this.errors.existing_owner.password_confirm
      ) {
        oauth
          .derive_password(
            this.input.existing_owner.raw_password,
            oauth.derive_salt(this.owner.username)
          )
          .then(
            derived_password => {
              requests
                .put_owner_credentials(this.owner.username, {
                  raw_password: derived_password
                })
                .then(response => {
                  if (response.success) {
                    this.input.existing_owner.raw_password = "";
                    this.input.existing_owner.password_confirm = "";
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

function extract_updatable_fields({ allowed_scopes, active }) {
  if (Object.prototype.toString.call(allowed_scopes) === "[object String]") {
    const split_scopes = allowed_scopes
      .split(",")
      .map(s => s.trim())
      .filter(String);
    return { allowed_scopes: split_scopes, active };
  } else {
    return { allowed_scopes, active };
  }
}
</script>


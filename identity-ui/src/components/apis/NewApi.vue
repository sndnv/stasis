<template>
  <div class="col s6 m3 l2">
    <div class="card green lighten-5 hoverable">
      <div class="card-content">
        <div class="row">
          <div class="input-field col s12">
            <input
              class="black-text"
              id="new-api-id"
              type="text"
              v-bind:class="{ 'invalid': errors.new_api_id }"
              v-model.trim="input.new_api_id"
              :title="errors.new_api_id"
            />
            <label>Identifier</label>
            <span v-if="errors.new_api_id" class="helper-text" :data-error="errors.new_api_id" />
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
  name: "NewAPI",
  data: function() {
    return {
      creating: false,
      input: {
        new_api_id: ""
      },
      errors: {
        new_api_id: ""
      }
    };
  },
  methods: {
    create: function() {
      this.creating = true;
      this.errors.new_api_id = "";

      if (!this.input.new_api_id) {
        this.errors.new_api_id = "API identifier cannot be empty";
      }

      if (!this.errors.new_api_id) {
        requests.post_api({ id: this.input.new_api_id }).then(response => {
          this.creating = false;
          if (response.success) {
            this.$emit("api-created", {
              id: this.input.new_api_id,
              is_new: true
            });
            this.input.new_api_id = "";
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
</script>


<template>
  <div class="col s6 m3 l2">
    <div
      :id="api.id"
      class="card hoverable"
      :class="get_current_api() === api.id ? 'yellow lighten-5' : ''"
      :title="get_current_api() === api.id ? 'Current API' : ''"
      v-on:mouseover="show_controls"
      v-on:mouseout="hide_controls"
    >
      <span v-if="api.is_new" class="new badge" />
      <div class="card-content">
        <div class="row">
          <div class="input-field col s12">
            <input class="black-text api-id" type="text" :value="api.id" readonly />
            <label class="active">Identifier</label>
          </div>
        </div>
      </div>
      <div class="card-action" v-show="controls_showing && get_current_api() !== api.id">
        <a
          class="btn-small waves-effect waves-light red right delete-button"
          :title="'Delete [' + api.id +']'"
          v-on:click.prevent="remove"
        >
          <i class="material-icons">delete</i>
        </a>
      </div>
    </div>
  </div>
</template>

<script>
import oauth from "@/api/oauth";
import requests from "@/api/requests";
import M from "materialize-css/dist/js/materialize.min.js";

export default {
  name: "ExistingAPI",
  props: ["api"],
  data: function() {
    return {
      controls_showing: false
    };
  },
  methods: {
    get_current_api: function() {
      return oauth.get_context().api;
    },
    show_controls: function() {
      this.controls_showing = true;
    },
    hide_controls: function() {
      this.controls_showing = false;
    },
    remove: function() {
      if (confirm(`Are you sure you want to delete API [${this.api.id}]?`))
        requests.delete_api(this.api.id).then(response => {
          if (response.success) {
            this.$emit("api-deleted", this.api.id);
          } else {
            const icon = '<i class="material-icons red-text">close</i>';
            const message = `${icon} ${response.error}`;
            M.toast({ html: message });
          }
        });
    }
  }
};
</script>

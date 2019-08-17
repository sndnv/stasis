<template>
  <div v-if="access_denied" class="row entities">
    <div class="col s12 m6">
      <div class="card orange">
        <div class="card-content center-align">
          <span class="card-title">Access Denied</span>
          <p>You do not have permission to view APIs.</p>
        </div>
      </div>
    </div>
  </div>
  <div v-else class="row entities">
    <api-container
      v-for="current in apis"
      v-bind:key="current.id"
      :api="current"
      v-on:api-deleted="api_deleted"
    />
    <new-api-container v-on:api-created="api_created" />
  </div>
</template>

<script>
import requests from "@/api/requests";
import NewApi from "./NewApi";
import ExistingApi from "./ExistingApi";
import M from 'materialize-css/dist/js/materialize.min.js';

export default {
  name: "APIs",
  components: {
    "new-api-container": NewApi,
    "api-container": ExistingApi
  },
  data: function() {
    return {
      apis: [],
      access_denied: false
    };
  },
  created() {
    this.$emit("loading-started");
    this.load_apis();
  },
  methods: {
    load_apis: function() {
      requests.get_apis().then(response => {
        this.$emit("loading-completed");
        if (response.error === "access_denied") {
          this.access_denied = true;
        } else if (response.error) {
          const icon = '<i class="material-icons red-text">close</i>';
          const message = `${icon} ${response.error}`;
          M.toast({ html: message });
        } else {
          this.apis = (response.entries || []).sort((a, b) => {
            return a.id.localeCompare(b.id);
          });
        }
      });
    },
    api_created: function(new_api) {
      this.apis.unshift(new_api);
    },
    api_deleted: function(api_id) {
      const api_index = this.apis.findIndex(api => api.id === api_id);
      this.apis.splice(api_index, 1);
    }
  }
};
</script>

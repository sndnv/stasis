<template>
  <div v-if="access_denied" class="row entities">
    <div class="col s12 m6">
      <div class="card orange">
        <div class="card-content center-align">
          <span class="card-title">Access Denied</span>
          <p>You do not have permission to view clients.</p>
        </div>
      </div>
    </div>
  </div>
  <div v-else class="row entities">
    <client-container
      v-for="current in clients"
      v-bind:key="current.id"
      :client="current"
      v-on:client-deleted="client_deleted"
    />
    <new-client-container v-on:client-created="client_created" />
  </div>
</template>

<script>
import requests from "@/api/requests";
import ExistingClient from "./ExistingClient";
import NewClient from "./NewClient";
import M from 'materialize-css/dist/js/materialize.min.js';

export default {
  name: "Clients",
  components: {
    "new-client-container": NewClient,
    "client-container": ExistingClient
  },
  data: function() {
    return {
      clients: [],
      access_denied: false
    };
  },
  created() {
    this.$emit("loading-started");
    this.load_clients();
  },
  methods: {
    load_clients: function() {
      requests.get_clients().then(response => {
        this.$emit("loading-completed");
        if (response.error === "access_denied") {
          this.access_denied = true;
        } else if (response.error) {
          const icon = '<i class="material-icons red-text">close</i>';
          const message = `${icon} ${response.error}`;
          M.toast({ html: message });
        } else {
          this.clients = (response.entries || []).sort((a, b) => {
            return a.id.localeCompare(b.id);
          });
        }
      });
    },
    client_created: function(new_client) {
      this.clients.unshift(new_client);
    },
    client_deleted: function(client_id) {
      const client_index = this.clients.findIndex(
        client => client.id === client_id
      );
      this.clients.splice(client_index, 1);
    }
  }
};
</script>

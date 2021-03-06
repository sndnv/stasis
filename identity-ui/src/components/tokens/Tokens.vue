<template>
  <div v-if="access_denied" class="row entities">
    <div class="col s12 m6">
      <div class="card orange">
        <div class="card-content center-align">
          <span class="card-title">Access Denied</span>
          <p>You do not have permission to view refresh tokens.</p>
        </div>
      </div>
    </div>
  </div>
  <div v-else class="row entities">
    <table class="highlight responsive-table centered">
      <thead>
        <tr>
          <th>Refresh Token</th>
          <th>Client</th>
          <th>Resource Owner</th>
          <th>Scope</th>
          <th>Expiration</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        <tr class="token-row" v-for="current in tokens" v-bind:key="current.token">
          <th>
            <pre class="truncate">{{ current.token }}</pre>
          </th>
          <td :title="current.client">
            <span class="badge blue-grey darken-1 white-text">{{ current.client.split('-').pop() }}</span>
          </td>
          <td>
            <span class="badge teal lighten-1 white-text">{{ current.owner }}</span>
          </td>
          <td :title="current.scope">{{ current.scope.split(':').pop() }}</td>
          <td>{{ current.expiration }}</td>
          <td>
            <a
              class="btn-small waves-effect waves-light red right delete-button"
              :title="'Delete token for [' + current.client + ' / ' + current.owner +']'"
              v-on:click.prevent="remove(current.token)"
            >
              <i class="material-icons">delete</i>
            </a>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script>
import requests from "@/api/requests";
import M from 'materialize-css/dist/js/materialize.min.js';

export default {
  name: "Tokens",
  data: function() {
    return {
      tokens: [],
      access_denied: false
    };
  },
  created() {
    this.$emit("loading-started");
    this.load_tokens();
  },
  methods: {
    load_tokens: function() {
      requests.get_tokens().then(response => {
        this.$emit("loading-completed");
        if (response.error === "access_denied") {
          this.access_denied = true;
        } else if (response.error) {
          const icon = '<i class="material-icons red-text">close</i>';
          const message = `${icon} ${response.error}`;
          M.toast({ html: message });
        } else {
          this.tokens = (response.entries || []).sort((a, b) => {
            return a.token.localeCompare(b.token);
          });
        }
      });
    },
    remove: function(token) {
      if (confirm(`Are you sure you want to delete refresh token [${token}]?`))
        requests.delete_token(token).then(response => {
          if (response.success) {
            const token_index = this.tokens.findIndex(
              token => token.token === token
            );
            this.tokens.splice(token_index, 1);

            const icon = '<i class="material-icons green-text">check</i>';
            const message = `${icon} Refresh token removed`;
            M.toast({ html: message });
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

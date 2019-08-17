<template>
  <div v-if="access_denied" class="row entities">
    <div class="col s12 m6">
      <div class="card orange">
        <div class="card-content center-align">
          <span class="card-title">Access Denied</span>
          <p>You do not have permission to view authorization codes.</p>
        </div>
      </div>
    </div>
  </div>
  <div v-else class="row entities">
    <table class="highlight responsive-table centered">
      <thead>
        <tr>
          <th>Authorization Code</th>
          <th>Client</th>
          <th>Resource Owner</th>
          <th>Scope</th>
          <th colspan="2">Challenge</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        <tr class="code-row" v-for="current in codes" v-bind:key="current.code">
          <th>
            <pre>{{ current.code }}</pre>
          </th>
          <td :title="current.client">
            <span class="badge blue-grey darken-1 white-text">{{ current.client.split('-').pop() }}</span>
          </td>
          <td>
            <span class="badge teal lighten-1 white-text">{{ current.owner }}</span>
          </td>
          <td :title="current.scope">{{ current.scope.split(':').pop() }}</td>
          <td>
            <span
              v-if="current.challenge"
              class="badge white-text"
              :class="current.challenge.method.toLowerCase() === 'plain' ? 'orange' : 'blue'"
            >{{ current.challenge.method }}</span>
            <span v-else class="badge white-text">-</span>
          </td>
          <td>
            <pre v-if="current.challenge">{{ current.challenge.value }}</pre>
            <pre v-else>-</pre>
          </td>
          <td>
            <a
              class="btn-small waves-effect waves-light red right delete-button"
              :title="'Delete token for [' + current.client + ' / ' + current.owner +']'"
              v-on:click.prevent="remove(current.code)"
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
import M from "materialize-css/dist/js/materialize.min.js";

export default {
  name: "Codes",
  data: function() {
    return {
      codes: [],
      access_denied: false
    };
  },
  created() {
    this.$emit("loading-started");
    this.load_codes();
  },
  methods: {
    load_codes: function() {
      requests.get_codes().then(response => {
        this.$emit("loading-completed");
        if (response.error === "access_denied") {
          this.access_denied = true;
        } else if (response.error) {
          const icon = '<i class="material-icons red-text">close</i>';
          const message = `${icon} ${response.error}`;
          M.toast({ html: message });
        } else {
          this.codes = (response.entries || []).sort((a, b) => {
            return a.code.localeCompare(b.code);
          });
        }
      });
    },
    remove: function(code) {
      if (
        confirm(`Are you sure you want to delete authorization code [${code}]?`)
      )
        requests.delete_code(code).then(response => {
          if (response.success) {
            const code_index = this.codes.findIndex(code => code.code === code);
            this.codes.splice(code_index, 1);

            const icon = '<i class="material-icons green-text">check</i>';
            const message = `${icon} Authorization code removed`;
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

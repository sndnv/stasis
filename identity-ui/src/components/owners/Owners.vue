<template>
  <div v-if="access_denied" class="row entities">
    <div class="col s12 m6">
      <div class="card orange">
        <div class="card-content center-align">
          <span class="card-title">Access Denied</span>
          <p>You do not have permission to view resource owners.</p>
        </div>
      </div>
    </div>
  </div>
  <div v-else class="row entities">
    <owner-container
      v-for="current in owners"
      v-bind:key="current.id"
      :owner="current"
      v-on:owner-deleted="owner_deleted"
    />
    <new-owner-container v-on:owner-created="owner_created" />
  </div>
</template>

<script>
import requests from "@/api/requests";
import ExistingOwner from "./ExistingOwner";
import NewOwner from "./NewOwner";
import M from 'materialize-css/dist/js/materialize.min.js';

export default {
  name: "Owners",
  components: {
    "new-owner-container": NewOwner,
    "owner-container": ExistingOwner
  },
  data: function() {
    return {
      owners: [],
      access_denied: false
    };
  },
  created() {
    this.$emit("loading-started");
    this.load_owners();
  },
  methods: {
    load_owners: function() {
      requests.get_owners().then(response => {
        this.$emit("loading-completed");
        if (response.error === "access_denied") {
          this.access_denied = true;
        } else if (response.error) {
          const icon = '<i class="material-icons red-text">close</i>';
          const message = `${icon} ${response.error}`;
          M.toast({ html: message });
        } else {
          this.owners = (response.entries || []).sort((a, b) => {
            return a.username.localeCompare(b.username);
          });
        }
      });
    },
    owner_created: function(new_owner) {
      this.owners.unshift(new_owner);
    },
    owner_deleted: function(owner_username) {
      const owner_index = this.owners.findIndex(
        owner => owner.username === owner_username
      );
      this.owners.splice(owner_index, 1);
    }
  }
};
</script>

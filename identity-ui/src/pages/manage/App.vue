<template>
  <div class="main-container" id="manage">
    <template v-if="$route.name == 'login'">
      <router-view></router-view>
    </template>
    <template v-else-if="$route.name == 'callback'">
      <router-view></router-view>
    </template>
    <template v-else-if="$route.matched.length">
      <div>
        <nav>
          <div class="nav-wrapper blue-grey darken-1">
            <div class="left">
              <router-link :to="{ name: 'home'}">
                <img class="nav-logo" width="40" height="40" src="@/assets/logo.svg" />
              </router-link>
              <a class="breadcrumb">identity</a>
              <a class="breadcrumb">{{ this.$route.name }}</a>
            </div>
            <ul class="right hide-on-med-and-down">
              <li>
                <a
                  class="waves-effect waves-light btn-small logout-button"
                  v-on:click.prevent="logout()"
                >Log Out</a>
              </li>
            </ul>
          </div>
        </nav>
        <div class="progress">
          <div class="indeterminate" v-if="loading"></div>
        </div>
        <div class="row">
          <div class="col s12"></div>
          <div class="col s2">
            <div class="collection">
              <router-link
                class="collection-item waves-effect"
                :class="$route.name === 'apis' ? 'active' : ''"
                :to="{ name: 'apis'}"
              >APIs</router-link>
              <router-link
                class="collection-item waves-effect"
                :class="$route.name === 'clients' ? 'active' : ''"
                :to="{ name: 'clients'}"
              >Clients</router-link>
              <router-link
                class="collection-item waves-effect"
                :class="$route.name === 'owners' ? 'active' : ''"
                :to="{ name: 'owners'}"
              >Resource Owners</router-link>
            </div>
            <div class="collection">
              <router-link
                class="collection-item waves-effect"
                :class="$route.name === 'codes' ? 'active' : ''"
                :to="{ name: 'codes'}"
              >Authorization Codes</router-link>
              <router-link
                class="collection-item waves-effect"
                :class="$route.name === 'tokens' ? 'active' : ''"
                :to="{ name: 'tokens'}"
              >Refresh Tokens</router-link>
            </div>
          </div>
          <div class="col s10">
            <router-view
              v-on:loading-completed="loading_completed"
              v-on:loading-started="loading_started"
            ></router-view>
          </div>
        </div>
      </div>
    </template>
    <template v-else>
      <generic-error />
    </template>
  </div>
</template>

<script>
import oauth from "@/api/oauth";
import GenericError from "@/components/GenericError";

export default {
  name: "manage",
  data: function() {
    return {
      loading: true
    };
  },
  components: {
    "generic-error": GenericError
  },
  methods: {
    loading_started: function() {
      this.loading = true;
    },
    loading_completed: function() {
      this.loading = false;
    },
    logout: function() {
      oauth.logout();
      this.$router.push({ name: "login" });
    }
  }
};
</script>

<style>
html,
body {
  height: 100%;
  background-color: #cfd8dc;
}

.nav-logo {
  vertical-align: middle;
  margin-left: 1em;
}

.main-container {
  height: 100%;
  width: 100%;
  margin: 0 auto;
}

.entities {
  display: flex;
  flex-wrap: wrap;
}

.entities .col {
  margin-left: 0 !important;
}

.entities-header {
  margin-bottom: 0px !important;
}

.entities-header blockquote {
  border-left-color: #546e7a !important;
}

.entities .card-content .row {
  margin-bottom: 0.75rem;
}

.entities .card-content .input-field {
  margin-top: 0px;
  margin-bottom: 0px;
}

.entities .card-content .input-field input {
  font-size: 0.85rem;
}

.entities
  .existing
  .card-content
  .input-field
  input:not([disabled]):not([readonly]) {
  color: #26a69a !important;
  font-weight: 500;
}

.entities .card-action .btn-small {
  margin-left: 0.1rem;
}

.progress {
  background-color: transparent !important;
  margin: 0 !important;
}
</style>

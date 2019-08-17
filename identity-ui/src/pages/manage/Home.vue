<template>
  <div>
    <div class="row">
      <div class="col s12 m4">
        <div class="card blue-grey darken-1">
          <div class="card-content white-text">
            <span class="card-title">API</span>
            <p>
              The server hosting the protected resources, capable
              of accepting and responding to protected resource
              requests using access tokens
            </p>
          </div>
        </div>
      </div>
      <div class="col s12 m4">
        <div class="card blue-grey darken-1">
          <div class="card-content white-text">
            <span class="card-title">Resource Owner</span>
            <p>
              An entity capable of granting access to a protected
              resource, usually a person (end-user)
            </p>
          </div>
        </div>
      </div>
      <div class="col s12 m4">
        <div class="card blue-grey darken-1">
          <div class="card-content white-text">
            <span class="card-title">Client</span>
            <p>
              An application making protected resource requests
              on behalf of the resource owner and with its authorization
            </p>
          </div>
        </div>
      </div>
    </div>
    <div class="row">
      <div class="col s12 m6">
        <blockquote>
          <a href="#" class="teal-text text-lighten-1 toggle-config" v-on:click.prevent="toggle_config">Service Config</a>
          <pre v-if="show_config" class="config">{{ get_config() }}</pre>
        </blockquote>
      </div>
      <div class="col s12 m6">
        <blockquote>
          For mode details, check the OAuth 2.0 RFC -
          <a
            href="https://tools.ietf.org/html/rfc6749"
          >https://tools.ietf.org/html/rfc6749</a>
        </blockquote>
      </div>
    </div>
  </div>
</template>

<script>
import config from "@/../identity.config.js";

export default {
  name: "Home",
  data: function() {
    return {
      show_config: false
    };
  },
  created() {
    this.$emit("loading-completed");
  },
  methods: {
    get_config: function() {
      return config;
    },
    toggle_config: function() {
      this.show_config = !this.show_config;
    }
  }
};
</script>

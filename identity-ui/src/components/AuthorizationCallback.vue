<template>
  <div class="row">
    <div class="col s12 offset-m3 m6">
      <div class="card orange">
        <div v-if="query && query.error" class="card-content center-align">
          <span class="card-title">{{ query.error }}</span>
          <p v-if="query.error_description">{{ query.error_description }}</p>
        </div>
        <div v-else class="center-align">
          <generic-error />
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import GenericError from "./GenericError";

export default {
  name: "AuthorizationCallback",
  components: {
    "generic-error": GenericError
  },
  data: function() {
    return {
      query: this.$route.query
    };
  }
};
</script>

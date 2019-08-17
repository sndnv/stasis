<template>
  <div class="main-container" id="authorize">
    <router-view></router-view>
  </div>
</template>

<script>
export default {
  name: "authorize"
};
</script>

<style>
html,
body {
  height: 100%;
  background-color: #cfd8dc;
}

.main-container {
  height: 100%;
  width: 100%;
  margin: 0 auto;
}
</style>

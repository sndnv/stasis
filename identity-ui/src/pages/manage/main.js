import Vue from 'vue';
import VueRouter from 'vue-router';
import App from './App';
import Apis from '@/components/apis/Apis';
import Clients from '@/components/clients/Clients';
import Owners from '@/components/owners/Owners';
import Codes from '@/components/codes/Codes';
import Tokens from '@/components/tokens/Tokens';
import Home from '@/pages/manage/Home';
import Login from '@/pages/manage/Login';
import AuthorizationCallback from '@/components/AuthorizationCallback'

import oauth from '@/api/oauth';

import 'materialize-css/dist/css/materialize.min.css'
import 'materialize-css/dist/js/materialize.min.js'

function require_authentication(to, from, next) {
  if (!oauth.is_authenticated()) {
    next({
      name: 'login',
      query: { redirect: to.fullPath }
    });
  } else {
    next();
  }
}

function skip_authentication(to, from, next) {
  if (oauth.is_authenticated()) {
    next({ name: 'home' });
  } else {
    next();
  }
}

const router = new VueRouter({
  mode: 'history',
  base: __dirname,
  routes: [
    { path: '/manage/home', name: 'home', component: Home, beforeEnter: require_authentication, meta: { title: 'Home' } },
    { path: '/manage/apis', name: 'apis', component: Apis, beforeEnter: require_authentication, meta: { title: 'APIs' } },
    { path: '/manage/clients', name: 'clients', component: Clients, beforeEnter: require_authentication, meta: { title: 'Clients' } },
    { path: '/manage/owners', name: 'owners', component: Owners, beforeEnter: require_authentication, meta: { title: 'Owners' } },
    { path: '/manage/codes', name: 'codes', component: Codes, beforeEnter: require_authentication, meta: { title: 'Codes' } },
    { path: '/manage/tokens', name: 'tokens', component: Tokens, beforeEnter: require_authentication, meta: { title: 'Tokens' } },
    { path: '/manage/login', name: 'login', component: Login, beforeEnter: skip_authentication, meta: { title: 'Login' } },
    { path: '/manage/login/callback', name: 'callback', component: AuthorizationCallback, beforeEnter: skip_authentication, meta: { title: 'Authorization' } },
    { path: '*', redirect: { name: 'home' } }
  ]
});

router.beforeEach((to, from, next) => {
  document.title = `identity > ${to.meta.title}`
  next()
})

Vue.use(VueRouter);

new Vue({
  router,
  render: h => h(App),
}).$mount('#main');

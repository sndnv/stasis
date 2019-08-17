import Vue from 'vue';
import VueRouter from 'vue-router';
import App from './App';
import Authorize from './Authorize';

import 'materialize-css/dist/css/materialize.min.css'
import 'materialize-css/dist/js/materialize.min.js'

const router = new VueRouter({
    mode: 'history',
    base: __dirname,
    routes: [
        {
            path: '/authorize',
            name: 'authorize',
            component: Authorize,
            meta: { title: 'Authorize' },
            props: (route) => ({ params: route.query })
        }
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

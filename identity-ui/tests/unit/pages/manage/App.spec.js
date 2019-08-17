import { shallowMount } from '@vue/test-utils'
import App from '@/pages/manage/App'
import oauth from '@/api/oauth'

describe('App', () => {
    const router_view_stub = /<router-view-stub><\/router-view-stub>/

    test('should render management app login page', () => {
        const $route = { name: 'login' }
        const app = shallowMount(App, { mocks: { $route }, stubs: ['router-view'] });

        expect(app.html()).toMatch(router_view_stub);
        expect(app.html()).not.toMatch(/<a class="breadcrumb">identity<\/a>/);
    })

    test('should render management app pages', () => {
        const $route = { name: 'test', matched: ['test'] }
        const app = shallowMount(App, { mocks: { $route }, stubs: ['router-view', 'router-link'] });

        expect(app.html()).toMatch(router_view_stub);
        expect(app.html()).toMatch(/<a class="breadcrumb">identity<\/a>/);
        expect(app.html()).toMatch(/<a class="breadcrumb">test<\/a>/);
    })

    test('should render callback page', () => {
        const $route = { name: 'callback', matched: [] }
        const app = shallowMount(App, { mocks: { $route }, stubs: ['router-view'] });

        expect(app.html()).toMatch(router_view_stub);
        expect(app.html()).not.toMatch(/<a class="breadcrumb">identity<\/a>/);
    })

    test('should render error page if invalid route is requested', () => {
        const $route = { name: 'test', matched: [] }
        const app = shallowMount(App, { mocks: { $route }, stubs: ['generic-error'] });

        expect(app.html()).toMatch(/<generic-error-stub><\/generic-error-stub>/);
        expect(app.html()).not.toMatch(router_view_stub);
        expect(app.html()).not.toMatch(/<a class="breadcrumb">test<\/a>/);
    })

    test('should handle page load start and end', () => {
        const load_bar_loading = '<div class="progress"><div class="indeterminate"></div></div>'
        const load_bar_completed = '<div class="progress"><!----></div>'
        const $route = { name: 'test', matched: ['test'] }
        const app = shallowMount(App, { mocks: { $route }, stubs: ['router-view', 'router-link'] });

        app.find('router-view-stub').vm.$emit('loading-started');

        expect(app.html()).toContain(load_bar_loading);
        expect(app.html()).not.toContain(load_bar_completed);

        app.find('router-view-stub').vm.$emit('loading-completed');

        expect(app.html()).not.toContain(load_bar_loading);
        expect(app.html()).toContain(load_bar_completed);
    })

    test('should allow users to log out', () => {
        const spy = jest.spyOn(oauth, 'logout').mockImplementation(
            () => { return Promise.resolve({ success: true }); }
        );

        const $route = { name: 'test', matched: ['test'] }
        const $router = { push: function() {} }
        const app = shallowMount(App, { mocks: { $route, $router }, stubs: ['router-view', 'router-link'] });

        app.find(".logout-button").trigger('click');

        expect(spy).toHaveBeenCalled();
        spy.mockRestore();
    })
})

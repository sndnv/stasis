import Vue from 'vue'
import { shallowMount } from '@vue/test-utils'
import Authorize from '@/pages/authorize/Authorize'
import oauth from '@/api/oauth'
import M from "materialize-css/dist/js/materialize.min.js";

describe('Authorize', () => {
    const props = { params: { scope: "some-scope" } };
    const $route = { path: 'some-path' };
    const $router = { replace: function () { } };

    test('should render authorization page', () => {
        const authorize = shallowMount(Authorize, { propsData: props, mocks: { $route, $router } });

        expect(authorize.find("#username").isVisible()).toBe(true);
        expect(authorize.find("#password").isVisible()).toBe(true);
        expect(authorize.find("#authorize-button").isVisible()).toBe(true);
    })

    test('should validate usernames', () => {
        const authorize = shallowMount(Authorize, { propsData: props, mocks: { $route, $router } });
        const error_message = 'Username cannot be empty'
        const error_label = `data-error="${error_message}"`
        const error_title = `title="${error_message}"`

        expect(authorize.html()).not.toContain(error_label);
        expect(authorize.html()).not.toContain(error_title);

        authorize.find('#username').setValue('');
        authorize.find('#password').setValue('some-password');

        authorize.find('#authorize-button').trigger('click');

        expect(authorize.html()).toContain(error_label);
        expect(authorize.html()).toContain(error_title);
    })

    test('should validate passwords', () => {
        const authorize = shallowMount(Authorize, { propsData: props, mocks: { $route, $router } });
        const error_message = 'Password cannot be empty'
        const error_label = `data-error="${error_message}"`
        const error_title = `title="${error_message}"`

        expect(authorize.html()).not.toContain(error_label);
        expect(authorize.html()).not.toContain(error_title);

        authorize.find('#username').setValue('some-user');
        authorize.find('#password').setValue('');

        authorize.find('#authorize-button').trigger('click');

        expect(authorize.html()).toContain(error_label);
        expect(authorize.html()).toContain(error_title);
    })

    test('should allow authorizations', () => {
        const authorize_spy = jest.spyOn(oauth, 'authorize').mockImplementation(
            () => { return Promise.resolve({ success: true }); }
        );

        const toast_spy = jest.spyOn(M, 'toast').mockImplementation(
            () => { return true; }
        );

        const authorize = shallowMount(Authorize, { propsData: props, mocks: { $route, $router } });

        authorize.find('#username').setValue('some-user');
        authorize.find('#password').setValue('some-password');

        authorize.find("#authorize-button").trigger('click');

        return Vue.nextTick().then(function () {
            const expected_toast = {
                html: "<i class=\"material-icons green-text\">check</i> Authorization successful"
            };

            expect(authorize_spy).toHaveBeenCalledWith('some-user', 'some-password', { scope: 'some-scope' });
            expect(toast_spy).toHaveBeenCalledWith(expected_toast);

            authorize_spy.mockRestore();
            toast_spy.mockRestore();
        });
    })

    test('should handle access denied errors', () => {
        const authorize_spy = jest.spyOn(oauth, 'authorize').mockImplementation(
            () => { return Promise.resolve({ error: 'access_denied' }); }
        );

        const toast_spy = jest.spyOn(M, 'toast').mockImplementation(
            () => { return true; }
        );

        const authorize = shallowMount(Authorize, { propsData: props, mocks: { $route, $router } });

        authorize.find('#username').setValue('some-user');
        authorize.find('#password').setValue('some-password');

        authorize.find("#authorize-button").trigger('click');

        return Vue.nextTick().then(function () {
            const expected_toast = {
                html: "<i class=\"material-icons red-text\">close</i> Invalid credentials specified"
            };

            expect(authorize_spy).toHaveBeenCalledWith('some-user', 'some-password', { scope: 'some-scope' });
            expect(toast_spy).toHaveBeenCalledWith(expected_toast);

            authorize_spy.mockRestore();
            toast_spy.mockRestore();
        });
    })

    test('should handle access denied errors', () => {
        const authorize_spy = jest.spyOn(oauth, 'authorize').mockImplementation(
            () => { return Promise.resolve({ error: 'failed', error_description: 'request' }); }
        );

        const toast_spy = jest.spyOn(M, 'toast').mockImplementation(
            () => { return true; }
        );

        const authorize = shallowMount(Authorize, { propsData: props, mocks: { $route, $router } });

        authorize.find('#username').setValue('some-user');
        authorize.find('#password').setValue('some-password');

        authorize.find("#authorize-button").trigger('click');

        return Vue.nextTick().then(function () {
            const expected_toast = {
                html: "<i class=\"material-icons red-text\">close</i> request (failed)"
            };

            expect(authorize_spy).toHaveBeenCalledWith('some-user', 'some-password', { scope: 'some-scope' });
            expect(toast_spy).toHaveBeenCalledWith(expected_toast);

            authorize_spy.mockRestore();
            toast_spy.mockRestore();
        });
    })
})

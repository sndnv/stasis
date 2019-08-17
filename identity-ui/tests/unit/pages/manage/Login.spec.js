import Vue from 'vue'
import { shallowMount } from '@vue/test-utils'
import Login from '@/pages/manage/Login'
import oauth from '@/api/oauth'
import M from "materialize-css/dist/js/materialize.min.js";

describe('Login', () => {
    test('should render login page', () => {
        const login = shallowMount(Login);

        expect(login.find("#username").isVisible()).toBe(true);
        expect(login.find("#password").isVisible()).toBe(true);
        expect(login.find("#login-button").isVisible()).toBe(true);
    })

    test('should validate usernames', () => {
        const login = shallowMount(Login);
        const error_message = 'Username cannot be empty'
        const error_label = `data-error="${error_message}"`
        const error_title = `title="${error_message}"`

        expect(login.html()).not.toContain(error_label);
        expect(login.html()).not.toContain(error_title);

        login.find('#username').setValue('');
        login.find('#password').setValue('some-password');

        login.find('#login-button').trigger('click');

        expect(login.html()).toContain(error_label);
        expect(login.html()).toContain(error_title);
    })

    test('should validate passwords', () => {
        const login = shallowMount(Login);
        const error_message = 'Password cannot be empty'
        const error_label = `data-error="${error_message}"`
        const error_title = `title="${error_message}"`

        expect(login.html()).not.toContain(error_label);
        expect(login.html()).not.toContain(error_title);

        login.find('#username').setValue('some-user');
        login.find('#password').setValue('');

        login.find('#login-button').trigger('click');

        expect(login.html()).toContain(error_label);
        expect(login.html()).toContain(error_title);
    })

    test('should allow users to log in', () => {
        const login_spy = jest.spyOn(oauth, 'login').mockImplementation(
            () => { return Promise.resolve({ success: true }); }
        );

        const toast_spy = jest.spyOn(M, 'toast').mockImplementation(
            () => { return true; }
        );

        const login = shallowMount(Login);

        login.find('#username').setValue('some-user');
        login.find('#password').setValue('some-password');

        login.find("#login-button").trigger('click');

        return Vue.nextTick().then(function () {
            const expected_toast = {
                html: "<i class=\"material-icons green-text\">check</i> Successfully logged in as [some-user]"
            };

            expect(login_spy).toHaveBeenCalledWith('some-user', 'some-password');
            expect(toast_spy).toHaveBeenCalledWith(expected_toast);

            login_spy.mockRestore();
            toast_spy.mockRestore();
        });
    })

    test('should handle access denied errors', () => {
        const login_spy = jest.spyOn(oauth, 'login').mockImplementation(
            () => { return Promise.resolve({ error: 'access_denied' }); }
        );

        const toast_spy = jest.spyOn(M, 'toast').mockImplementation(
            () => { return true; }
        );

        const login = shallowMount(Login);

        login.find('#username').setValue('some-user');
        login.find('#password').setValue('some-password');

        login.find("#login-button").trigger('click');

        return Vue.nextTick().then(function () {
            const expected_toast = {
                html: "<i class=\"material-icons red-text\">close</i> Invalid credentials specified"
            };

            expect(login_spy).toHaveBeenCalledWith('some-user', 'some-password');
            expect(toast_spy).toHaveBeenCalledWith(expected_toast);

            login_spy.mockRestore();
            toast_spy.mockRestore();
        });
    })

    test('should handle failures', () => {
        const login_spy = jest.spyOn(oauth, 'login').mockImplementation(
            () => { return Promise.resolve({ error: 'failed', error_description: 'request' }); }
        );

        const toast_spy = jest.spyOn(M, 'toast').mockImplementation(
            () => { return true; }
        );

        const login = shallowMount(Login);

        login.find('#username').setValue('some-user');
        login.find('#password').setValue('some-password');

        login.find("#login-button").trigger('click');

        return Vue.nextTick().then(function () {
            const expected_toast = {
                html: "<i class=\"material-icons red-text\">close</i> request (failed)"
            };

            expect(login_spy).toHaveBeenCalledWith('some-user', 'some-password');
            expect(toast_spy).toHaveBeenCalledWith(expected_toast);

            login_spy.mockRestore();
            toast_spy.mockRestore();
        });
    })
})

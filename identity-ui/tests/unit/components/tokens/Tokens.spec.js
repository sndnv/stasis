import Vue from 'vue'
import { shallowMount } from '@vue/test-utils'
import Tokens from '@/components/tokens/Tokens'
import requests from '@/api/requests'

describe('Tokens', () => {
    let default_window_confirm = null;

    beforeAll(() => {
        default_window_confirm = window.confirm;
        window.confirm = jest.fn(() => true);
    })

    afterAll(() => {
        window.confirm = default_window_confirm;
    })

    const token = { token: 'some-token', client: 'some-client', owner: 'some-owner', scope: 'a:b:c' }
    const existing_token_row = /<tr class="token-row">.*<\/tr>/;

    test('should render empty tokens table', () => {
        const spy = jest.spyOn(requests, 'get_tokens').mockImplementation(
            () => { return Promise.resolve({ entries: [] }); }
        );

        const tokens = shallowMount(Tokens);

        return Vue.nextTick().then(function () {
            expect(tokens.emitted()['loading-completed']).toBeDefined();
            expect(tokens.html()).not.toMatch(existing_token_row);
            spy.mockRestore();
        });
    })

    test('should render existing tokens', () => {
        const spy = jest.spyOn(requests, 'get_tokens').mockImplementation(
            () => { return Promise.resolve({ entries: [token, { ...token, ...{ token: 'other-token' } }] }); }
        );

        const tokens = shallowMount(Tokens);

        return Vue.nextTick().then(function () {
            expect(tokens.emitted()['loading-completed']).toBeDefined();
            expect(tokens.html()).toMatch(existing_token_row);
            spy.mockRestore();
        });
    })

    test('should handle access denied responses', () => {
        const spy = jest.spyOn(requests, 'get_tokens').mockImplementation(
            () => { return Promise.resolve({ error: 'access_denied' }); }
        );

        const tokens = shallowMount(Tokens);

        return Vue.nextTick().then(function () {
            expect(tokens.emitted()['loading-completed']).toBeDefined();
            expect(tokens.html()).toContain('You do not have permission to view refresh tokens');
            spy.mockRestore();
        });
    })

    test('should handle failures', () => {
        const spy = jest.spyOn(requests, 'get_tokens').mockImplementation(
            () => { return Promise.resolve({ error: 'failed' }); }
        );

        const tokens = shallowMount(Tokens);

        return Vue.nextTick().then(function () {
            expect(tokens.emitted()['loading-completed']).toBeDefined();
            expect(tokens.html()).not.toMatch(existing_token_row);
            spy.mockRestore();
        });
    })

    test('should successfully remove existing tokens', () => {
        const tokens_spy = jest.spyOn(requests, 'get_tokens').mockImplementation(
            () => { return Promise.resolve({ entries: [token] }); }
        );

        const delete_spy = jest.spyOn(requests, 'delete_token').mockImplementation(
            () => { return Promise.resolve({ success: true }); }
        );

        const tokens = shallowMount(Tokens);

        return Vue.nextTick().then(function () {
            expect(tokens.emitted()['loading-completed']).toBeDefined();
            expect(tokens.html()).toMatch(existing_token_row);

            tokens.find(".token-row").find('.delete-button').trigger('click');

            return Vue.nextTick().then(function () {
                expect(tokens.html()).not.toMatch(existing_token_row);
                tokens_spy.mockRestore();
                delete_spy.mockRestore();
            });
        });
    })

    test('should handle token removal failures', () => {
        const tokens_spy = jest.spyOn(requests, 'get_tokens').mockImplementation(
            () => { return Promise.resolve({ entries: [token] }); }
        );

        const delete_spy = jest.spyOn(requests, 'delete_token').mockImplementation(
            () => { return Promise.resolve({ error: 'failed' }); }
        );

        const tokens = shallowMount(Tokens);

        return Vue.nextTick().then(function () {
            expect(tokens.emitted()['loading-completed']).toBeDefined();
            expect(tokens.html()).toMatch(existing_token_row);

            tokens.find(".token-row").find('.delete-button').trigger('click');

            return Vue.nextTick().then(function () {
                expect(tokens.html()).toMatch(existing_token_row);
                tokens_spy.mockRestore();
                delete_spy.mockRestore();
            });
        });
    })
})

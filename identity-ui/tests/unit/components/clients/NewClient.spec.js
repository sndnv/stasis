import Vue from 'vue'
import { shallowMount } from '@vue/test-utils'
import NewClient from '@/components/clients/NewClient'
import requests from '@/api/requests'

describe('NewClient', () => {
    const new_client_redirect_uri = "some-redirect-uri"
    const new_client_token_expiration = "1200"
    const new_client_secret = "some-secret"

    test('should validate client redirect URIs', () => {
        const new_client = shallowMount(NewClient);
        const error_message = 'Redirect URI cannot be empty'
        const error_label = `data-error="${error_message}"`
        const error_title = `title="${error_message}"`

        expect(new_client.html()).not.toContain(error_label);
        expect(new_client.html()).not.toContain(error_title);

        set_input_value(new_client, '#new-client-token-expiration', new_client_token_expiration);
        set_input_value(new_client, '#new-client-secret', new_client_secret);
        set_input_value(new_client, '#new-client-secret-confirm', new_client_secret);

        new_client.find('.create-button').trigger('click');

        expect(new_client.html()).toContain(error_label);
        expect(new_client.html()).toContain(error_title);
    })

    test('should validate client token expiration', () => {
        const new_client = shallowMount(NewClient);
        const error_message = 'Token expiration must be a positive number'
        const error_label = `data-error="${error_message}"`
        const error_title = `title="${error_message}"`

        expect(new_client.html()).not.toContain(error_label);
        expect(new_client.html()).not.toContain(error_title);

        set_input_value(new_client, '#new-client-token-expiration', '0');
        set_input_value(new_client, '#new-client-redirect-uri', new_client_redirect_uri);
        set_input_value(new_client, '#new-client-secret', new_client_secret);
        set_input_value(new_client, '#new-client-secret-confirm', new_client_secret);

        new_client.find('.create-button').trigger('click');

        expect(new_client.html()).toContain(error_label);
        expect(new_client.html()).toContain(error_title);
    })

    test('should validate client secrets', () => {
        const new_client = shallowMount(NewClient);
        const error_message = 'Secret cannot be empty'
        const error_label = `data-error="${error_message}"`
        const error_title = `title="${error_message}"`

        expect(new_client.html()).not.toContain(error_label);
        expect(new_client.html()).not.toContain(error_title);

        set_input_value(new_client, '#new-client-token-expiration', new_client_token_expiration);
        set_input_value(new_client, '#new-client-redirect-uri', new_client_redirect_uri);
        set_input_value(new_client, '#new-client-secret-confirm', new_client_secret);

        new_client.find('.create-button').trigger('click');

        expect(new_client.html()).toContain(error_label);
        expect(new_client.html()).toContain(error_title);
    })

    test('should confirm client secrets', () => {
        const new_client = shallowMount(NewClient);
        const error_message = 'Secrets must be provided and must match'
        const error_label = `data-error="${error_message}"`
        const error_title = `title="${error_message}"`

        expect(new_client.html()).not.toContain(error_label);
        expect(new_client.html()).not.toContain(error_title);

        set_input_value(new_client, '#new-client-token-expiration', new_client_token_expiration);
        set_input_value(new_client, '#new-client-redirect-uri', new_client_redirect_uri);
        set_input_value(new_client, '#new-client-secret', new_client_secret);
        set_input_value(new_client, '#new-client-secret-confirm', 'other-secret');

        new_client.find('.create-button').trigger('click');

        expect(new_client.html()).toContain(error_label);
        expect(new_client.html()).toContain(error_title);
    })

    test('should successfully create new clients', () => {
        const new_client_id = 'some-client'

        const spy = jest.spyOn(requests, 'post_client').mockImplementation(
            () => { return Promise.resolve({ success: true, data: { client: new_client_id } }); }
        );

        const new_client = shallowMount(NewClient);

        set_input_value(new_client, '#new-client-token-expiration', new_client_token_expiration);
        set_input_value(new_client, '#new-client-redirect-uri', new_client_redirect_uri);
        set_input_value(new_client, '#new-client-secret', new_client_secret);
        set_input_value(new_client, '#new-client-secret-confirm', new_client_secret);

        new_client.find('.create-button').trigger('click');

        return Vue.nextTick().then(function () {
            const client_created = new_client.emitted()['client-created'][0][0];
            expect(client_created.id).toBe(new_client_id);
            expect(client_created.redirectUri).toBe(new_client_redirect_uri);
            expect(`${client_created.tokenExpiration}`).toBe(new_client_token_expiration);
            expect(client_created.active).toBe(true);
            expect(client_created.is_new).toBe(true);

            spy.mockRestore();
        })
    })

    test('should handle client creation failures', () => {
        const spy = jest.spyOn(requests, 'post_client').mockImplementation(
            () => { return Promise.resolve({ error: "failed" }); }
        );

        const new_client = shallowMount(NewClient);

        set_input_value(new_client, '#new-client-token-expiration', new_client_token_expiration);
        set_input_value(new_client, '#new-client-redirect-uri', new_client_redirect_uri);
        set_input_value(new_client, '#new-client-secret', new_client_secret);
        set_input_value(new_client, '#new-client-secret-confirm', new_client_secret);

        new_client.find('.create-button').trigger('click');

        return Vue.nextTick().then(function () {
            expect(new_client.emitted()['client-created']).toBeUndefined();

            spy.mockRestore();
        });
    })
})

function set_input_value(new_client, input_id, value) {
    const input = new_client.find(input_id);
    input.setValue(value);
    expect(input.element.value).toBe(value);
}

import Vue from 'vue'
import { shallowMount } from '@vue/test-utils'
import ExistingClient from '@/components/clients/ExistingClient'
import requests from '@/api/requests'

jest.mock('@/api/oauth', () => (
    {
        get_context: () => { return { client: 'test-client' }; }
    }
))

describe('ExistingClient', () => {
    let default_window_confirm = null;

    beforeAll(() => {
        default_window_confirm = window.confirm;
        window.confirm = jest.fn(() => true);
    })

    afterAll(() => {
        window.confirm = default_window_confirm;
    })

    const client = { id: 'some-client', redirect_uri: 'some-uri', token_expiration: 4200, active: true }
    const some_client_props = { client: client };
    const current_client_props = { client: { ...client, ...{ id: 'test-client' } } };

    test('should explicitly mark the current client', () => {
        const current_client_title = 'title="Current Client"'

        const some_client = shallowMount(ExistingClient, { propsData: some_client_props });
        expect(some_client.html()).not.toContain(current_client_title);

        const current_client = shallowMount(ExistingClient, { propsData: current_client_props });
        expect(current_client.html()).toContain(current_client_title);
    })

    test('should explicitly mark newly created clients', () => {
        const new_client_badge = '<span class="new badge"></span>';

        const some_client = shallowMount(ExistingClient, { propsData: some_client_props });
        expect(some_client.html()).not.toContain(new_client_badge);

        const new_client_props = { client: { ...client, ...{ is_new: true } } };
        const new_client = shallowMount(ExistingClient, { propsData: new_client_props });
        expect(new_client.html()).toContain(new_client_badge);
    })

    test('should not allow removing the current client', () => {
        const some_client = shallowMount(ExistingClient, { propsData: some_client_props });
        some_client.setData({ controls_showing: true });
        expect(some_client.find('.delete-button').isVisible()).toBe(true);

        const current_client = shallowMount(ExistingClient, { propsData: current_client_props });
        current_client.setData({ controls_showing: true });
        expect(current_client.find('.delete-button').isVisible()).toBe(false);
    })

    test('should show/hide controls on mouseover/mouseout', () => {
        const some_client = shallowMount(ExistingClient, { propsData: some_client_props });
        expect(some_client.find('.delete-button').isVisible()).toBe(false);

        some_client.find('.card').trigger('mouseover');
        expect(some_client.find('.delete-button').isVisible()).toBe(true);

        some_client.find('.card').trigger('mouseout');
        expect(some_client.find('.delete-button').isVisible()).toBe(false);
    })

    test('should validate updated client token expiration', () => {
        const error_message = 'Token expiration must be a positive number'
        const error_label = `data-error="${error_message}"`
        const error_title = `title="${error_message}"`

        const some_client = shallowMount(ExistingClient, { propsData: some_client_props });
        some_client.setData({ controls_showing: true, editing: true });

        expect(some_client.html()).not.toContain(error_label);
        expect(some_client.html()).not.toContain(error_title);

        some_client.find('.token-expiration').setValue('');

        some_client.find('.save-button').trigger('click');

        expect(some_client.html()).toContain(error_label);
        expect(some_client.html()).toContain(error_title);
    })

    test('should successfully update clients', () => {
        const spy = jest.spyOn(requests, 'put_client').mockImplementation(
            () => { return Promise.resolve({ success: true }); }
        );

        const some_client = shallowMount(ExistingClient, { propsData: some_client_props });
        some_client.setData({ controls_showing: true, editing: true });
        some_client.find('.token-expiration').setValue('999');

        some_client.find('.save-button').trigger('click');

        return Vue.nextTick().then(function () {
            expect(some_client.find('.save-button').element).toBeUndefined();
            expect(spy).toHaveBeenCalled();
            spy.mockRestore();
        });
    })

    test('should not update unchanged clients', () => {
        const spy = jest.spyOn(requests, 'put_client').mockImplementation(
            () => { return Promise.resolve({ success: true }); }
        );

        const some_client = shallowMount(ExistingClient, { propsData: some_client_props });
        some_client.setData({ controls_showing: true, editing: true });

        some_client.find('.save-button').trigger('click');

        return Vue.nextTick().then(function () {
            expect(some_client.find('.save-button').element).toBeUndefined();
            expect(spy).not.toHaveBeenCalled();
            spy.mockRestore();
        });
    })

    test('should validate updated client secrets', () => {
        const error_message = 'Secret cannot be empty'
        const error_label = `data-error="${error_message}"`
        const error_title = `title="${error_message}"`

        const some_client = shallowMount(ExistingClient, { propsData: some_client_props });
        some_client.setData({ controls_showing: true, editing: true, editing_secret: true });

        expect(some_client.html()).not.toContain(error_label);
        expect(some_client.html()).not.toContain(error_title);

        some_client.find('.secret').setValue('');
        some_client.find('.secret-confirm').setValue('abc');

        some_client.find('.save-button').trigger('click');

        expect(some_client.html()).toContain(error_label);
        expect(some_client.html()).toContain(error_title);
    })

    test('should confirm updated client secrets', () => {
        const error_message = 'Secrets must be provided and must match'
        const error_label = `data-error="${error_message}"`
        const error_title = `title="${error_message}"`

        const some_client = shallowMount(ExistingClient, { propsData: some_client_props });
        some_client.setData({ controls_showing: true, editing: true, editing_secret: true });

        expect(some_client.html()).not.toContain(error_label);
        expect(some_client.html()).not.toContain(error_title);

        some_client.find('.secret').setValue('abc');
        some_client.find('.secret-confirm').setValue('def');

        some_client.find('.save-button').trigger('click');

        expect(some_client.html()).toContain(error_label);
        expect(some_client.html()).toContain(error_title);
    })

    test('should allow updating client secrets', () => {
        const spy = jest.spyOn(requests, 'put_client_credentials').mockImplementation(
            () => { return Promise.resolve({ success: true }); }
        );

        const some_client = shallowMount(ExistingClient, { propsData: some_client_props });
        some_client.setData({ controls_showing: true, editing: true, editing_secret: true });

        some_client.find('.secret').setValue('abc');
        some_client.find('.secret-confirm').setValue('abc');

        some_client.find('.save-button').trigger('click');

        return Vue.nextTick().then(function () {
            expect(some_client.find('.save-button').element).toBeUndefined();
            expect(spy).toHaveBeenCalled();
            spy.mockRestore();
        });
    })

    test('should allow cancelling client updates', () => {
        const spy = jest.spyOn(requests, 'put_client').mockImplementation(
            () => { return Promise.resolve({ success: true }); }
        );

        const some_client = shallowMount(ExistingClient, { propsData: some_client_props });
        some_client.setData({ controls_showing: true, editing: true });
        some_client.find('.token-expiration').setValue('999');

        some_client.find('.cancel-button').trigger('click');

        return Vue.nextTick().then(function () {
            expect(some_client.find('.cancel-button').element).toBeUndefined();
            expect(spy).not.toHaveBeenCalled();
            spy.mockRestore();
        });
    })

    test('should successfully remove existing clients', () => {
        const spy = jest.spyOn(requests, 'delete_client').mockImplementation(
            () => { return Promise.resolve({ success: true }); }
        );

        const some_client = shallowMount(ExistingClient, { propsData: some_client_props });
        some_client.setData({ controls_showing: true });

        some_client.find('.delete-button').trigger('click');

        return Vue.nextTick().then(function () {
            const client_deleted = some_client.emitted()['client-deleted'][0][0];
            expect(client_deleted).toBe(client.id);
            spy.mockRestore();
        });
    })

    test('should handle client removal failures', () => {
        const spy = jest.spyOn(requests, 'delete_client').mockImplementation(
            () => { return Promise.resolve({ error: 'failed' }); }
        );

        const some_client = shallowMount(ExistingClient, { propsData: some_client_props });
        some_client.setData({ controls_showing: true });

        some_client.find('.delete-button').trigger('click');

        return Vue.nextTick().then(function () {
            expect(some_client.emitted()['client-deleted']).toBeUndefined();
            spy.mockRestore();
        });
    })
})

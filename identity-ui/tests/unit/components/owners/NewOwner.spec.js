import Vue from 'vue'
import { shallowMount } from '@vue/test-utils'
import NewOwner from '@/components/owners/NewOwner'
import requests from '@/api/requests'

describe('NewOwner', () => {
    const new_owner_username = "some-user"
    const new_owner_allowed_scopes = "a,b,c"
    const new_owner_password = "some-password"
    const new_owner_subject = "some-subject"

    test('should validate resource owner usernames', () => {
        const new_owner = shallowMount(NewOwner);
        const error_message = 'Username cannot be empty'
        const error_label = `data-error="${error_message}"`
        const error_title = `title="${error_message}"`

        expect(new_owner.html()).not.toContain(error_label);
        expect(new_owner.html()).not.toContain(error_title);

        set_input_value(new_owner, '#new-owner-username', '');
        set_input_value(new_owner, '#new-owner-allowed-scopes', new_owner_allowed_scopes);
        set_input_value(new_owner, '#new-owner-password', new_owner_password);
        set_input_value(new_owner, '#new-owner-password-confirm', new_owner_password);

        new_owner.find('.create-button').trigger('click');

        expect(new_owner.html()).toContain(error_label);
        expect(new_owner.html()).toContain(error_title);
    })

    test('should validate resource owner allowed scopes', () => {
        const new_owner = shallowMount(NewOwner);
        const error_message = 'Allowed scopes cannot be empty'
        const error_label = `data-error="${error_message}"`
        const error_title = `title="${error_message}"`

        expect(new_owner.html()).not.toContain(error_label);
        expect(new_owner.html()).not.toContain(error_title);

        set_input_value(new_owner, '#new-owner-username', new_owner_username);
        set_input_value(new_owner, '#new-owner-allowed-scopes', '');
        set_input_value(new_owner, '#new-owner-password', new_owner_password);
        set_input_value(new_owner, '#new-owner-password-confirm', new_owner_password);

        new_owner.find('.create-button').trigger('click');

        expect(new_owner.html()).toContain(error_label);
        expect(new_owner.html()).toContain(error_title);
    })

    test('should validate resource owner passwords', () => {
        const new_owner = shallowMount(NewOwner);
        const error_message = 'Password cannot be empty'
        const error_label = `data-error="${error_message}"`
        const error_title = `title="${error_message}"`

        expect(new_owner.html()).not.toContain(error_label);
        expect(new_owner.html()).not.toContain(error_title);

        set_input_value(new_owner, '#new-owner-username', new_owner_username);
        set_input_value(new_owner, '#new-owner-allowed-scopes', new_owner_allowed_scopes);
        set_input_value(new_owner, '#new-owner-password', '');
        set_input_value(new_owner, '#new-owner-password-confirm', new_owner_password);

        new_owner.find('.create-button').trigger('click');

        expect(new_owner.html()).toContain(error_label);
        expect(new_owner.html()).toContain(error_title);
    })

    test('should confirm resource owner passwords', () => {
        const new_owner = shallowMount(NewOwner);
        const error_message = 'Passwords must be provided and must match'
        const error_label = `data-error="${error_message}"`
        const error_title = `title="${error_message}"`

        expect(new_owner.html()).not.toContain(error_label);
        expect(new_owner.html()).not.toContain(error_title);

        set_input_value(new_owner, '#new-owner-username', new_owner_username);
        set_input_value(new_owner, '#new-owner-allowed-scopes', new_owner_allowed_scopes);
        set_input_value(new_owner, '#new-owner-password', new_owner_password);
        set_input_value(new_owner, '#new-owner-password-confirm', 'abc');

        new_owner.find('.create-button').trigger('click');

        expect(new_owner.html()).toContain(error_label);
        expect(new_owner.html()).toContain(error_title);
    })

    test('should successfully create new resource owners (with custom subject)', () => {
        const spy = jest.spyOn(requests, 'post_owner').mockImplementation(
            () => { return Promise.resolve({ success: true }); }
        );

        const new_owner = shallowMount(NewOwner);

        set_input_value(new_owner, '#new-owner-username', new_owner_username);
        set_input_value(new_owner, '#new-owner-allowed-scopes', new_owner_allowed_scopes);
        set_input_value(new_owner, '#new-owner-password', new_owner_password);
        set_input_value(new_owner, '#new-owner-password-confirm', new_owner_password);
        set_input_value(new_owner, '#new-owner-subject', new_owner_subject);

        new_owner.find('.create-button').trigger('click');

        return Vue.nextTick().then(function () {
            return Vue.nextTick().then(function () {
                const owner_created = new_owner.emitted()['owner-created'][0][0];
                expect(owner_created.username).toBe(new_owner_username);
                expect(owner_created.allowedScopes).toBe(new_owner_allowed_scopes);
                expect(owner_created.active).toBe(true);
                expect(owner_created.is_new).toBe(true);
                expect(owner_created.subject).toBe(new_owner_subject);
                spy.mockRestore();
            })
        })
    })

    test('should successfully create new resource owners (without custom subject)', () => {
        const spy = jest.spyOn(requests, 'post_owner').mockImplementation(
            () => { return Promise.resolve({ success: true }); }
        );

        const new_owner = shallowMount(NewOwner);

        set_input_value(new_owner, '#new-owner-username', new_owner_username);
        set_input_value(new_owner, '#new-owner-allowed-scopes', new_owner_allowed_scopes);
        set_input_value(new_owner, '#new-owner-password', new_owner_password);
        set_input_value(new_owner, '#new-owner-password-confirm', new_owner_password);

        new_owner.find('.create-button').trigger('click');

        return Vue.nextTick().then(function () {
            return Vue.nextTick().then(function () {
                const owner_created = new_owner.emitted()['owner-created'][0][0];
                expect(owner_created.username).toBe(new_owner_username);
                expect(owner_created.allowedScopes).toBe(new_owner_allowed_scopes);
                expect(owner_created.active).toBe(true);
                expect(owner_created.is_new).toBe(true);
                expect(owner_created.subject).toBe('');
                spy.mockRestore();
            })
        })
    })

    test('should handle resource owner creation failures', () => {
        const spy = jest.spyOn(requests, 'post_owner').mockImplementation(
            () => { return Promise.resolve({ error: 'failed' }); }
        );

        const new_owner = shallowMount(NewOwner);

        set_input_value(new_owner, '#new-owner-username', new_owner_username);
        set_input_value(new_owner, '#new-owner-allowed-scopes', new_owner_allowed_scopes);
        set_input_value(new_owner, '#new-owner-password', new_owner_password);
        set_input_value(new_owner, '#new-owner-password-confirm', new_owner_password);

        new_owner.find('.create-button').trigger('click');

        return Vue.nextTick().then(function () {
            return Vue.nextTick().then(function () {
                expect(new_owner.emitted()['owner-created']).toBeUndefined();
                spy.mockRestore();
            })
        })
    })
})

function set_input_value(new_owner, input_id, value) {
    const input = new_owner.find(input_id);
    input.setValue(value);
    expect(input.element.value).toBe(value);
}

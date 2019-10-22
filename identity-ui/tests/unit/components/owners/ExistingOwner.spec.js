import Vue from 'vue'
import { shallowMount } from '@vue/test-utils'
import ExistingOwner from '@/components/owners/ExistingOwner'
import requests from '@/api/requests'

jest.mock('@/api/oauth', () => (
    {
        get_context: () => { return { owner: 'test-owner' }; },
        derive_password: () => { return Promise.resolve('some-string'); },
        derive_salt: () => { return Promise.resolve('some-salt'); },
    }
))

describe('ExistingOwner', () => {
    let default_window_confirm = null;

    beforeAll(() => {
        default_window_confirm = window.confirm;
        window.confirm = jest.fn(() => true);
    })

    afterAll(() => {
        window.confirm = default_window_confirm;
    })

    const owner = { username: 'some-owner', allowed_scopes: 'a,b,c', active: true }
    const some_owner_props = { owner: owner };
    const current_owner_props = { owner: { ...owner, ...{ username: 'test-owner' } } };

    test('should explicitly mark the current resource owner', () => {
        const current_owner_title = 'title="Current User"'

        const some_owner = shallowMount(ExistingOwner, { propsData: some_owner_props });
        expect(some_owner.html()).not.toContain(current_owner_title);

        const current_owner = shallowMount(ExistingOwner, { propsData: current_owner_props });
        expect(current_owner.html()).toContain(current_owner_title);
    })

    test('should explicitly mark newly created resource owner', () => {
        const new_owner_badge = '<span class="new badge"></span>';

        const some_owner = shallowMount(ExistingOwner, { propsData: some_owner_props });
        expect(some_owner.html()).not.toContain(new_owner_badge);

        const new_owner_props = { owner: { ...owner, ...{ is_new: true } } };
        const new_owner = shallowMount(ExistingOwner, { propsData: new_owner_props });
        expect(new_owner.html()).toContain(new_owner_badge);
    })

    test('should not allow removing the current resource owner', () => {
        const some_owner = shallowMount(ExistingOwner, { propsData: some_owner_props });
        some_owner.setData({ controls_showing: true });
        expect(some_owner.find('.delete-button').isVisible()).toBe(true);

        const current_owner = shallowMount(ExistingOwner, { propsData: current_owner_props });
        current_owner.setData({ controls_showing: true });
        expect(current_owner.find('.delete-button').isVisible()).toBe(false);
    })

    test('should show/hide controls on mouseover/mouseout', () => {
        const some_owner = shallowMount(ExistingOwner, { propsData: some_owner_props });
        expect(some_owner.find('.delete-button').isVisible()).toBe(false);

        some_owner.find('.card').trigger('mouseover');
        expect(some_owner.find('.delete-button').isVisible()).toBe(true);

        some_owner.find('.card').trigger('mouseout');
        expect(some_owner.find('.delete-button').isVisible()).toBe(false);
    })

    test('should validate updated resource owner allowed scopes', () => {
        const error_message = 'Allowed scopes cannot be empty'
        const error_label = `data-error="${error_message}"`
        const error_title = `title="${error_message}"`

        const some_owner = shallowMount(ExistingOwner, { propsData: some_owner_props });
        some_owner.setData({ controls_showing: true, editing: true });

        expect(some_owner.html()).not.toContain(error_label);
        expect(some_owner.html()).not.toContain(error_title);

        some_owner.find('.allowed-scopes').setValue('');

        some_owner.find('.save-button').trigger('click');

        expect(some_owner.html()).toContain(error_label);
        expect(some_owner.html()).toContain(error_title);
    })

    test('should successfully update resource owner', () => {
        const spy = jest.spyOn(requests, 'put_owner').mockImplementation(
            () => { return Promise.resolve({ success: true }); }
        );

        const some_owner = shallowMount(ExistingOwner, { propsData: some_owner_props });
        some_owner.setData({ controls_showing: true, editing: true });
        some_owner.find('.allowed-scopes').setValue('x,y,z');

        some_owner.find('.save-button').trigger('click');

        return Vue.nextTick().then(function () {
            expect(some_owner.find('.save-button').element).toBeUndefined();
            expect(spy).toHaveBeenCalled();
            spy.mockRestore();
        });
    })

    test('should not update unchanged resource owner', () => {
        const spy = jest.spyOn(requests, 'put_owner').mockImplementation(
            () => { return Promise.resolve({ success: true }); }
        );

        const some_owner = shallowMount(ExistingOwner, { propsData: some_owner_props });
        some_owner.setData({ controls_showing: true, editing: true });

        some_owner.find('.save-button').trigger('click');

        return Vue.nextTick().then(function () {
            expect(some_owner.find('.save-button').element).toBeUndefined();
            expect(spy).not.toHaveBeenCalled();
            spy.mockRestore();
        });
    })

    test('should validate updated resource owner password', () => {
        const error_message = 'Password cannot be empty'
        const error_label = `data-error="${error_message}"`
        const error_title = `title="${error_message}"`

        const some_owner = shallowMount(ExistingOwner, { propsData: some_owner_props });
        some_owner.setData({ controls_showing: true, editing: true, editing_password: true });

        expect(some_owner.html()).not.toContain(error_label);
        expect(some_owner.html()).not.toContain(error_title);

        some_owner.find('.password').setValue('');
        some_owner.find('.password-confirm').setValue('abc');

        some_owner.find('.save-button').trigger('click');

        expect(some_owner.html()).toContain(error_label);
        expect(some_owner.html()).toContain(error_title);
    })

    test('should confirm updated resource owner passwords', () => {
        const error_message = 'Passwords must be provided and must match'
        const error_label = `data-error="${error_message}"`
        const error_title = `title="${error_message}"`

        const some_owner = shallowMount(ExistingOwner, { propsData: some_owner_props });
        some_owner.setData({ controls_showing: true, editing: true, editing_password: true });

        expect(some_owner.html()).not.toContain(error_label);
        expect(some_owner.html()).not.toContain(error_title);

        some_owner.find('.password').setValue('abc');
        some_owner.find('.password-confirm').setValue('def');

        some_owner.find('.save-button').trigger('click');

        expect(some_owner.html()).toContain(error_label);
        expect(some_owner.html()).toContain(error_title);
    })

    test('should allow updating resource owner passwords', () => {
        const spy = jest.spyOn(requests, 'put_owner_credentials').mockImplementation(
            () => { return Promise.resolve({ success: true }); }
        );

        const some_owner = shallowMount(ExistingOwner, { propsData: some_owner_props });
        some_owner.setData({ controls_showing: true, editing: true, editing_password: true });

        some_owner.find('.password').setValue('abc');
        some_owner.find('.password-confirm').setValue('abc');

        some_owner.find('.save-button').trigger('click');

        return Vue.nextTick().then(function () {
            expect(some_owner.find('.save-button').element).toBeUndefined();
            expect(spy).toHaveBeenCalled();
            spy.mockRestore();
        });
    })

    test('should allow cancelling resource owner updates', () => {
        const spy = jest.spyOn(requests, 'put_owner').mockImplementation(
            () => { return Promise.resolve({ success: true }); }
        );

        const some_owner = shallowMount(ExistingOwner, { propsData: some_owner_props });
        some_owner.setData({ controls_showing: true, editing: true });
        some_owner.find('.allowed-scopes').setValue('x,y,z');

        some_owner.find('.cancel-button').trigger('click');

        return Vue.nextTick().then(function () {
            expect(some_owner.find('.cancel-button').element).toBeUndefined();
            expect(spy).not.toHaveBeenCalled();
            spy.mockRestore();
        });
    })

    test('should successfully remove existing resource owners', () => {
        const spy = jest.spyOn(requests, 'delete_owner').mockImplementation(
            () => { return Promise.resolve({ success: true }); }
        );

        const some_owner = shallowMount(ExistingOwner, { propsData: some_owner_props });
        some_owner.setData({ controls_showing: true });

        some_owner.find('.delete-button').trigger('click');

        return Vue.nextTick().then(function () {
            const owner_deleted = some_owner.emitted()['owner-deleted'][0][0];
            expect(owner_deleted).toBe(owner.username);
            spy.mockRestore();
        });
    })

    test('should handle resource owner removal failures', () => {
        const spy = jest.spyOn(requests, 'delete_owner').mockImplementation(
            () => { return Promise.resolve({ error: 'failed' }); }
        );

        const some_owner = shallowMount(ExistingOwner, { propsData: some_owner_props });
        some_owner.setData({ controls_showing: true });

        some_owner.find('.delete-button').trigger('click');

        return Vue.nextTick().then(function () {
            expect(some_owner.emitted()['owner-deleted']).toBeUndefined();
            spy.mockRestore();
        });
    })
})

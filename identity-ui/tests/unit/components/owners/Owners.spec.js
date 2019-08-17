import Vue from 'vue'
import { shallowMount } from '@vue/test-utils'
import Owners from '@/components/owners/Owners'
import requests from '@/api/requests'

describe('owners', () => {
    const new_owner_container = '<new-owner-container-stub></new-owner-container-stub>'
    const existing_owner_container = /<owner-container-stub owner=".*"><\/owner-container-stub>/;

    test('should render new owner container', () => {
        const spy = jest.spyOn(requests, 'get_owners').mockImplementation(
            () => { return Promise.resolve({ entries: [] }); }
        );

        const owners = shallowMount(Owners);

        return Vue.nextTick().then(function () {
            expect(owners.emitted()['loading-completed']).toBeDefined();
            expect(owners.html()).toContain(new_owner_container);
            expect(owners.html()).not.toMatch(existing_owner_container);
            spy.mockRestore();
        });
    })

    test('should render existing owner containers', () => {
        const spy = jest.spyOn(requests, 'get_owners').mockImplementation(
            () => { return Promise.resolve({ entries: [{ username: 'a' }, { username: 'b' }] }); }
        );

        const owners = shallowMount(Owners);

        return Vue.nextTick().then(function () {
            expect(owners.emitted()['loading-completed']).toBeDefined();
            expect(owners.html()).toContain(new_owner_container);
            expect(owners.html()).toMatch(existing_owner_container);
            spy.mockRestore();
        });
    })

    test('should handle access denied responses', () => {
        const spy = jest.spyOn(requests, 'get_owners').mockImplementation(
            () => { return Promise.resolve({ error: 'access_denied' }); }
        );

        const owners = shallowMount(Owners);

        return Vue.nextTick().then(function () {
            expect(owners.emitted()['loading-completed']).toBeDefined();
            expect(owners.html()).toContain('You do not have permission to view resource owners');
            spy.mockRestore();
        });
    })

    test('should handle failures', () => {
        const spy = jest.spyOn(requests, 'get_owners').mockImplementation(
            () => { return Promise.resolve({ error: 'failed' }); }
        );

        const owners = shallowMount(Owners);

        return Vue.nextTick().then(function () {
            expect(owners.emitted()['loading-completed']).toBeDefined();
            expect(owners.html()).not.toMatch(existing_owner_container);
            spy.mockRestore();
        });
    })

    test('should handle owner creation events', () => {
        const spy = jest.spyOn(requests, 'get_owners').mockImplementation(
            () => { return Promise.resolve({ entries: [] }); }
        );

        const owners = shallowMount(Owners);

        return Vue.nextTick().then(function () {
            expect(owners.emitted()['loading-completed']).toBeDefined();
            expect(owners.html()).toContain(new_owner_container);
            expect(owners.html()).not.toMatch(existing_owner_container);

            owners.find('new-owner-container-stub').vm.$emit('owner-created', { username: "some-owner", is_new: true })
            expect(owners.html()).toContain(new_owner_container);
            expect(owners.html()).toMatch(existing_owner_container);
            spy.mockRestore();
        });
    })

    test('should handle owner deletion events', () => {
        const spy = jest.spyOn(requests, 'get_owners').mockImplementation(
            () => { return Promise.resolve({ entries: [{ username: 'a' }] }); }
        );

        const owners = shallowMount(Owners);

        return Vue.nextTick().then(function () {
            expect(owners.emitted()['loading-completed']).toBeDefined();
            expect(owners.html()).toContain(new_owner_container);
            expect(owners.html()).toMatch(existing_owner_container);

            owners.find('owner-container-stub').vm.$emit('owner-deleted', 'a')
            expect(owners.html()).toContain(new_owner_container);
            expect(owners.html()).not.toMatch(existing_owner_container);
            spy.mockRestore();
        });
    })
})

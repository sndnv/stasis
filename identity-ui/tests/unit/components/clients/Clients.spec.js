import Vue from 'vue'
import { shallowMount } from '@vue/test-utils'
import Clients from '@/components/clients/Clients'
import requests from '@/api/requests'

describe('Clients', () => {
    const new_client_container = '<new-client-container-stub></new-client-container-stub>'
    const existing_client_container = /<client-container-stub client=".*"><\/client-container-stub>/;

    test('should render new client container', () => {
        const spy = jest.spyOn(requests, 'get_clients').mockImplementation(
            () => { return Promise.resolve({ entries: [] }); }
        );

        const clients = shallowMount(Clients);

        return Vue.nextTick().then(function () {
            expect(clients.emitted()['loading-completed']).toBeDefined();
            expect(clients.html()).toContain(new_client_container);
            expect(clients.html()).not.toMatch(existing_client_container);
            spy.mockRestore();
        });
    })

    test('should render existing client containers', () => {
        const spy = jest.spyOn(requests, 'get_clients').mockImplementation(
            () => { return Promise.resolve({ entries: [{ id: 'a' }, { id: 'b' }] }); }
        );

        const clients = shallowMount(Clients);

        return Vue.nextTick().then(function () {
            expect(clients.emitted()['loading-completed']).toBeDefined();
            expect(clients.html()).toContain(new_client_container);
            expect(clients.html()).toMatch(existing_client_container);
            spy.mockRestore();
        });
    })

    test('should handle access denied responses', () => {
        const spy = jest.spyOn(requests, 'get_clients').mockImplementation(
            () => { return Promise.resolve({ error: 'access_denied' }); }
        );

        const clients = shallowMount(Clients);

        return Vue.nextTick().then(function () {
            expect(clients.emitted()['loading-completed']).toBeDefined();
            expect(clients.html()).toContain('You do not have permission to view clients');
            spy.mockRestore();
        });
    })

    test('should handle failures', () => {
        const spy = jest.spyOn(requests, 'get_clients').mockImplementation(
            () => { return Promise.resolve({ error: 'failed' }); }
        );

        const clients = shallowMount(Clients);

        return Vue.nextTick().then(function () {
            expect(clients.emitted()['loading-completed']).toBeDefined();
            expect(clients.html()).not.toMatch(existing_client_container);
            spy.mockRestore();
        });
    })

    test('should handle client creation events', () => {
        const spy = jest.spyOn(requests, 'get_clients').mockImplementation(
            () => { return Promise.resolve({ entries: [] }); }
        );

        const clients = shallowMount(Clients);

        return Vue.nextTick().then(function () {
            expect(clients.emitted()['loading-completed']).toBeDefined();
            expect(clients.html()).toContain(new_client_container);
            expect(clients.html()).not.toMatch(existing_client_container);

            clients.find('new-client-container-stub').vm.$emit('client-created', { id: "some-client", is_new: true })
            expect(clients.html()).toContain(new_client_container);
            expect(clients.html()).toMatch(existing_client_container);
            spy.mockRestore();
        });
    })

    test('should handle client deletion events', () => {
        const spy = jest.spyOn(requests, 'get_clients').mockImplementation(
            () => { return Promise.resolve({ entries: [{ id: 'a' }] }); }
        );

        const clients = shallowMount(Clients);

        return Vue.nextTick().then(function () {
            expect(clients.emitted()['loading-completed']).toBeDefined();
            expect(clients.html()).toContain(new_client_container);
            expect(clients.html()).toMatch(existing_client_container);

            clients.find('client-container-stub').vm.$emit('client-deleted', 'a')
            expect(clients.html()).toContain(new_client_container);
            expect(clients.html()).not.toMatch(existing_client_container);
            spy.mockRestore();
        });
    })
})

import Vue from 'vue'
import { shallowMount } from '@vue/test-utils'
import Apis from '@/components/apis/Apis'
import requests from '@/api/requests'

describe('Apis', () => {
    const new_api_container = '<new-api-container-stub></new-api-container-stub>'
    const existing_api_container = /<api-container-stub api=".*"><\/api-container-stub>/;

    test('should render new API container', () => {
        const spy = jest.spyOn(requests, 'get_apis').mockImplementation(
            () => { return Promise.resolve({ entries: [] }); }
        );

        const apis = shallowMount(Apis);

        return Vue.nextTick().then(function () {
            expect(apis.emitted()['loading-completed']).toBeDefined();
            expect(apis.html()).toContain(new_api_container);
            expect(apis.html()).not.toMatch(existing_api_container);

            spy.mockRestore();
        });
    })

    test('should render existing API containers', () => {
        const spy = jest.spyOn(requests, 'get_apis').mockImplementation(
            () => { return Promise.resolve({ entries: [{ id: 'a' }, { id: 'b' }] }); }
        );

        const apis = shallowMount(Apis);

        return Vue.nextTick().then(function () {
            expect(apis.emitted()['loading-completed']).toBeDefined();
            expect(apis.html()).toContain(new_api_container);
            expect(apis.html()).toMatch(existing_api_container);

            spy.mockRestore();
        });
    })

    test('should handle access denied responses', () => {
        const spy = jest.spyOn(requests, 'get_apis').mockImplementation(
            () => { return Promise.resolve({ error: 'access_denied' }); }
        );

        const apis = shallowMount(Apis);

        return Vue.nextTick().then(function () {
            expect(apis.emitted()['loading-completed']).toBeDefined();
            expect(apis.html()).toContain('You do not have permission to view APIs');

            spy.mockRestore();
        });
    })

    test('should handle failures', () => {
        const spy = jest.spyOn(requests, 'get_apis').mockImplementation(
            () => { return Promise.resolve({ error: 'failed' }); }
        );

        const apis = shallowMount(Apis);

        return Vue.nextTick().then(function () {
            expect(apis.emitted()['loading-completed']).toBeDefined();
            expect(apis.html()).not.toMatch(existing_api_container);

            spy.mockRestore();
        });
    })

    test('should handle API creation events', () => {
        const spy = jest.spyOn(requests, 'get_apis').mockImplementation(
            () => { return Promise.resolve({ entries: [] }); }
        );

        const apis = shallowMount(Apis);

        return Vue.nextTick().then(function () {
            expect(apis.emitted()['loading-completed']).toBeDefined();
            expect(apis.html()).toContain(new_api_container);
            expect(apis.html()).not.toMatch(existing_api_container);

            apis.find('new-api-container-stub').vm.$emit('api-created', { id: "some-api", is_new: true })
            expect(apis.html()).toContain(new_api_container);
            expect(apis.html()).toMatch(existing_api_container);

            spy.mockRestore();
        });
    })

    test('should handle API deletion events', () => {
        const spy = jest.spyOn(requests, 'get_apis').mockImplementation(
            () => { return Promise.resolve({ entries: [{ id: 'a' }] }); }
        );

        const apis = shallowMount(Apis);

        return Vue.nextTick().then(function () {
            expect(apis.emitted()['loading-completed']).toBeDefined();
            expect(apis.html()).toContain(new_api_container);
            expect(apis.html()).toMatch(existing_api_container);

            apis.find('api-container-stub').vm.$emit('api-deleted', 'a')
            expect(apis.html()).toContain(new_api_container);
            expect(apis.html()).not.toMatch(existing_api_container);

            spy.mockRestore();
        });
    })
})

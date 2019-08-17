import Vue from 'vue'
import { shallowMount } from '@vue/test-utils'
import ExistingApi from '@/components/apis/ExistingApi'
import requests from '@/api/requests'

jest.mock('@/api/oauth', () => (
    {
        get_context: () => { return { api: 'test-api' }; }
    }
))

describe('ExistingApi', () => {
    let default_window_confirm = null;

    beforeAll(() => {
        default_window_confirm = window.confirm;
        window.confirm = jest.fn(() => true);
    })

    afterAll(() => {
        window.confirm = default_window_confirm;
    })

    test('should explicitly mark the current API', () => {
        const current_api_title = 'title="Current API"'

        const some_api = shallowMount(ExistingApi, { propsData: { api: { id: 'some-api' } } });
        expect(some_api.html()).not.toContain(current_api_title);

        const current_api = shallowMount(ExistingApi, { propsData: { api: { id: 'test-api' } } });
        expect(current_api.html()).toContain(current_api_title);
    })

    test('should explicitly mark newly created APIs', () => {
        const new_api_badge = '<span class="new badge"></span>';

        const some_api = shallowMount(ExistingApi, { propsData: { api: { id: 'some-api' } } });
        expect(some_api.html()).not.toContain(new_api_badge);

        const new_api = shallowMount(ExistingApi, { propsData: { api: { id: 'some-api', is_new: true } } });
        expect(new_api.html()).toContain(new_api_badge);
    })

    test('should not allow removing the current API', () => {
        const some_api = shallowMount(ExistingApi, { propsData: { api: { id: 'some-api' } } });
        some_api.setData({ controls_showing: true });
        expect(some_api.find('.delete-button').isVisible()).toBe(true);

        const current_api = shallowMount(ExistingApi, { propsData: { api: { id: 'test-api' } } });
        current_api.setData({ controls_showing: true });
        expect(current_api.find('.delete-button').isVisible()).toBe(false);
    })

    test('should show/hide controls on mouseover/mouseout', () => {
        const some_api = shallowMount(ExistingApi, { propsData: { api: { id: 'some-api' } } });
        expect(some_api.find('.delete-button').isVisible()).toBe(false);

        some_api.find('.card').trigger('mouseover');
        expect(some_api.find('.delete-button').isVisible()).toBe(true);

        some_api.find('.card').trigger('mouseout');
        expect(some_api.find('.delete-button').isVisible()).toBe(false);
    })

    test('should successfully remove existing APIs', () => {
        const spy = jest.spyOn(requests, 'delete_api').mockImplementation(
            () => { return Promise.resolve({ success: true }); }
        );

        const some_api_id = 'some-api'
        const some_api = shallowMount(ExistingApi, { propsData: { api: { id: some_api_id } } });
        some_api.setData({ controls_showing: true });

        some_api.find('.delete-button').trigger('click');

        return Vue.nextTick().then(function () {
            const api_deleted = some_api.emitted()['api-deleted'][0][0];
            expect(api_deleted).toBe(some_api_id);
            spy.mockRestore();
        });
    })

    test('should handle API removal failures', () => {
        const spy = jest.spyOn(requests, 'delete_api').mockImplementation(
            () => { return Promise.resolve({ error: 'failed' }); }
        );

        const some_api = shallowMount(ExistingApi, { propsData: { api: { id: 'some-api' } } });
        some_api.setData({ controls_showing: true });

        some_api.find('.delete-button').trigger('click');

        return Vue.nextTick().then(function () {
            expect(some_api.emitted()['api-deleted']).toBeUndefined();
            spy.mockRestore();
        });
    })
})

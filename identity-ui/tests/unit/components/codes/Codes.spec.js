import Vue from 'vue'
import { shallowMount } from '@vue/test-utils'
import Codes from '@/components/codes/Codes'
import requests from '@/api/requests'

describe('Codes', () => {
    let default_window_confirm = null;

    beforeAll(() => {
        default_window_confirm = window.confirm;
        window.confirm = jest.fn(() => true);
    })

    afterAll(() => {
        window.confirm = default_window_confirm;
    })

    const code = { code: 'some-code', client: 'some-client', owner: 'some-owner', scope: 'a:b:c' }
    const existing_code_row = /<tr class="code-row">.*<\/tr>/;

    test('should render empty codes table', () => {
        const spy = jest.spyOn(requests, 'get_codes').mockImplementation(
            () => { return Promise.resolve({ entries: [] }); }
        );

        const codes = shallowMount(Codes);

        return Vue.nextTick().then(function () {
            expect(codes.emitted()['loading-completed']).toBeDefined();
            expect(codes.html()).not.toMatch(existing_code_row);
            spy.mockRestore();
        });
    })

    test('should render existing codes', () => {
        const spy = jest.spyOn(requests, 'get_codes').mockImplementation(
            () => { return Promise.resolve({ entries: [code, { ...code, ...{ code: 'other-code' } }] }); }
        );

        const codes = shallowMount(Codes);

        return Vue.nextTick().then(function () {
            expect(codes.emitted()['loading-completed']).toBeDefined();
            expect(codes.html()).toMatch(existing_code_row);
            spy.mockRestore();
        });
    })

    test('should handle access denied responses', () => {
        const spy = jest.spyOn(requests, 'get_codes').mockImplementation(
            () => { return Promise.resolve({ error: 'access_denied' }); }
        );

        const codes = shallowMount(Codes);

        return Vue.nextTick().then(function () {
            expect(codes.emitted()['loading-completed']).toBeDefined();
            expect(codes.html()).toContain('You do not have permission to view authorization codes');
            spy.mockRestore();
        });
    })

    test('should handle failures', () => {
        const spy = jest.spyOn(requests, 'get_codes').mockImplementation(
            () => { return Promise.resolve({ error: 'failed' }); }
        );

        const codes = shallowMount(Codes);

        return Vue.nextTick().then(function () {
            expect(codes.emitted()['loading-completed']).toBeDefined();
            expect(codes.html()).not.toMatch(existing_code_row);
            spy.mockRestore();
        });
    })

    test('should successfully remove existing codes', () => {
        const codes_spy = jest.spyOn(requests, 'get_codes').mockImplementation(
            () => { return Promise.resolve({ entries: [code] }); }
        );

        const delete_spy = jest.spyOn(requests, 'delete_code').mockImplementation(
            () => { return Promise.resolve({ success: true }); }
        );

        const codes = shallowMount(Codes);

        return Vue.nextTick().then(function () {
            expect(codes.emitted()['loading-completed']).toBeDefined();
            expect(codes.html()).toMatch(existing_code_row);

            codes.find(".code-row").find('.delete-button').trigger('click');

            return Vue.nextTick().then(function () {
                expect(codes.html()).not.toMatch(existing_code_row);
                codes_spy.mockRestore();
                delete_spy.mockRestore();
            });
        });
    })

    test('should handle code removal failures', () => {
        const codes_spy = jest.spyOn(requests, 'get_codes').mockImplementation(
            () => { return Promise.resolve({ entries: [code] }); }
        );

        const delete_spy = jest.spyOn(requests, 'delete_code').mockImplementation(
            () => { return Promise.resolve({ error: 'failed' }); }
        );

        const codes = shallowMount(Codes);

        return Vue.nextTick().then(function () {
            expect(codes.emitted()['loading-completed']).toBeDefined();
            expect(codes.html()).toMatch(existing_code_row);

            codes.find(".code-row").find('.delete-button').trigger('click');

            return Vue.nextTick().then(function () {
                expect(codes.html()).toMatch(existing_code_row);
                codes_spy.mockRestore();
                delete_spy.mockRestore();
            });
        });
    })
})

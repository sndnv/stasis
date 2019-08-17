import { shallowMount } from '@vue/test-utils'
import AuthorizationCallback from '@/components/AuthorizationCallback'

describe('AuthorizationCallback', () => {
    test('should render callback errors', () => {
        const $route = { query: { error: 'failed', error_description: 'some-failure' } }
        const callback = shallowMount(AuthorizationCallback, { mocks: { $route } });

        expect(callback.html()).toMatch(/<span class="card-title">failed<\/span>/);
        expect(callback.html()).toMatch(/<p>some-failure<\/p>/);
    })

    test('should render generic errors', () => {
        const $route = { query: {} }
        const callback = shallowMount(AuthorizationCallback, { mocks: { $route }, stubs: ['generic-error'] });

        expect(callback.html()).toMatch(/<generic-error-stub><\/generic-error-stub>/);
    })
})

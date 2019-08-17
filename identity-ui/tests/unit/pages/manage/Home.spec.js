import { shallowMount } from '@vue/test-utils'
import Home from '@/pages/manage/Home'

describe('Home', () => {
    test('should render home page', () => {
        const home = shallowMount(Home);

        expect(home.emitted()['loading-completed']).toBeDefined();
    })

    test('should toggle config view', () => {
        const home = shallowMount(Home);

        expect(home.html()).not.toMatch(/<pre class="config">/);
        home.find(".toggle-config").trigger('click');
        expect(home.html()).toMatch(/<pre class="config">/);
    })
})

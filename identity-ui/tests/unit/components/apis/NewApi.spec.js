import Vue from 'vue'
import { shallowMount } from '@vue/test-utils'
import NewApi from '@/components/apis/NewApi'
import requests from '@/api/requests'

describe('NewApi', () => {
  test('should validate API identifiers', () => {
    const new_api = shallowMount(NewApi);
    const error_message = 'API identifier cannot be empty'
    const error_label = `data-error="${error_message}"`
    const error_title = `title="${error_message}"`

    expect(new_api.html()).not.toContain(error_label);
    expect(new_api.html()).not.toContain(error_title);

    new_api.find('.create-button').trigger('click');

    expect(new_api.html()).toContain(error_label);
    expect(new_api.html()).toContain(error_title);
  })

  test('should successfully create new APIs', () => {
    const new_api_id = 'some-api';

    const spy = jest.spyOn(requests, 'post_api').mockImplementation(
      () => { return Promise.resolve({ success: true }); }
    );

    const new_api = shallowMount(NewApi);

    const input = new_api.find('#new-api-id');
    input.setValue(new_api_id);
    expect(input.element.value).toBe(new_api_id);

    new_api.find('.create-button').trigger('click');

    return Vue.nextTick().then(function () {
      expect(input.element.value).toBe('');
      const api_created = new_api.emitted()['api-created'][0][0];
      expect(api_created.id).toBe(new_api_id);
      expect(api_created.is_new).toBe(true);

      spy.mockRestore();
    });
  })

  test('should handle API creation failures', () => {
    const new_api_id = 'some-api';

    const spy = jest.spyOn(requests, 'post_api').mockImplementation(
      () => { return Promise.resolve({ error: "failed" }); }
    );

    const new_api = shallowMount(NewApi);

    const input = new_api.find('#new-api-id');
    input.setValue(new_api_id);
    expect(input.element.value).toBe(new_api_id);

    new_api.find('.create-button').trigger('click');

    return Vue.nextTick().then(function () {
      expect(input.element.value).toBe(new_api_id);
      expect(new_api.emitted()['api-created']).toBeUndefined();

      spy.mockRestore();
    });
  })
})

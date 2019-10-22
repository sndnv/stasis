import { service } from '@/api/requests'
import requests from '@/api/requests'
import oauth from '@/api/oauth'
import config from '../../../identity.config.js'
import MockAdapter from 'axios-mock-adapter';

describe('requests', () => {
    let token_spy = null;
    let service_mock = null;

    const service_url = function (entries_type) {
        return new RegExp(`${config.identity_api_url}/api/manage/${entries_type}.*`);
    }

    beforeAll(() => {
        token_spy = jest.spyOn(oauth, 'get_token').mockImplementation(() => { return 'some-token'; });
        service_mock = new MockAdapter(service);
    })

    afterAll(() => {
        service_mock.restore();
        token_spy.mockRestore();
    })

    test('should successfully retrieve APIs', () => {
        const api = { id: 'some-api' };
        service_mock.onGet(service_url('apis')).reply(200, [api]);

        return requests.get_apis().then(function (result) {
            expect(result).toEqual({ entries: [api] });
        });
    })

    test('should successfully create APIs', () => {
        const api = { id: 'some-api' };
        service_mock.onPost(service_url('apis')).reply(200, 'success');

        return requests.post_api(api).then(function (result) {
            expect(result).toEqual({ success: true, data: 'success' });
        });
    })

    test('should successfully remove APIs', () => {
        service_mock.onDelete(service_url('apis')).reply(200);

        return requests.delete_api('some-api').then(function (result) {
            expect(result).toEqual({ success: true });
        });
    })

    test('should successfully retrieve clients', () => {
        const client = { id: 'some-client', redirect_uri: 'some-uri', token_expiration: 42, active: true };
        service_mock.onGet(service_url('clients')).reply(200, [client]);

        return requests.get_clients().then(function (result) {
            expect(result).toEqual({ entries: [client] });
        });
    })

    test('should successfully create clients', () => {
        const client = { id: 'some-client', redirect_uri: 'some-uri', token_expiration: 42, active: true };
        service_mock.onPost(service_url('clients')).reply(200, 'success');

        return requests.post_client(client).then(function (result) {
            expect(result).toEqual({ success: true, data: 'success' });
        });
    })

    test('should successfully update clients', () => {
        const client_update = { token_expiration: 23, active: false };
        service_mock.onPut(service_url('clients')).reply(200, 'success');

        return requests.put_client('some-client', client_update).then(function (result) {
            expect(result).toEqual({ success: true, data: 'success' });
        });
    })

    test('should successfully update client credentials', () => {
        const client_credentials = { raw_secret: 'some-secret' };
        service_mock.onPut(service_url('clients')).reply(200, 'success');

        return requests.put_client_credentials('some-client', client_credentials).then(function (result) {
            expect(result).toEqual({ success: true, data: 'success' });
        });
    })

    test('should successfully remove clients', () => {
        service_mock.onDelete(service_url('clients')).reply(200);

        return requests.delete_client('some-client').then(function (result) {
            expect(result).toEqual({ success: true });
        });
    })

    test('should successfully retrieve resource owners', () => {
        const owner = { username: 'some-owner', allowed_scopes: ['a', 'b', 'c'], active: true };
        service_mock.onGet(service_url('owners')).reply(200, [owner]);

        return requests.get_owners().then(function (result) {
            expect(result).toEqual({ entries: [owner] });
        });
    })

    test('should successfully create resource owners', () => {
        const owner = { username: 'some-owner', allowed_scopes: ['a', 'b', 'c'], active: true };
        service_mock.onPost(service_url('owners')).reply(200, 'success');

        return requests.post_owner(owner).then(function (result) {
            expect(result).toEqual({ success: true, data: 'success' });
        });
    })

    test('should successfully update resource owners', () => {
        const owner_update = { allowed_scopes: ['d'], active: false };
        service_mock.onPut(service_url('owners')).reply(200, 'success');

        return requests.put_owner('some-owner', owner_update).then(function (result) {
            expect(result).toEqual({ success: true, data: 'success' });
        });
    })

    test('should successfully update resource owner credentials', () => {
        const owner_credentials = { raw_password: 'some-password' };
        service_mock.onPut(service_url('owners')).reply(200, 'success');

        return requests.put_owner_credentials('some-owner', owner_credentials).then(function (result) {
            expect(result).toEqual({ success: true, data: 'success' });
        });
    })

    test('should successfully remove resource owners', () => {
        service_mock.onDelete(service_url('owners')).reply(200);

        return requests.delete_owner('some-owner').then(function (result) {
            expect(result).toEqual({ success: true });
        });
    })

    test('should successfully retrieve authorization codes', () => {
        const code = { code: 'some-code', client: 'some-client', owner: 'some-owner' };
        service_mock.onGet(service_url('codes')).reply(200, [code]);

        return requests.get_codes().then(function (result) {
            expect(result).toEqual({ entries: [code] });
        });
    })

    test('should successfully remove authorization codes', () => {
        service_mock.onDelete(service_url('codes')).reply(200);

        return requests.delete_code('some-code').then(function (result) {
            expect(result).toEqual({ success: true });
        });
    })

    test('should successfully retrieve refresh tokens', () => {
        const token = { token: 'some-token', client: 'some-client', owner: 'some-owner' };
        service_mock.onGet(service_url('tokens')).reply(200, [token]);

        return requests.get_tokens().then(function (result) {
            expect(result).toEqual({ entries: [token] });
        });
    })

    test('should successfully remove refresh tokens', () => {
        service_mock.onDelete(service_url('tokens')).reply(200);

        return requests.delete_token('some-token').then(function (result) {
            expect(result).toEqual({ success: true });
        });
    })

    test('should handle authentication failures', () => {
        service_mock.onGet(service_url('apis')).reply(401);

        return requests.get_apis().then(function (result) {
            expect(result).toEqual({ error: 'not_authenticated' });
        });
    })

    test('should handle authorization failures', () => {
        service_mock.onGet(service_url('apis')).reply(403);

        return requests.get_apis().then(function (result) {
            expect(result).toEqual({ error: 'access_denied' });
        });
    })

    test('should handle generic failures', () => {
        service_mock.onGet(service_url('apis')).reply(500);

        return requests.get_apis().then(function (result) {
            expect(result).toEqual({ error: 500 });
        });
    })
})

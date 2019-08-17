import oauth from '@/api/oauth'
import axios from 'axios'
import crypto from 'crypto'
import config from '../../../identity.config.js'

describe('oauth', () => {
    const user = 'some-user'
    const token = 'some-token'
    const context = {
        api: config.authentication.scope.split(":").pop(),
        client: config.authentication.client_id,
        owner: user
    }

    test('should successfully handle authorizations', () => {
        const data = 'some-data'
        const spy = jest.spyOn(axios, 'get').mockImplementation(
            () => { return Promise.resolve({ status: 200, data: data }); }
        );

        const params = { a: 'b', c: 4 }

        return oauth.authorize(user, 'some-password', params).then(function (result) {
            expect(result.success).toBe(true);
            expect(result.data).toEqual(data);
            spy.mockRestore();
        });
    })

    test('should handle authorization failures (invalid credentials)', () => {
        const spy = jest.spyOn(axios, 'get').mockImplementation(
            () => { return Promise.reject({ response: { status: 401 } }); }
        );

        const params = { a: 'b', c: 4 }

        return oauth.authorize(user, 'some-password', params).then(function (result) {
            expect(result.success).toBeUndefined();
            expect(result).toEqual({ error: 'access_denied' });
            spy.mockRestore();
        });
    })

    test('should handle authorization failures (general failures)', () => {
        const spy = jest.spyOn(axios, 'get').mockImplementation(
            () => { return Promise.reject({ response: { status: 500, data: { error: 'failed' } } }); }
        );

        const params = { a: 'b', c: 4 }

        return oauth.authorize(user, 'some-password', params).then(function (result) {
            expect(result.success).toBeUndefined();
            expect(result).toEqual({ error: 'failed' });
            spy.mockRestore();
        });
    })

    test('should successfully log in and out', () => {
        const get_spy = jest.spyOn(axios, 'get').mockImplementation(
            (request_url) => {
                const url = new URL(request_url);

                const code = 'some-code';
                const state = url.searchParams.get('state');
                const scope = url.searchParams.get('scope');
                const response_url = `${config.authentication.redirect_uri}?code=${code}&state=${state}&scope=${scope}`;

                return Promise.resolve({ request: { responseURL: response_url } });
            }
        );

        const post_spy = jest.spyOn(axios, 'post').mockImplementation(
            () => { return Promise.resolve({ data: { access_token: token, expires_in: 42 } }); }
        );

        expect(oauth.get_token()).toBeNull();
        expect(oauth.get_context()).toEqual({});
        expect(oauth.is_authenticated()).toBe(false);

        return oauth.login(user, "some-password").then(function (result) {
            expect(result.success).toBe(true);

            expect(oauth.get_token()).toBe(token);
            expect(oauth.get_context()).toEqual(context);
            expect(oauth.is_authenticated()).toBe(true);

            oauth.logout();

            expect(oauth.get_token()).toBeNull();
            expect(oauth.get_context()).toEqual({});
            expect(oauth.is_authenticated()).toBe(false);

            get_spy.mockRestore();
            post_spy.mockRestore();
        });
    })

    test('should handle failures during login', () => {
        const get_spy = jest.spyOn(axios, 'get').mockImplementation(
            () => {
                const error = 'failed'
                const error_description = 'failed'
                const response_url = `${config.authentication.redirect_uri}?error=${error}&error_description=${error_description}`;

                return Promise.reject({ request: { responseURL: response_url } });
            }
        );

        return oauth.login(user, "some-password").then(function (result) {
            expect(result.error).toBe('failed');

            expect(oauth.get_token()).toBeNull();
            expect(oauth.get_context()).toEqual({});
            expect(oauth.is_authenticated()).toBe(false);

            oauth.logout();
            get_spy.mockRestore();
        });
    })

    test('should handle missing authorization codes during login', () => {
        const get_spy = jest.spyOn(axios, 'get').mockImplementation(
            () => {
                const response_url = `${config.authentication.redirect_uri}`;

                return Promise.resolve({ request: { responseURL: response_url } });
            }
        );

        return oauth.login(user, "some-password").then(function (result) {
            expect(result.error).toBe('missing_code');

            expect(oauth.get_token()).toBeNull();
            expect(oauth.get_context()).toEqual({});
            expect(oauth.is_authenticated()).toBe(false);

            oauth.logout();
            get_spy.mockRestore();
        });
    })

    test('should handle invalid state provided during login', () => {
        const get_spy = jest.spyOn(axios, 'get').mockImplementation(
            (request_url) => {
                const url = new URL(request_url);

                const code = 'some-code';
                const state = 'some-state';
                const scope = url.searchParams.get('scope');
                const response_url = `${config.authentication.redirect_uri}?code=${code}&state=${state}&scope=${scope}`;

                return Promise.resolve({ request: { responseURL: response_url } });
            }
        );

        return oauth.login(user, "some-password").then(function (result) {
            expect(result.error).toBe('invalid_state');

            expect(oauth.get_token()).toBeNull();
            expect(oauth.get_context()).toEqual({});
            expect(oauth.is_authenticated()).toBe(false);

            oauth.logout();
            get_spy.mockRestore();
        });
    })

    test('should handle invalid redirect URI provided during login', () => {
        const get_spy = jest.spyOn(axios, 'get').mockImplementation(
            (request_url) => {
                const url = new URL(request_url);

                const code = 'some-code';
                const state = url.searchParams.get('state');
                const scope = url.searchParams.get('scope');
                const response_url = `http://example.com?code=${code}&state=${state}&scope=${scope}`;

                return Promise.resolve({ request: { responseURL: response_url } });
            }
        );

        return oauth.login(user, "some-password").then(function (result) {
            expect(result.error).toBe('invalid_redirect_uri');

            expect(oauth.get_token()).toBeNull();
            expect(oauth.get_context()).toEqual({});
            expect(oauth.is_authenticated()).toBe(false);

            oauth.logout();
            get_spy.mockRestore();
        });
    })

    test('should handle token request failures during login', () => {
        const get_spy = jest.spyOn(axios, 'get').mockImplementation(
            (request_url) => {
                const url = new URL(request_url);

                const code = 'some-code';
                const state = url.searchParams.get('state');
                const scope = url.searchParams.get('scope');
                const response_url = `${config.authentication.redirect_uri}?code=${code}&state=${state}&scope=${scope}`;

                return Promise.resolve({ request: { responseURL: response_url } });
            }
        );

        const post_spy = jest.spyOn(axios, 'post').mockImplementation(
            () => { return Promise.reject({ response: { data: { error: 'failed', error_description: 'failed' } } }); }
        );

        return oauth.login(user, "some-password").then(function (result) {
            expect(result.error).toBe('failed');

            expect(oauth.get_token()).toBeNull();
            expect(oauth.get_context()).toEqual({});
            expect(oauth.is_authenticated()).toBe(false);

            oauth.logout();
            get_spy.mockRestore();
            post_spy.mockRestore();
        });
    })

    test('should handle missing token failures during login', () => {
        const get_spy = jest.spyOn(axios, 'get').mockImplementation(
            (request_url) => {
                const url = new URL(request_url);

                const code = 'some-code';
                const state = url.searchParams.get('state');
                const scope = url.searchParams.get('scope');
                const response_url = `${config.authentication.redirect_uri}?code=${code}&state=${state}&scope=${scope}`;

                return Promise.resolve({ request: { responseURL: response_url } });
            }
        );

        const post_spy = jest.spyOn(axios, 'post').mockImplementation(
            () => { return Promise.resolve({ data: {} }); }
        );

        return oauth.login(user, "some-password").then(function (result) {
            expect(result.error).toBe('missing_token');

            expect(oauth.get_token()).toBeNull();
            expect(oauth.get_context()).toEqual({});
            expect(oauth.is_authenticated()).toBe(false);

            oauth.logout();
            get_spy.mockRestore();
            post_spy.mockRestore();
        });
    })

    test('should derive passwords', () => {
        const raw_password = 'some-password'
        const derived_password = 'VcARAa1_33RzxIZzH6mCXFU4u0mjNALC_PAo9olARCWMw_fYcwHwMwLsJZ89izXBS5xGGV_J0NNv9sXxj01VCA'

        const derivation_enabled = config.authentication.secret_derivation.enabled;
        config.authentication.secret_derivation.enabled = true;

        return oauth.derive_password(raw_password, 'some-salt').then(function (derived) {
            expect(derived).toBe(derived_password);
            config.authentication.secret_derivation.enabled = derivation_enabled;
        });
    })

    test('should skip password derivation', () => {
        const derivation_enabled = config.authentication.secret_derivation.enabled;
        config.authentication.secret_derivation.enabled = false;

        const password = 'some-password'

        return oauth.derive_password(password, 'some-salt').then(function (derived) {
            expect(derived).toBe(password);
            config.authentication.secret_derivation.enabled = derivation_enabled;
        });
    })

    test('should handle password derivation failures', () => {
        const spy = jest.spyOn(crypto, 'pbkdf2').mockImplementation(
            (p, s, i, k, d, callback) => { callback('failed', ''); }
        );

        const derivation_enabled = config.authentication.secret_derivation.enabled;
        config.authentication.secret_derivation.enabled = true;

        return oauth.derive_password('some-password', 'some-salt').then(
            response => {
                spy.mockRestore();
                config.authentication.secret_derivation.enabled = derivation_enabled;
                throw new Error(`Unexpected response received: [${response}]`);
            },
            failure => {
                expect(failure).toBe('failed');
                spy.mockRestore();
                config.authentication.secret_derivation.enabled = derivation_enabled;
            });
    })

    test('should derive salts', () => {
        const user = 'some-user'
        expect(oauth.derive_salt(user)).toBe(user);
    })
})

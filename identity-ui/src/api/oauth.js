import axios from 'axios'
import crypto from 'crypto'
import base64url from 'base64url'
import docCookies from 'doc-cookies'
import config from '../../identity.config.js'

export default {
    get_token() {
        return get_token();
    },
    get_context() {
        return get_context();
    },
    is_authenticated() {
        return Boolean(this.get_token());
    },
    authorize(username, password, params) {
        return authorize(username, password, params);
    },
    login(username, password) {
        return make_authorization_request(username, password);
    },
    logout() {
        delete_token();
        delete_context();
    },
    derive_password(raw_password, salt) {
        return derive_password(raw_password, salt);
    },
    derive_salt(username) {
        return derive_salt(username);
    },
};

function authorize(username, password, params) {
    return derive_password(password, derive_salt(username))
        .then(
            derived_password => {
                const authorization_request_url = `${config.identity_api_url}/oauth/authorization`;
                const query = params_to_query({ ...params, ...{ no_redirect: true } })

                return axios
                    .get(`${authorization_request_url}?${query}`, {
                        auth: {
                            username: username,
                            password: derived_password
                        }
                    })
                    .then(
                        response => { return process_authorization_response(response); },
                        failure => { return process_authorization_response(failure.response); }
                    )
            },
            () => {
                return {
                    error: 'derivation_failed',
                    error_description: 'Failed to derive login password'
                }
            }
        )
}

function process_authorization_response(response) {
    if (response.status == 200) {
        return { success: true, data: response.data }
    } else if (response.status == 401) {
        return { error: 'access_denied' }
    } else {
        return response.data || { error: response.status };
    }
}

function make_authorization_request(username, password) {
    return derive_password(password, derive_salt(username))
        .then(
            derived_password => {
                const state = base64url(crypto.randomBytes(parseInt(config.authentication.state_size)));

                const code_verifier = base64url(crypto.randomBytes(parseInt(config.authentication.code_verifier_size)));
                const code_challenge = base64url(crypto.createHash('sha256').update(code_verifier).digest());
                const code_challenge_method = 's256';

                const authorization_request_url = `${config.identity_api_url}/oauth/authorization`;

                const params = {
                    response_type: 'code',
                    client_id: config.authentication.client_id,
                    redirect_uri: config.authentication.redirect_uri,
                    scope: config.authentication.scope,
                    state: state,
                    code_challenge: code_challenge,
                    code_challenge_method: code_challenge_method
                }

                const query = params_to_query(params);

                return axios
                    .get(`${authorization_request_url}?${query}`, {
                        auth: {
                            username: username,
                            password: derived_password
                        }
                    })
                    .then(
                        response => {
                            return process_authorization_redirect_request(
                                response.request,
                                config.authentication.redirect_uri,
                                state
                            );
                        },
                        failure => {
                            return process_authorization_redirect_request(
                                failure.request,
                                config.authentication.redirect_uri,
                                state
                            );
                        }
                    ).then(response => {
                        if (response.error) {
                            return response;
                        } else {
                            return make_token_request(username, response.code, code_verifier);
                        }
                    });
            },
            () => {
                return {
                    error: 'derivation_failed',
                    error_description: 'Failed to derive authentication password'
                }
            }
        )
}

function process_authorization_redirect_request(
    request,
    expected_redirect_uri,
    expected_state
) {
    const url = new URL(request.responseURL);

    const error = url.searchParams.get('error');
    const error_description = url.searchParams.get('error_description');

    const provided_state = url.searchParams.get('state');
    const code = url.searchParams.get('code');
    const scope = url.searchParams.get('scope');

    if (error) {
        return {
            error: error,
            error_description: error_description
        };
    } else if (!code) {
        return {
            error: 'missing_code',
            error_description: 'Authorization code not found in response'
        };
    } else if (expected_state !== provided_state) {
        return {
            error: 'invalid_state',
            error_description: 'Invalid state found in response'
        };
    } else if (!request.responseURL.startsWith(expected_redirect_uri)) {
        return {
            error: 'invalid_redirect_uri',
            error_description: 'Invalid redirect URL found in response'
        };
    } else {
        return {
            code: code,
            scope: scope
        };
    }
}

function make_token_request(owner, code, code_verifier) {
    const token_request_url = `${config.identity_api_url}/oauth/token`

    const params = {
        grant_type: 'authorization_code',
        code: code,
        redirect_uri: config.authentication.redirect_uri,
        client_id: config.authentication.client_id,
        code_verifier: code_verifier
    };

    const query = params_to_query(params);

    return axios
        .post(`${token_request_url}?${query}`)
        .then(
            response => { return process_token_response(response); },
            failure => { return process_token_response(failure.response); }
        ).then(response => {
            if (response.error) {
                return response;
            } else {
                store_token(response.token, response.expires_in);
                store_context(config.authentication.client_id, owner, config.authentication.scope, response.expires_in);
                return { success: true };
            }
        });
}

function process_token_response(response) {
    const error = response.data.error;
    const error_description = response.data.error_description;
    const token = response.data.access_token
    const expires_in = response.data.expires_in

    if (error) {
        return {
            error: error,
            error_description: error_description
        };
    } else if (!token) {
        return {
            error: 'missing_token',
            error_description: 'Access token not found in response'
        };
    } else {
        return {
            token: token,
            expires_in: expires_in
        };
    }
}

function derive_password(raw_password, salt) {
    if (config.authentication.secret_derivation.enabled == 'yes') {
        return new Promise(function (success, failure) {
            crypto.pbkdf2(
            /* password */ raw_password,
            /* salt */ `${config.authentication.secret_derivation.salt_prefix}-authentication-${salt}`,
            /* iterations */ parseInt(config.authentication.secret_derivation.iterations),
            /* keylen */ parseInt(config.authentication.secret_derivation.key_size),
            /* digest */ 'sha512',
                function (error, password) {
                    if (error) {
                        failure(error);
                    } else {
                        success(base64url(password));
                    }
                }
            );
        });
    } else {
        return new Promise(function (success) { success(raw_password) });
    }
}

function derive_salt(username) {
    return `${username}`;
}

function store_token(token, expiration) {
    docCookies.setItem(
        /* key */ config.cookies.authentication_token,
        /* value */ token,
        /* end */ expiration,
        /* path */ null,
        /* domain */ null,
        /* secure */ config.cookies.secure
    );
}

function get_token() {
    return docCookies.getItem(config.cookies.authentication_token);
}

function delete_token() {
    docCookies.removeItem(config.cookies.authentication_token);
}

function store_context(client, owner, scope, expiration) {
    const api = scope.split(':').slice(-1)[0];
    const context = [client, owner, api].join('|');

    docCookies.setItem(
        /* key */ config.cookies.context,
        /* value */ context,
        /* end */ expiration,
        /* path */ null,
        /* domain */ null,
        /* secure */ config.cookies.secure
    );
}

function get_context() {
    const context = docCookies.getItem(config.cookies.context);

    if (context) {
        const [client, owner, api] = context.split('|');
        return { client, owner, api };
    } else {
        return {};
    }
}

function delete_context() {
    docCookies.removeItem(config.cookies.context);
}

function params_to_query(params) {
    return Object.entries(params).map(entry => entry.join("=")).join("&");
}

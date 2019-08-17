import axios from 'axios'
import tokenProvider from 'axios-token-interceptor'
import oauth from './oauth'
import config from '../../identity.config.js'

export default {
    get_apis() { return get_entries("apis"); },
    post_api(new_api_request) { return post_entry("apis", new_api_request); },
    delete_api(api_id) { return delete_entry("apis", api_id); },

    get_clients() { return get_entries("clients"); },
    post_client(new_client_request) { return post_entry("clients", new_client_request); },
    put_client(client_id, client_update_request) { return put_entry("clients", client_id, client_update_request); },
    put_client_credentials(client_id, client_credentials_request) { return put_entry_credentials("clients", client_id, client_credentials_request); },
    delete_client(client_id) { return delete_entry("clients", client_id); },

    get_owners() { return get_entries("owners"); },
    post_owner(new_owner_request) { return post_entry("owners", new_owner_request); },
    put_owner(owner_id, owner_update_request) { return put_entry("owners", owner_id, owner_update_request); },
    put_owner_credentials(owner_id, owner_credentials_request) { return put_entry_credentials("owners", owner_id, owner_credentials_request); },
    delete_owner(owner_id) { return delete_entry("owners", owner_id); },

    get_codes() { return get_entries("codes"); },
    delete_code(code) { return delete_entry("codes", code); },

    get_tokens() { return get_entries("tokens"); },
    delete_token(token) { return delete_entry("tokens", token); },
};

function get_entries(entries_type) {
    return service
        .get(`/api/manage/${entries_type}`)
        .then(
            response => {
                return { entries: response.data };
            },
            process_request_failure
        );
}

function post_entry(entries_type, new_entry_request) {
    return service
        .post(`/api/manage/${entries_type}`, new_entry_request)
        .then(
            response => {
                return { success: true, data: response.data };
            },
            process_request_failure
        )
}

function put_entry(entries_type, entry_id, entry_update_request) {
    return service
        .put(`/api/manage/${entries_type}/${entry_id}`, entry_update_request)
        .then(
            response => { return { success: true, data: response.data }; },
            process_request_failure
        )
}

function put_entry_credentials(entries_type, entry_id, entry_credentials_request) {
    return service
        .put(`/api/manage/${entries_type}/${entry_id}/credentials`, entry_credentials_request)
        .then(
            response => {
                return { success: true, data: response.data };
            },
            process_request_failure
        )
}

function delete_entry(entries_type, entry_id) {
    return service
        .delete(`/api/manage/${entries_type}/${entry_id}`)
        .then(
            () => {
                return { success: true };
            },
            process_request_failure
        )
}

function process_request_failure(failure) {
    if (failure.response.status === 401) {
        oauth.logout();
        return { error: "not_authenticated" };
    } else if (failure.response.status === 403) {
        return { error: "access_denied" };
    } else {
        return { error: failure.response.data || failure.response.status };
    }
}

export const service = axios.create({
    baseURL: config.identity_api_url
});

service.interceptors.request.use(tokenProvider({ getToken: () => oauth.get_token() }));

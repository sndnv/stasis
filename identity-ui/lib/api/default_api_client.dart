import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:identity_ui/api/api_client.dart';
import 'package:identity_ui/model/api.dart';
import 'package:identity_ui/model/client.dart';
import 'package:identity_ui/model/requests/create_api.dart';
import 'package:identity_ui/model/requests/create_client.dart';
import 'package:identity_ui/model/requests/create_owner.dart';
import 'package:identity_ui/model/requests/update_client.dart';
import 'package:identity_ui/model/requests/update_client_credentials.dart';
import 'package:identity_ui/model/requests/update_owner.dart';
import 'package:identity_ui/model/requests/update_owner_credentials.dart';
import 'package:identity_ui/model/resource_owner.dart';
import 'package:identity_ui/model/stored_authorization_code.dart';
import 'package:identity_ui/model/stored_refresh_token.dart';
import 'package:oauth2/oauth2.dart' as oauth2;

class DefaultApiClient implements ApiClient {
  DefaultApiClient({
    required this.server,
    required this.underlying,
    this.clientId,
    this.subject,
    this.audience,
  });

  String server;

  @override
  String? clientId;

  @override
  String? subject;

  @override
  String? audience;

  http.Client underlying;

  @override
  Future<List<Api>> getApis() async {
    return await _get<Api>(from: 'apis', fromJson: Api.fromJson);
  }

  @override
  Future<void> postApi(CreateApi request) async {
    return _post(data: request.toJson(), to: 'apis');
  }

  @override
  Future<void> deleteApi(String id) async {
    return await _delete(from: 'apis', id: id);
  }

  @override
  Future<List<Client>> getClients() async {
    return await _get<Client>(from: 'clients', fromJson: Client.fromJson);
  }

  @override
  Future<void> postClient(CreateClient request) async {
    return _post(data: request.toJson(), to: 'clients');
  }

  @override
  Future<void> putClient(String id, UpdateClient request) async {
    return _put(data: request.toJson(), to: 'clients', id: id);
  }

  @override
  Future<void> putClientCredentials(String id, UpdateClientCredentials request) async {
    return _putCredentials(data: request.toJson(), to: 'clients', id: id);
  }

  @override
  Future<void> deleteClient(String id) async {
    return await _delete(from: 'clients', id: id);
  }

  @override
  Future<List<ResourceOwner>> getOwners() async {
    return await _get<ResourceOwner>(from: 'owners', fromJson: ResourceOwner.fromJson);
  }

  @override
  Future<void> postOwner(CreateOwner request) async {
    return _post(data: request.toJson(), to: 'owners');
  }

  @override
  Future<void> putOwner(String id, UpdateOwner request) async {
    return _put(data: request.toJson(), to: 'owners', id: id);
  }

  @override
  Future<void> putOwnerCredentials(String id, UpdateOwnerCredentials request) async {
    return _putCredentials(data: request.toJson(), to: 'owners', id: id);
  }

  @override
  Future<void> deleteOwner(String id) async {
    return _delete(from: 'owners', id: id);
  }

  @override
  Future<List<StoredAuthorizationCode>> getCodes() async {
    return _get<StoredAuthorizationCode>(from: 'codes', fromJson: StoredAuthorizationCode.fromJson);
  }

  @override
  Future<void> deleteCode(String code) async {
    return _delete(from: 'codes', id: code);
  }

  @override
  Future<List<StoredRefreshToken>> getTokens() async {
    return _get<StoredRefreshToken>(from: 'tokens', fromJson: StoredRefreshToken.fromJson);
  }

  @override
  Future<void> deleteToken(String token) async {
    return _delete(from: 'tokens', id: token);
  }

  @override
  Future<bool> ping() async {
    return await underlying
        .get(Uri.parse('$server/service/health'))
        .andProcessResponseWith((response) => response.statusCode == 200);
  }

  Future<List<T>> _get<T>({required String from, required T Function(Map<String, Object?> json) fromJson}) async {
    return underlying.get(Uri.parse('$server/manage/$from')).andProcessResponseWith(
        (r) => (jsonDecode(r.body) as Iterable<dynamic>).map((json) => fromJson(json)).toList());
  }

  Future<void> _post({required Map<String, dynamic> data, required String to}) async {
    final _ = await underlying
        .post(
          Uri.parse('$server/manage/$to'),
          headers: {'Content-Type': 'application/json'},
          body: jsonEncode(data),
        )
        .andProcessResponse();
  }

  Future<void> _put({required Map<String, dynamic> data, required String to, required String id}) async {
    final _ = await underlying
        .put(
          Uri.parse('$server/manage/$to/$id'),
          headers: {'Content-Type': 'application/json'},
          body: jsonEncode(data),
        )
        .andProcessResponse();
  }

  Future<void> _putCredentials({required Map<String, dynamic> data, required String to, required String id}) async {
    final _ = await underlying
        .put(
          Uri.parse('$server/manage/$to/$id/credentials'),
          headers: {'Content-Type': 'application/json'},
          body: jsonEncode(data),
        )
        .andProcessResponse();
  }

  Future<void> _delete({required String from, required String id}) async {
    final _ = await underlying.delete(Uri.parse('$server/manage/$from/$id')).andProcessResponse();
  }
}

extension ExtendedResponse on Future<http.Response> {
  Future<T> andProcessResponseWith<T>(T Function(http.Response response) f) async {
    try {
      final response = await this;

      if (response.statusCode == 401) {
        return Future.error(AuthenticationFailure());
      } else if (response.statusCode == 403) {
        return Future.error(AuthorizationFailure());
      } else if (response.statusCode >= 400 && response.statusCode < 500) {
        return Future.error(BadRequest(message: response.body));
      } else if (response.statusCode >= 500) {
        return Future.error(InternalServerError(message: response.body));
      } else {
        return f(response);
      }
    } on oauth2.AuthorizationException catch (_) {
      return Future.error(AuthenticationFailure());
    } on oauth2.ExpirationException catch (_) {
      return Future.error(AuthenticationFailure());
    }
  }

  Future<void> andProcessResponse() async {
    return andProcessResponseWith((response) => response);
  }
}

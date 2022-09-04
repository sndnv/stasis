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

abstract class ApiClient {
  String? clientId;
  String? subject;
  String? audience;

  // APIs
  Future<List<Api>> getApis();

  Future<void> postApi(CreateApi request);

  Future<void> deleteApi(String id);

  // Clients
  Future<List<Client>> getClients();

  Future<void> postClient(CreateClient request);

  Future<void> putClient(String id, UpdateClient request);

  Future<void> putClientCredentials(String id, UpdateClientCredentials request);

  Future<void> deleteClient(String id);

  // Resource Owners
  Future<List<ResourceOwner>> getOwners();

  Future<void> postOwner(CreateOwner request);

  Future<void> putOwner(String id, UpdateOwner request);

  Future<void> putOwnerCredentials(String id, UpdateOwnerCredentials request);

  Future<void> deleteOwner(String id);

  // Authorization Codes
  Future<List<StoredAuthorizationCode>> getCodes();

  Future<void> deleteCode(String code);

  // Tokens
  Future<List<StoredRefreshToken>> getTokens();

  Future<void> deleteToken(String token);

  // System
  Future<bool> ping();
}

class AuthenticationFailure implements Exception {
  final String message = 'Failed to authenticate user';

  @override
  String toString() {
    return message;
  }
}

class AuthorizationFailure implements Exception {
  final String message = 'User not allowed to access resource';

  @override
  String toString() {
    return message;
  }
}

class BadRequest implements Exception {
  BadRequest({required this.message});

  final String message;

  @override
  String toString() {
    return 'Bad Request - $message';
  }
}

class InternalServerError implements Exception {
  InternalServerError({required this.message});

  final String message;

  @override
  String toString() {
    return 'Internal Server Error - $message';
  }
}

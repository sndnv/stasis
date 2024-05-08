import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:freezed_annotation/freezed_annotation.dart';
import 'package:http/http.dart' as http;
import 'package:mockito/annotations.dart';
import 'package:mockito/mockito.dart';
import 'package:stasis_client_ui/api/api_client.dart';

import 'api_client_test.mocks.dart';

part 'api_client_test.freezed.dart';

part 'api_client_test.g.dart';

@GenerateMocks([http.Client])
void main() {
  const server = 'http://localhost:1234';
  const applicationJson = {'Content-Type': 'application/json'};

  group('An ApiClient should', () {
    test('support handling GET requests (list of objects response)', () async {
      final underlying = MockClient();
      final client = TestApiClient(server, underlying);

      const response = '[{"a":"1","b":2},{"a":"3","b":4},{"a":"5","b":6}]';

      when(underlying.get(Uri.parse('$server/test/get'))).thenAnswer((_) async => http.Response(response, 200));

      expect(
          await client.getMultiple(), const [TestData(a: '1', b: 2), TestData(a: '3', b: 4), TestData(a: '5', b: 6)]);
    });

    test('support handling GET requests (single object response)', () async {
      final underlying = MockClient();
      final client = TestApiClient(server, underlying);

      const response = '{"a":"1","b":2}';

      when(underlying.get(Uri.parse('$server/test/get'))).thenAnswer((_) async => http.Response(response, 200));

      expect(await client.getSingle(), const TestData(a: '1', b: 2));
    });

    test('support handling GET requests (optional object response)', () async {
      final underlying = MockClient();
      final client = TestApiClient(server, underlying);

      const response = '{"a":"1","b":2}';

      when(underlying.get(Uri.parse('$server/test/get'))).thenAnswer((_) async => http.Response(response, 200));
      expect(await client.getOpt(), const TestData(a: '1', b: 2));

      when(underlying.get(Uri.parse('$server/test/get'))).thenAnswer((_) async => http.Response('', 404));
      expect(await client.getOpt(), null);
    });

    test('support handling GET SSE requests', () async {
      final underlying = MockClient();
      final client = TestApiClient(server, underlying);

      const data = [
        TestData(a: '1', b: 1),
        TestData(a: '2', b: 2),
        TestData(a: '3', b: 3),
      ];

      final responseStream = Stream.fromIterable(data)
          .map((e) => jsonEncode(e.toJson()))
          .map((json) => ['data: $json', '\n'].join('\n'))
          .transform(utf8.encoder);

      when(underlying.send(any)).thenAnswer((_) async => http.StreamedResponse(responseStream, 200));

      expect(await client.getSse(from: '/a/b/c', fromJson: TestData.fromJson).toList(), data);
    });

    test('support handling POST requests', () async {
      final underlying = MockClient();
      final client = TestApiClient(server, underlying);

      const request = TestData(a: '1', b: 2);
      const jsonRequest = '{"a":"1","b":2}';

      when(underlying.post(Uri.parse('$server/test/post'), headers: applicationJson, body: jsonRequest))
          .thenAnswer((_) async => http.Response('{"c":"3"}', 200));

      expect(await client.postData(request), const TestResponse(c: '3'));
    });

    test('support handling PUT requests', () async {
      final underlying = MockClient();
      final client = TestApiClient(server, underlying);

      const request = TestData(a: '1', b: 2);
      const jsonRequest = '{"a":"1","b":2}';

      when(underlying.put(Uri.parse('$server/test/put'), headers: applicationJson, body: jsonRequest))
          .thenAnswer((_) async => http.Response('', 200));

      expect(() async => await client.putData(request), returnsNormally);
    });

    test('support handling PUT requests without data', () async {
      final underlying = MockClient();
      final client = TestApiClient(server, underlying);

      when(underlying.put(Uri.parse('$server/test/put'))).thenAnswer((_) async => http.Response('', 200));

      expect(() async => await client.putWithoutData(), returnsNormally);
    });

    test('support handling PUT requests with responses', () async {
      final underlying = MockClient();
      final client = TestApiClient(server, underlying);

      const request = TestData(a: '1', b: 2);
      const jsonRequest = '{"a":"1","b":2}';

      when(underlying.put(Uri.parse('$server/test/put'), headers: applicationJson, body: jsonRequest))
          .thenAnswer((_) async => http.Response('{"c":"3"}', 200));

      expect(await client.putDataWithResponse(request), const TestResponse(c: '3'));
    });

    test('support handling PUT requests with responses and no data', () async {
      final underlying = MockClient();
      final client = TestApiClient(server, underlying);

      when(underlying.put(Uri.parse('$server/test/put'))).thenAnswer((_) async => http.Response('{"c":"3"}', 200));

      expect(await client.putWithResponseAndNoData(), const TestResponse(c: '3'));
    });

    test('support handling DELETE requests', () async {
      final underlying = MockClient();
      final client = TestApiClient(server, underlying);

      when(underlying.delete(Uri.parse('$server/test/delete'))).thenAnswer((_) async => http.Response('', 200));

      expect(() async => await client.deleteData(), returnsNormally);
    });

    test('handle valid responses', () async {
      final future100 = Future.value(http.Response('', 100));
      final future200 = Future.value(http.Response('', 200));
      final future300 = Future.value(http.Response('', 300));

      expect(await future100.andProcessResponseWith((response) => response.statusCode), 100);
      expect(await future200.andProcessResponseWith((response) => response.statusCode), 200);
      expect(await future300.andProcessResponseWith((response) => response.statusCode), 300);
    });

    test('handle 401 responses', () async {
      final future = Future.value(http.Response('', 401));
      expect(() async => await future.andProcessResponse(), throwsA(const TypeMatcher<AuthenticationFailure>()));
    });

    test('handle 403 responses', () async {
      final future = Future.value(http.Response('', 403));
      expect(() async => await future.andProcessResponse(), throwsA(const TypeMatcher<AuthorizationFailure>()));
    });

    test('handle 4xx responses', () async {
      final future = Future.value(http.Response('', 400));
      expect(() async => await future.andProcessResponse(), throwsA(const TypeMatcher<BadRequest>()));
    });

    test('handle 5xx responses', () async {
      final future = Future.value(http.Response('', 500));
      expect(() async => await future.andProcessResponse(), throwsA(const TypeMatcher<InternalServerError>()));
    });

    test('handle API failures', () async {
      final Future<http.Response> apiFailureFuture = Future.error(http.ClientException('error'));

      expect(() async => await apiFailureFuture.andProcessResponse(), throwsA(const TypeMatcher<ApiUnavailable>()));
    });

    test('support handling requests with API tokens', () async {
      final underlying = MockClient();
      final client = TestApiClient(server, underlying, token: 'abc');

      const response = '[{"a":"1","b":2},{"a":"3","b":4},{"a":"5","b":6}]';

      when(underlying.get(Uri.parse('$server/test/get'), headers: {'Authorization': 'Bearer abc'}))
          .thenAnswer((_) async => http.Response(response, 200));

      expect(
          await client.getMultiple(), const [TestData(a: '1', b: 2), TestData(a: '3', b: 4), TestData(a: '5', b: 6)]);
    });
  });
}

class TestApiClient extends ApiClient {
  TestApiClient(String server, http.Client client, {super.token})
      : super(server: server, underlying: client);

  Future<List<TestData>> getMultiple() async {
    return await get(from: '/test/get', fromJson: TestData.fromJson);
  }

  Future<TestData> getSingle() async {
    return await getOne(from: '/test/get', fromJson: TestData.fromJson);
  }

  Future<TestData?> getOpt() async {
    return await getOptional(from: '/test/get', fromJson: TestData.fromJson);
  }

  Future<TestResponse> postData(TestData data) async {
    return await post(data: data.toJson(), to: '/test/post', fromJsonResponse: TestResponse.fromJson);
  }

  Future<void> putData(TestData data) async {
    return await put(data: data.toJson(), to: '/test/put');
  }

  Future<void> putWithoutData() async {
    return await put(data: {}, to: '/test/put');
  }

  Future<TestResponse> putDataWithResponse(TestData data) async {
    return await putWithResponse(data: data.toJson(), to: '/test/put', fromJsonResponse: TestResponse.fromJson);
  }

  Future<TestResponse> putWithResponseAndNoData() async {
    return await putWithResponse(data: {}, to: '/test/put', fromJsonResponse: TestResponse.fromJson);
  }

  Future<void> deleteData() async {
    return await delete(from: '/test/delete');
  }
}

@freezed
class TestData with _$TestData {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory TestData({
    required String a,
    required int b,
  }) = _TestData;

  factory TestData.fromJson(Map<String, Object?> json) => _$TestDataFromJson(json);
}

@freezed
class TestResponse with _$TestResponse {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory TestResponse({
    required String c,
  }) = _TestResponse;

  factory TestResponse.fromJson(Map<String, Object?> json) => _$TestResponseFromJson(json);
}

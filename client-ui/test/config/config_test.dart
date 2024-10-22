import 'package:stasis_client_ui/config/config.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('A ConfigFactory should', () {
    test('provide empty config', () async {
      final config = ConfigFactory.empty();

      const expected = '{}';

      expect(config.toString(), expected);
    });

    test('load config from valid JSON files', () async {
      const configFile = './test/resources/valid.json';

      final config = ConfigFactory.load(path: configFile);

      const expected = '{a: {b: {c: {d: value-1, e: true}}, f: {g: null}, h: 1},'
          ' i: 3 seconds,'
          ' j: {k: other test, l: 128M, m: 4.2},'
          ' n: {},'
          ' flutter-test: true}';

      expect(config.toString(), expected);
    });

    test('load config from valid HOCON files', () async {
      const configFile = './test/resources/valid.conf';

      final config = ConfigFactory.load(path: configFile);

      const expected = '{a: {b: {c: {d: value-1, e: true}}, f: {g: null}, h: 1},'
          ' i: 3 seconds,'
          ' j: {k: other test, l: 128M, m: 4.2},'
          ' n: {},'
          ' flutter-test: true}';

      expect(config.toString(), expected);
    });

    test('fail to load config from invalid JSON files', () async {
      const configFile = './test/resources/invalid.json';

      expect(() => ConfigFactory.load(path: configFile), throwsA((e) => e is InvalidConfigFileException));
    });

    test('fail to load config from invalid HOCON files', () async {
      const configFile = './test/resources/invalid.conf';

      expect(() => ConfigFactory.load(path: configFile), throwsA((e) => e is InvalidConfigFileException));
    });

    test('fail to load config from missing JSON files', () async {
      const configFile = './test/resources/missing.json';

      expect(() => ConfigFactory.load(path: configFile), throwsA((e) => e is ConfigFileNotAvailableException));
    });

    test('fail to load config from missing HOCON files', () async {
      const configFile = './test/resources/missing.conf';

      expect(() => ConfigFactory.load(path: configFile), throwsA((e) => e is ConfigFileNotAvailableException));
    });
  });

  group('A Config should', () {
    test('support checking if paths exist', () async {
      const configFile = './test/resources/valid.conf';

      final config = ConfigFactory.load(path: configFile);

      expect(config.hasPath('a'), true);
      expect(config.hasPath('a.b'), true);
      expect(config.hasPath('a.b.c'), true);
      expect(config.hasPath('a.b.c.d'), true);
      expect(config.hasPath('a.b.c.e'), true);
      expect(config.hasPath('a.f'), true);
      expect(config.hasPath('a.f.g'), false); // value is null
      expect(config.hasPath('a.h'), true);
      expect(config.hasPath('i'), true);
      expect(config.hasPath('j'), true);
      expect(config.hasPath('j.k'), true);
      expect(config.hasPath('j.l'), true);
      expect(config.hasPath('j.m'), true);
      expect(config.hasPath('n'), true);
      expect(config.hasPath('flutter-test'), true);
      expect(config.hasPath('other'), false);
    });

    test('provide config values', () async {
      const configFile = './test/resources/valid.conf';

      final config = ConfigFactory.load(path: configFile);

      expect(config.getString('a.b.c.d'), 'value-1');
      expect(config.getBoolean('a.b.c.e'), true);
      expect(config.getInt('a.h'), 1);
      expect(config.getString('i'), '3 seconds'); // durations are not supported
      expect(config.getString('j.k'), 'other test');
      expect(config.getString('j.l'), '128M'); // bytes and memory size are not supported
      expect(config.getDouble('j.m'), 4.2);
      expect(config.getBoolean('flutter-test'), true); // default is `false` in config but env var should be `true`

      expect(() => config.getString('other'), throwsA((e) => e is ConfigMissingException));
      expect(() => config.getString('j.m'), throwsA((e) => e is WrongTypeException));

      expect(config.getString('a.b.x', withDefault: 'default-value-1'), 'default-value-1');
      expect(config.getBoolean('a.b.y', withDefault: false), false);
      expect(config.getInt('a.b.z', withDefault: 9000), 9000);
      expect(config.getDouble('other', withDefault: 9000.1), 9000.1);
    });

    test('provide nested config', () async {
      const configFile = './test/resources/valid.conf';

      final config = ConfigFactory.load(path: configFile);

      expect(config.isEmpty(), false);

      final nested = config.getConfig('a.b.c');

      expect(nested.isEmpty(), false);
      expect(nested.toString(), '{d: value-1, e: true}');

      expect(config.getConfig('n').isEmpty(), true);

      expect(
        () => config.getConfig('a.f.g'),
        throwsA((e) => e is ConfigMissingException), // `a.f.g` is a value, not a config
      );
    });

    test('support providing the underlying config', () async {
      const configFile = './test/resources/valid.conf';

      final config = ConfigFactory.load(path: configFile);

      expect(
        config.getConfig('a.b').raw,
        {
          'c': {'d': 'value-1', 'e': true}
        },
      );
    });

    test('support sanitizing secret and missing config', () async {
      const configFile = './test/resources/secret.conf';

      final config = ConfigFactory.load(path: configFile).sanitized();

      expect(config.getString('a.b.c'), 'test-value');
      expect(config.getString('a.b.d'), '<missing>');
      expect(config.getString('a.b.password'), '<removed>');
      expect(config.getString('a.b.secret'), '<removed>');

      expect(
        config.raw,
        {
          'a': {
            'b': {'c': 'test-value', 'd': '<missing>', 'password': '<removed>', 'secret': '<removed>'}
          }
        },
      );
    });
  });
}

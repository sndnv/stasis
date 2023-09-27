import 'package:stasis_client_ui/model/operations/operation.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('Operation States should', () {
    test('support creation from strings', () async {
      expect(State.fromName('active'), State.active);
      expect(State.fromName('completed'), State.completed);
      expect(State.fromName('all'), State.all);

      expect(() => State.fromName('other'), throwsArgumentError);
    });
  });

  group('Operation Types should', () {
    test('support conversion to strings', () async {
      expect(Type.toName(Type.backup), 'client-backup');
      expect(Type.toName(Type.recovery), 'client-recovery');
      expect(Type.toName(Type.expiration), 'client-expiration');
      expect(Type.toName(Type.validation), 'client-validation');
      expect(Type.toName(Type.keyRotation), 'client-key-rotation');
      expect(Type.toName(Type.garbageCollection), 'server-garbage-collection');
    });

    test('support creation from strings', () async {
      expect(Type.fromName('client-backup'), Type.backup);
      expect(Type.fromName('client-recovery'), Type.recovery);
      expect(Type.fromName('client-expiration'), Type.expiration);
      expect(Type.fromName('client-validation'), Type.validation);
      expect(Type.fromName('client-key-rotation'), Type.keyRotation);
      expect(Type.fromName('server-garbage-collection'), Type.garbageCollection);

      expect(() => Type.fromName('other'), throwsArgumentError);
    });
  });
}

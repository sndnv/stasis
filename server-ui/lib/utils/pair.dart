class Pair<A, B> {
  Pair(this.a, this.b);

  final A a;
  final B b;

  @override
  String toString() => '($a,$b)';

  @override
  bool operator ==(Object other) {
    return other is Pair && a == other.a && b == other.b;
  }

  @override
  int get hashCode => Object.hash(a, b);
}

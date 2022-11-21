class Triple<A, B, C> {
  Triple(this.a, this.b, this.c);

  final A a;
  final B b;
  final C c;

  @override
  String toString() => '($a,$b,$c)';

  @override
  bool operator ==(Object other) {
    return other is Triple && a == other.a && b == other.b && c == other.c;
  }

  @override
  int get hashCode => Object.hash(a, b, c);
}

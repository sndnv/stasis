package stasis.layers.service.bootstrap.mocks

final case class TestClass(a: String, b: Int)

object TestClass {
  val Default: TestClass = TestClass(a = "a", b = 0)
}

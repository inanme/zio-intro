import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class TestFirst extends AnyFlatSpec with Matchers {
  "fd" should "test" in {
    1 shouldBe 1
  }
}

package lgbt.princess.lifevest

import akka.stream.scaladsl.Source

class StreamableUsageTest extends BaseSpec {
  behavior of "Streamable"

  it should "allow mixing collections, Options and Sources freely through manual wrapping" in {
    val res = for {
      i    <- Streamable.elems(1, 2, 3, 4, 5)
      jStr <- Streamable(List("1", "2"))
      j = jStr.toInt
      k <- Streamable(Some(1))
      x <- Streamable(Source(List(1, 2, 3)))
      if i + x > 6
      y <- Vector(6, 7)
    } yield i + j + k + x + y

    get(res) shouldEqual Seq(15, 16, 16, 17, 15, 16, 16, 17, 16, 17, 17, 18)
  }
}

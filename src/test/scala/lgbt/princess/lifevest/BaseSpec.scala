package lgbt.princess.lifevest

import akka.actor.ActorSystem
import akka.stream.scaladsl.Sink
import org.scalatest.{BeforeAndAfterAll, LoneElement, OptionValues}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Await
import scala.concurrent.duration._

abstract class BaseSpec extends AnyFlatSpec with Matchers with OptionValues with LoneElement with BeforeAndAfterAll {
  private[this] var sys: ActorSystem = _
  implicit protected def system: ActorSystem   = sys

  override def beforeAll(): Unit = {
    sys = ActorSystem()
  }

  override def afterAll(): Unit = {
    Await.ready(sys.terminate(), Duration.Inf)
  }

  protected def get[A](streamable: Streamable[A]): Seq[A] =
    Await.result(streamable.toSource.runWith(Sink.seq[A]), 5.seconds)
}

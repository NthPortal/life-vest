package lgbt.princess.lifevest

import akka.stream.scaladsl.Source
import lgbt.princess.lifevest.{Streamable => S}
import lgbt.princess.lifevest.Streamable.MutableCollectionSupport.Implicits.support

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

class StreamableTest extends BaseSpec {
  import StreamableTest.Instance

  private def emptyMixed[A]: S.Mixed[A]            = S.empty[A].flatMap(_ => Source.empty)
  private def mixedElems[A](elems: A*): S.Mixed[A] = S(elems).flatMap(Source.single)

  behavior of "Streamable"

  it should "be constructed from an Option" in {
    get(S(Some(1))) shouldEqual Seq(1)
    get(S(None)) shouldBe empty
  }

  it should "be constructed from an immutable Iterable" in {
    get(S(Nil)) shouldBe empty
    get(S(Vector(1))) shouldEqual Seq(1)
    get(S(Vector(1, 2))) shouldEqual Seq(1, 2)
  }

  it should "be constructed from a Source" in {
    get(S(Source(Vector(1, 2, 3, 4)))) shouldEqual Seq(1, 2, 3, 4)
  }

  it should "be constructed from varargs" in {
    get(S.elems(1, 2, 3)) shouldEqual Seq(1, 2, 3)
  }

  it should "be constructed from a single element" in {
    get(S.single(1)) shouldEqual Seq(1)
  }

  it should "return an empty Materialized" in {
    get(S.empty) shouldBe empty
  }

  it should "be constructed from a mutable collection" in {
    get(S(ListBuffer.empty)) shouldBe empty
    get(S(ListBuffer(1))) shouldEqual Seq(1)
    get(S(ListBuffer(1, 2, 3))) shouldEqual Seq(1, 2, 3)
  }

  it should "be constructed from an IterableOnce" in {
    get(S(Iterator.empty)) shouldBe empty
    get(S(Iterator.single(1))) shouldEqual Seq(1)
    get(S(Iterator(1, 2, 3))) shouldEqual Seq(1, 2, 3)
  }

  it should "not drop duplicate elements if initially backed by a Set" in {
    get(S(Set(1, 2, 3)).map(_ => 1)) shouldEqual Seq(1, 1, 1)
  }

  private val instanceNameCount: mutable.Map[String, Int] = mutable.Map.empty
  private def uniqueName(instance: Instance[_]): String = {
    val baseName = instance.name
    val idx      = instanceNameCount.updateWith(baseName)(opt => Some(opt.fold(0)(_ + 1))).get
    s"$baseName [$idx]"
  }

  check(Instance(Nil, S.empty))
  check(Instance(List(1), S.single(1)))
  check(Instance(Nil, S(None)))
  check(Instance(List(1), S(Some(1))))
  check(Instance(Nil, S.elems()))
  check(Instance(List(1), S.elems(1)))
  check(Instance(List(1, 2, 3), S.elems(1, 2, 3)))
  check(Instance(Nil, S(Nil)))
  check(Instance(List(1), S(List(1))))
  check(Instance(List(1, 2, 3), S(List(1, 2, 3))))
  check(Instance(Nil, S(ListBuffer.empty)))
  check(Instance(List(1), S(ListBuffer(1))))
  check(Instance(List(1, 2, 3), S(ListBuffer(1, 2, 3))))
  check(Instance(Nil, S(Iterator.empty)))
  check(Instance(List(1), S(Iterator.single(1))))
  check(Instance(List(1, 2, 3), S(Iterator(1, 2, 3))))
  check(Instance(List(2), S(Vector(1, 2)).withFilter(_ % 2 == 0)))

  check(Instance(Nil, S(Source.empty)))
  check(Instance(List(1), S(Source.single(1))))
  check(Instance(List(1, 2, 3), S(Source(List(1, 2, 3)))))

  check(Instance(Nil, emptyMixed))
  check(Instance(List(1), mixedElems(1)))
  check(Instance(List(1, 2), mixedElems(1, 2)))
  check(Instance(List(2), mixedElems(1, 2).withFilter(_ % 2 == 0)))

  private def check(instance: Instance[Int]): Unit = {
    check0(instance)
    check0(instance.withFilter)
  }

  private def check0(instance: Instance[Int]): Unit = {
    uniqueName(instance) should behave like correctStreamable(instance)
  }

  private def correctStreamable(instance: Instance[Int]): Unit = {
    it should "fold into a collection" in {
      get(instance.streamable.foldInto(Set)).loneElement shouldEqual instance.values.to(Set)
      get(instance.streamable.foldInto(Seq)).loneElement shouldEqual instance.values.to(Seq)
    }

    it should "fold to an Option" in {
      if (instance.values.sizeIs > 1) {
        an[UnsupportedOperationException] should be thrownBy get(instance.streamable.foldToOption)
      } else {
        get(instance.streamable.foldToOption).loneElement shouldEqual instance.values.headOption
      }
    }

    it should "map" in {
      get(instance.streamable.map(_ + 1)) shouldEqual instance.values.map(_ + 1)
    }

    it should "filter" in {
      get(instance.streamable.filter(_ => true)) shouldEqual instance.values.filter(_ => true)
      get(instance.streamable.filter(_ => false)) shouldEqual instance.values.filter(_ => false)
      get(instance.streamable.filter(_ % 2 == 0)) shouldEqual instance.values.filter(_ % 2 == 0)
    }

    it should "transform withFilter" in {
      get(instance.streamable.withFilter(_ => true)) shouldEqual instance.values.filter(_ => true)
      get(instance.streamable.withFilter(_ => false)) shouldEqual instance.values.filter(_ => false)
      get(instance.streamable.withFilter(_ % 2 == 0)) shouldEqual instance.values.filter(_ % 2 == 0)
    }

    it should "flatMap with a Materialized" in {
      get(instance.streamable.flatMap(_ => S.empty)) shouldBe empty
      get(instance.streamable.flatMap(elem => S.single(elem + 1))) shouldEqual instance.values.map(_ + 1)
      get(instance.streamable.flatMap(elem => S.elems(elem, 2))) shouldEqual instance.values.flatMap(List(_, 2))
      get(
        instance.streamable.flatMap(elem => S.elems(elem, 2).withFilter(_ => true))
      ) shouldEqual instance.values.flatMap(List(_, 2))
      get(instance.streamable.flatMap(elem => S(ListBuffer(elem, 2)))) shouldEqual instance.values.flatMap(List(_, 2))
    }

    it should "flatMap with a Streamed" in {
      get(instance.streamable.flatMap(_ => S(Source.empty))) shouldBe empty
      get(
        instance.streamable.flatMap(elem => S(Source(Vector(elem, 2)))),
      ) shouldEqual instance.values.flatMap(List(_, 2))
    }

    it should "flatMap with a Streamed (using FlatMapBehavior.Merge)" in {
      import StreamableTest.mergeBehavior

      get(
        instance.streamable.flatMap(elem => S(Source(Vector(elem, 2)))),
      ) should contain theSameElementsAs instance.values.flatMap(List(_, 2))
    }

    it should "flatMap with a Mixed" in {
      get(instance.streamable.flatMap(_ => emptyMixed)) shouldBe empty
      get(instance.streamable.flatMap(elem => mixedElems(elem + 1))) shouldEqual instance.values.map(_ + 1)
      get(instance.streamable.flatMap(elem => mixedElems(elem, 2))) shouldEqual instance.values.flatMap(List(_, 2))
      get(
        instance.streamable.flatMap(elem => mixedElems(elem, 2).withFilter(_ => true))
      ) shouldEqual instance.values.flatMap(List(_, 2))
    }

    it should "flatMap with a Mixed (using FlatMapBehavior.Merge)" in {
      import StreamableTest.mergeBehavior

      get(
        instance.streamable.flatMap(elem => mixedElems(elem, 2)),
      ) should contain theSameElementsAs instance.values.flatMap(List(_, 2))
    }

    it should "flatMap with an Option" in {
      get(instance.streamable.flatMap(_ => None)) shouldBe empty
      get(instance.streamable.flatMap(elem => Some(elem + 1))) shouldEqual instance.values.map(_ + 1)
    }

    it should "flatMap with an immutable collection" in {
      get(instance.streamable.flatMap(_ => Nil)) shouldBe empty
      get(instance.streamable.flatMap(elem => Vector(elem, 2))) shouldEqual instance.values.flatMap(List(_, 2))
    }

    it should "flatMap with a Source" in {
      get(instance.streamable.flatMap(_ => Source.empty)) shouldBe empty
      get(instance.streamable.flatMap(elem => Source(Vector(elem, 2)))) shouldEqual instance.values.flatMap(List(_, 2))
    }

    it should "flatMap with a Source (using FlatMapBehavior.Merge)" in {
      import StreamableTest.mergeBehavior

      get(
        instance.streamable.flatMap(elem => Source(Vector(elem, 2))),
      ) should contain theSameElementsAs instance.values.flatMap(List(_, 2))
    }

    it should "flatMap with a mutable collection" in {
      get(instance.streamable.flatMap(_ => ListBuffer.empty)) shouldBe empty
      get(instance.streamable.flatMap(elem => ListBuffer(elem, 2))) shouldEqual instance.values.flatMap(List(_, 2))
    }

    it should "flatMap with an IterableOnce" in {
      get(instance.streamable.flatMap(_ => Iterator.empty)) shouldBe empty
      get(instance.streamable.flatMap(elem => Iterator(elem, 2))) shouldEqual instance.values.flatMap(List(_, 2))
    }
  }
}

object StreamableTest {
  implicit def mergeBehavior: S.FlatMapBehavior = S.FlatMapBehavior.Merge(4)

  final case class Instance[A](values: List[A], streamable: Streamable[A]) {
    def name: String =
      streamable.getClass.getName
        .stripPrefix(streamable.getClass.getPackage.getName)
        .stripPrefix(".")
        .stripSuffix("$")
        .replace('$', '.')

    def withFilter: Instance[A] = Instance(values, streamable.withFilter(_ => true))
  }
}

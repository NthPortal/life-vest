package lgbt.princess.lifevest

import akka.stream.scaladsl.Source

import scala.annotation.{implicitNotFound, unused}
import scala.collection.compat._
import scala.collection.{SeqFactory, View, immutable => i, mutable => m}
import scala.{collection => c}

/** Something that can be streamed as a [[akka.stream.scaladsl.Source Source]]. */
sealed trait Streamable[+A] {
  import Streamable._
  import Overload._

  /** This type of `Streamable`, and the result of non-`flatMap` transformations. */
  type Repr[+X] <: Streamable[X]

  /**
   * The result of `flatMap`ing using a function that returns a
   * [[Streamable.Materialized Materialized]] type.
   */
  type FMMaterialized[+X] <: Streamable[X]

  /**
   * The result of `flatMap`ing using a function that returns a
   * [[Streamable.Streamed Streamed]] or [[Streamable.Mixed Mixed]] type.
   */
  type FMStreamed[+X] <: Streamable[X]

  /** @return this Streamable streamed as a [[akka.stream.scaladsl.Source Source]] */
  def toSource: Source[A, _]

  /**
   * Transforms this Streamable into a Streamable of a single element that is
   * a collection containing this stream's elements.
   *
   * @param factory the factory with which to create a collection containing
   *                all of this stream's elements
   */
  def foldInto[C](factory: Factory[A, C]): Repr[C]

  /**
   * Transforms this Streamable of one or zero elements into a Streamable of
   * a single element that is an [[scala.Option Option]] containing this
   * stream's element, if this stream is not empty.
   *
   * @throws scala.UnsupportedOperationException if this stream contains more
   *                                             than one element
   */
  @throws[UnsupportedOperationException]
  def foldToOption: Repr[Option[A]]

  /**
   * Transforms this Streamable by applying a function to each element of the stream.
   *
   * @param f the function to apply to each element
   * @tparam B the element type of the returned Streamable
   */
  def map[B](f: A => B): Repr[B]

  /**
   * Transforms this Streamable by only retaining elements satisfying a predicate.
   *
   * @param p the predicate with which to test elements
   */
  def filter(p: A => Boolean): Repr[A]

  /**
   * Transforms this Streamable by only retaining elements satisfying a predicate.
   *
   * If this Streamable is already materialized, this operation does not build a
   * new collection of filtered elements, but instead uses a filtered view. This
   * method is mainly used by `for` comprehensions.
   *
   * @param p the predicate with which to test elements
   */
  def withFilter(p: A => Boolean): Repr[A] = filter(p)

  /**
   * Transforms this Streamable by applying a function to each element of
   * the stream, and flattening the resulting collections into the stream.
   *
   * @param f the function to apply to each element
   * @tparam B the element type of the returned Streamable
   */
  def flatMap[B](f: A => i.Iterable[B]): FMMaterialized[B]

  /**
   * Transforms this Streamable by applying a function to each element of
   * the stream, and flattening the resulting
   * [[akka.stream.scaladsl.Source Sources]] into the stream.
   *
   * @see [[Streamable.FlatMapBehavior]] for how to customize flattening
   *
   * @param f the function to apply to each element
   * @tparam B the element type of the returned Streamable
   */
  def flatMap[B](f: A => Source[B, _])(implicit fmb: FlatMapBehavior): FMStreamed[B]

  /**
   * Transforms this Streamable by applying a function to each element of
   * the stream, and flattening the resulting
   * [[Streamable.Mixed Mixed Streamables]] into the stream.
   *
   * @see [[Streamable.FlatMapBehavior]] for how to customize flattening
   *
   * @param f the function to apply to each element
   * @tparam B the element type of the returned Streamable
   */
  def flatMap[B](f: A => Mixed[B])(implicit fmb: FlatMapBehavior, @unused d: Diff1): FMStreamed[B]

  /**
   * Transforms this Streamable by applying a function to each element of
   * the stream, and flattening the resulting
   * [[Streamable.Materialized Materialized Streamables]] into the stream.
   *
   * @param f the function to apply to each element
   * @tparam B the element type of the returned Streamable
   */
  def flatMap[B](f: A => Materialized[B])(implicit @unused d: Diff1): FMMaterialized[B] =
    flatMap(f(_: A).immutableElems)

  /**
   * Transforms this Streamable by applying a function to each element of
   * the stream, and flattening the resulting [[scala.Option Options]] into the stream.
   *
   * @param f the function to apply to each element
   * @tparam B the element type of the returned Streamable
   */
  def flatMap[B](f: A => Option[B])(implicit @unused d: Diff2): FMMaterialized[B] =
    flatMap(f(_: A).toList)

  /**
   * Transforms this Streamable by applying a function to each element of
   * the stream, and flattening the resulting
   * [[Streamable.Streamed Streamed Streamables]] into the stream.
   *
   * @see [[Streamable.FlatMapBehavior]] for how to customize flattening
   *
   * @param f the function to apply to each element
   * @tparam B the element type of the returned Streamable
   */
  def flatMap[B](f: A => Streamed[B])(implicit fmb: FlatMapBehavior, @unused d: Diff2): FMStreamed[B] =
    flatMap(f(_: A).toSource)

  /**
   * Transforms this Streamable by applying a function to each element of
   * the stream, and flattening the resulting collections into the stream.
   *
   * @note This method requires [[Streamable.MutableCollectionSupport]] in
   *       the implicit scope.
   *
   * @param f the function to apply to each element
   * @tparam B the element type of the returned Streamable
   */
  def flatMap[B](f: A => IterableOnce[B])(implicit @unused mcs: MutableCollectionSupport): FMMaterialized[B] =
    flatMap {
      f(_: A) match {
        case elems: i.Iterable[B] => elems
        case elems                => i.Seq from elems
      }
    }
}

object Streamable {
  import Overload._

  /** @return a Streamable containing the element in an [[scala.Option Option]], if any */
  def apply[A](maybeElem: Option[A]): Materialized[A] = Materialized(maybeElem)

  /** @return a Streamable containing the elements of an immutable collection */
  def apply[A](elems: i.Iterable[A]): Materialized[A] = Materialized(elems)

  /** @return a Streamable of the elements of a [[akka.stream.scaladsl.Source Source]] */
  def apply[A](elems: Source[A, _]): Streamed[A] = Streamed.Elems(elems)

  /** @return a Streamable of the given elements */
  def elems[A](elems: A*): Materialized[A] = Materialized(elems)

  /** @return a Streamable with no elements */
  def empty[A]: Materialized[A] = Materialized.Empty

  /** @return a Streamable containing a single element */
  def single[A](elem: A): Materialized[A] = Materialized.Single(elem)

  /** @return a Streamable containing the elements of a possibly mutable collection */
  def apply[A](elems: IterableOnce[A])(implicit @unused mcs: MutableCollectionSupport): Materialized[A] =
    Materialized.mutable(elems)

  @throws[UnsupportedOperationException]
  @inline private def foldMultipleToOption(): Nothing =
    throw new UnsupportedOperationException("fold multiple elements to Option")

  /** A [[Streamable]] with elements that are already materialized. */
  sealed trait Materialized[+A] extends Streamable[A] {
    type Repr[+X]           = Materialized[X]
    type FMMaterialized[+X] = Materialized[X]
    type FMStreamed[+X]     = Mixed[X]

    private[Streamable] def immutableElems: i.Iterable[A]
    private[Streamable] def elems: c.Iterable[A] = immutableElems
  }

  private object Materialized {
    case object Empty extends Materialized[Nothing] {
      private[Streamable] def immutableElems: i.Iterable[Nothing] = Nil

      def toSource: Source[Nothing, _] = Source.empty

      def foldInto[C](factory: Factory[Nothing, C]): Materialized[C] = Single(factory.fromSpecific(Nil))
      val foldToOption: Materialized[Option[Nothing]]                = Single(None)

      def map[B](f: Nothing => B): Materialized[B]             = this
      def filter(p: Nothing => Boolean): Materialized[Nothing] = this

      def flatMap[B](f: Nothing => i.Iterable[B]): Materialized[B]                               = this
      def flatMap[B](f: Nothing => Source[B, _])(implicit fmb: FlatMapBehavior): Mixed[B]        = Mixed.Empty
      def flatMap[B](f: Nothing => Mixed[B])(implicit fmb: FlatMapBehavior, d: Diff1): Mixed[B]  = Mixed.Empty
      override def flatMap[B](f: Nothing => Materialized[B])(implicit d: Diff1): Materialized[B] = this
      override def flatMap[B](f: Nothing => Option[B])(implicit d: Diff2): Materialized[B]       = this
      override def flatMap[B](f: Nothing => Streamed[B])(implicit fmb: FlatMapBehavior, d: Diff2): Mixed[B] =
        Mixed.Empty
      override def flatMap[B](f: Nothing => IterableOnce[B])(implicit mcs: MutableCollectionSupport): Materialized[B] =
        this
    }

    final case class Single[+A](elem: A) extends Materialized[A] {
      private[Streamable] def immutableElems: i.Iterable[A] = List(elem)

      def toSource: Source[A, _] = Source.single(elem)

      def foldInto[C](factory: Factory[A, C]): Materialized[C] = Single((factory.newBuilder += elem).result())
      def foldToOption: Materialized[Option[A]]                = Single(Some(elem))

      def map[B](f: A => B): Materialized[B]       = Single(f(elem))
      def filter(p: A => Boolean): Materialized[A] = if (p(elem)) this else Empty

      def flatMap[B](f: A => i.Iterable[B]): Materialized[B] = Materialized(f(elem))
      def flatMap[B](f: A => Source[B, _])(implicit fmb: FlatMapBehavior): Mixed[B] =
        Mixed.Single(Streamed.Elems(f(elem)))
      def flatMap[B](f: A => Mixed[B])(implicit fmb: FlatMapBehavior, d: Diff1): Mixed[B]  = f(elem)
      override def flatMap[B](f: A => Materialized[B])(implicit d: Diff1): Materialized[B] = f(elem)
      override def flatMap[B](f: A => Option[B])(implicit d: Diff2): Materialized[B] =
        Materialized(f(elem))
      override def flatMap[B](f: A => Streamed[B])(implicit fmb: FlatMapBehavior, d: Diff2): Mixed[B] =
        Mixed.Single(f(elem))
      override def flatMap[B](f: A => IterableOnce[B])(implicit mcs: MutableCollectionSupport): Materialized[B] =
        Materialized.mutable(f(elem))
    }

    abstract class ElemsShared[+A] extends Materialized[A] {
      protected def seqFactory: SeqFactory[i.Seq]

      @inline private[this] def transformCollInternal[B](op: c.Iterable[A] => IterableOnce[B]): i.Seq[B] = {
        seqFactory from {
          elems match {
            case elems: i.Seq[A] => op(elems)      // `Seq.from` is probably a no-op
            case elems           => op(elems.view) // don't accidentally drop duplicate elements if elems is a Set
          }
        }
      }
      @inline private[this] def transform[B](op: c.Iterable[A] => IterableOnce[B]): Materialized[B] =
        Materialized(transformCollInternal(op))
      @inline private[this] def transformMixed[B](op: c.Iterable[A] => IterableOnce[Streamed[B]]): Mixed[B] =
        Mixed(transformCollInternal(op))

      final def toSource: Source[A, _] = Source(immutableElems)

      def foldInto[C](factory: Factory[A, C]): Materialized[C] = Single(factory fromSpecific elems)
      @throws[UnsupportedOperationException]
      def foldToOption: Materialized[Option[A]] =
        if (elems.sizeIs > 1) foldMultipleToOption()
        else Single(elems.headOption)

      def map[B](f: A => B): Materialized[B]                    = transform(_ map f)
      def filter(q: A => Boolean): Materialized[A]              = transform(_ filter q)
      override def withFilter(p: A => Boolean): Materialized[A] = new WithFilter(elems, p)(seqFactory)

      def flatMap[B](f: A => i.Iterable[B]): Materialized[B] =
        transform(_ flatMap f)
      def flatMap[B](f: A => Source[B, _])(implicit fmb: FlatMapBehavior): Mixed[B] =
        transformMixed(_.map(elem => Streamed.Elems(f(elem))))
      def flatMap[B](f: A => Mixed[B])(implicit fmb: FlatMapBehavior, d: Diff1): Mixed[B] =
        transformMixed(_.flatMap(f(_).elems))
      override def flatMap[B](f: A => Materialized[B])(implicit d: Diff1): Materialized[B] =
        transform(_.flatMap(f(_).elems))
      override def flatMap[B](f: A => Option[B])(implicit d: Diff2): Materialized[B] =
        transform(_ flatMap f)
      override def flatMap[B](f: A => Streamed[B])(implicit fmb: FlatMapBehavior, d: Diff2): Mixed[B] =
        transformMixed(_ map f)
      override def flatMap[B](f: A => IterableOnce[B])(implicit mcs: MutableCollectionSupport): Materialized[B] =
        transform(_ flatMap f)
    }

    final case class Elems[+A](override private[Streamable] val elems: i.Iterable[A]) extends ElemsShared[A] {
      private[Streamable] def immutableElems: i.Iterable[A] = elems
      protected def seqFactory: SeqFactory[i.Seq] =
        elems match {
          case elems: i.Seq[A] => elems.iterableFactory
          case _               => i.Seq
        }
    }

    final case class Mutable[+A](override private[Streamable] val elems: c.Iterable[A]) extends ElemsShared[A] {
      private[Streamable] def immutableElems: i.Iterable[A] = elems.toSeq
      protected def seqFactory: SeqFactory[i.Seq]           = i.Seq
    }

    final class WithFilter[+A] private[this] (override private[Streamable] val elems: View[A])(
        protected val seqFactory: SeqFactory[i.Seq],
    ) extends ElemsShared[A] {
      def this(coll: collection.Iterable[A], p: A => Boolean)(fact: SeqFactory[i.Seq]) =
        this(coll.view filter p)(fact)

      private[Streamable] def immutableElems: i.Iterable[A] = seqFactory from elems

      // override so we don't evaluate filter multiple times
      override def foldToOption: Materialized[Option[A]] = {
        val short = elems.take(2).toList
        if (short.sizeIs > 1) foldMultipleToOption()
        else Single(short.headOption)
      }
    }

    def apply[A](maybeElem: Option[A]): Materialized[A] =
      maybeElem.fold(Materialized.Empty: Materialized[A])(Materialized.Single(_))

    def apply[A](elems: i.Iterable[A]): Materialized[A] =
      elems.knownSize match {
        case 0 => Empty
        case 1 => Single(elems.head)
        case _ => Elems(elems)
      }

    def mutable[A](elems: IterableOnce[A]): Materialized[A] = {
      elems.knownSize match {
        case 0 => Empty
        case 1 => Single(elems.iterator.next())
        case _ =>
          elems match {
            case elems: i.Iterable[A] => Elems(elems)
            case elems: c.Iterable[A] => Mutable(elems)
            case elems                => apply(i.Seq from elems)
          }
      }
    }
  }

  /** A [[Streamable]] with elements backed by a [[akka.stream.scaladsl.Source Source]]. */
  sealed trait Streamed[+A] extends Streamable[A] {
    type Repr[+X]           = Streamed[X]
    type FMMaterialized[+X] = Streamed[X]
    type FMStreamed[+X]     = Streamed[X]
  }

  private object Streamed {
    final case class Elems[+A](toSource: Source[A, _]) extends Streamed[A] {
      def foldInto[C](factory: Factory[A, C]): Streamed[C] = {
        val res = toSource
          .fold(Option.empty[m.Builder[A, C]]) { (bOpt, elem) =>
            bOpt match {
              case Some(b) => b += elem; bOpt // avoid re-allocating `Some`
              case None    => Some(factory.newBuilder += elem)
            }
          }
          .map(_.fold(factory.fromSpecific(Nil))(_.result()))
        Elems(res)
      }
      @throws[UnsupportedOperationException]
      def foldToOption: Streamed[Option[A]] = {
        val res =
          toSource
            .take(2)
            .fold(Option.empty[A]) { (opt, elem) =>
              if (opt.isDefined) foldMultipleToOption()
              else Some(elem)
            }
        Elems(res)
      }

      def map[B](f: A => B): Streamed[B]       = Elems(toSource map f)
      def filter(p: A => Boolean): Streamed[A] = Elems(toSource filter p)

      def flatMap[B](f: A => i.Iterable[B]): Streamed[B] =
        Elems(toSource mapConcat f)
      def flatMap[B](f: A => Source[B, _])(implicit fmb: FlatMapBehavior): Streamed[B] =
        Elems(fmb.flatMapSource(toSource)(f))
      def flatMap[B](f: A => Mixed[B])(implicit fmb: FlatMapBehavior, d: Diff1): Streamed[B] =
        Elems(fmb.flatMapSource(toSource.mapConcat(f(_).elems))(_.toSource))
    }
  }

  /**
   * A [[Streamable]] with elements backed by a mixture of collections and
   * [[akka.stream.scaladsl.Source Sources]].
   */
  sealed trait Mixed[+A] extends Streamable[A] {
    private[Streamable] def elems: i.Iterable[Streamed[A]]

    type Repr[+X]           = Mixed[X]
    type FMMaterialized[+X] = Mixed[X]
    type FMStreamed[+X]     = Mixed[X]
  }

  private object Mixed {
    case object Empty extends Mixed[Nothing] {
      private[Streamable] def elems: i.Iterable[Streamed[Nothing]] = Nil

      def toSource: Source[Nothing, _] = Source.empty

      def foldInto[C](factory: Factory[Nothing, C]): Mixed[C] =
        Single(Streamed.Elems(Source.single(factory.fromSpecific(Nil))))
      def foldToOption: Mixed[Option[Nothing]] =
        Single(Streamed.Elems(Source.single(None)))

      def map[B](f: Nothing => B): Mixed[B]             = this
      def filter(p: Nothing => Boolean): Mixed[Nothing] = this

      def flatMap[B](f: Nothing => i.Iterable[B]): Mixed[B]                                                 = this
      def flatMap[B](f: Nothing => Source[B, _])(implicit fmb: FlatMapBehavior): Mixed[B]                   = this
      def flatMap[B](f: Nothing => Mixed[B])(implicit fmb: FlatMapBehavior, d: Diff1): Mixed[B]             = this
      override def flatMap[B](f: Nothing => Materialized[B])(implicit d: Diff1): Mixed[B]                   = this
      override def flatMap[B](f: Nothing => Option[B])(implicit d: Diff2): Mixed[B]                         = this
      override def flatMap[B](f: Nothing => Streamed[B])(implicit fmb: FlatMapBehavior, d: Diff2): Mixed[B] = this
      override def flatMap[B](f: Nothing => IterableOnce[B])(implicit mcs: MutableCollectionSupport): Mixed[B] =
        this
    }

    final case class Single[+A](elem: Streamed[A]) extends Mixed[A] {
      private[Streamable] def elems: i.Iterable[Streamed[A]] = List(elem)

      def toSource: Source[A, _] = elem.toSource

      def foldInto[C](factory: Factory[A, C]): Mixed[C] = Single(elem.foldInto(factory))
      def foldToOption: Mixed[Option[A]]                = Single(elem.foldToOption)

      def map[B](f: A => B): Mixed[B]       = Single(elem map f)
      def filter(p: A => Boolean): Mixed[A] = Single(elem filter p)

      def flatMap[B](f: A => i.Iterable[B]): Mixed[B]                               = Single(elem flatMap f)
      def flatMap[B](f: A => Source[B, _])(implicit fmb: FlatMapBehavior): Mixed[B] = Single(elem flatMap f)
      def flatMap[B](f: A => Mixed[B])(implicit fmb: FlatMapBehavior, d: Diff1): Mixed[B] =
        Single(elem flatMap f)
    }

    final case class Elems[+A](elems: i.Seq[Streamed[A]]) extends Mixed[A] {
      def toSource: Source[A, _] = Source(elems).flatMapConcat(_.toSource)

      def foldInto[C](factory: Factory[A, C]): Mixed[C] =
        Single(Streamed.Elems(toSource).foldInto(factory))
      @throws[UnsupportedOperationException]
      def foldToOption: Mixed[Option[A]] =
        Single(Streamed.Elems(toSource).foldToOption)

      @inline private[this] def transform[B](op: Streamed[A] => Streamed[B]): Mixed[B] =
        Elems(elems map op)

      def map[B](f: A => B): Mixed[B]                    = transform(_ map f)
      def filter(p: A => Boolean): Mixed[A]              = transform(_ filter p)
      override def withFilter(p: A => Boolean): Mixed[A] = new WithFilter(elems, p)

      def flatMap[B](f: A => i.Iterable[B]): Mixed[B]                               = transform(_ flatMap f)
      def flatMap[B](f: A => Source[B, _])(implicit fmb: FlatMapBehavior): Mixed[B] = transform(_ flatMap f)
      def flatMap[B](f: A => Mixed[B])(implicit fmb: FlatMapBehavior, d: Diff1): Mixed[B] =
        transform(_ flatMap f)
    }

    final class WithFilter[+A](self: i.Seq[Streamed[A]], p: A => Boolean) extends Mixed[A] {
      private def filtered: View[Streamed[A]] = self.view.map(_ filter p)

      @inline private def force[B](view: View[Streamed[B]]): Mixed[B] =
        Mixed(self.iterableFactory from view)
      @inline private def forced: Mixed[A] = force(filtered)
      @inline private def transform[B](op: Streamed[A] => Streamed[B]): Mixed[B] =
        force(filtered map op)

      private[Streamable] def elems: i.Iterable[Streamed[A]] = self.iterableFactory from filtered

      def toSource: Source[A, _] = forced.toSource

      def foldInto[C](factory: Factory[A, C]): Mixed[C] = forced foldInto factory
      @throws[UnsupportedOperationException]
      def foldToOption: Mixed[Option[A]] = forced.foldToOption

      def map[B](f: A => B): Mixed[B]                    = transform(_ map f)
      def filter(q: A => Boolean): Mixed[A]              = transform(_ filter q)
      override def withFilter(q: A => Boolean): Mixed[A] = new WithFilter(self, (a: A) => p(a) && q(a))

      def flatMap[B](f: A => i.Iterable[B]): Mixed[B]                               = transform(_ flatMap f)
      def flatMap[B](f: A => Source[B, _])(implicit fmb: FlatMapBehavior): Mixed[B] = transform(_ flatMap f)
      def flatMap[B](f: A => Mixed[B])(implicit fmb: FlatMapBehavior, d: Diff1): Mixed[B] =
        transform(_ flatMap f)
    }

    def apply[A](elems: i.Seq[Streamed[A]]): Mixed[A] =
      elems.knownSize match {
        case 0 => Empty
        case 1 => Single(elems.head)
        case _ => Elems(elems)
      }
  }

  /**
   * An enum describing how to `flatMap` a `Source` into a stream.
   *
   *   - [[FlatMapBehavior.Concat Concat]] uses `flatMapConcat`
   *   - [[FlatMapBehavior.Merge Merge]] uses `flatMapMerge`
   *
   * The default behavior is Concat, because it produces and retains
   * a stable element order.
   */
  sealed trait FlatMapBehavior {
    private[Streamable] def flatMapSource[A, B](source: Source[A, _])(f: A => Source[B, _]): Source[B, _]
  }

  object FlatMapBehavior {

    /** `flatMap` a `Source` into the stream using `flatMapConcat`. */
    case object Concat extends FlatMapBehavior {
      private[Streamable] def flatMapSource[A, B](source: Source[A, _])(f: A => Source[B, _]): Source[B, _] =
        source.flatMapConcat(f)
    }

    /**
     * `flatMap` a `Source` into the stream using `flatMapMerge`
     * with the given breadth.
     */
    final case class Merge(breadth: Int) extends FlatMapBehavior {
      private[Streamable] def flatMapSource[A, B](source: Source[A, _])(f: A => Source[B, Any]): Source[B, _] =
        source.flatMapMerge(breadth, f)
    }

    /**
     * The default behavior is [[Concat]] because the resulting
     * elements have a stable order.
     */
    implicit def default: FlatMapBehavior = Concat
  }

  /**
   * Utility classes for enabling overloads that would otherwise have
   * the same erased signatures.
   */
  object Overload {
    sealed trait Diff1
    implicit object Diff1 extends Diff1

    sealed trait Diff2
    implicit object Diff2 extends Diff2
  }

  // This can just be `private`, but scaladoc is both wrong AND doesn't support -Wconf
  private[Streamable] final val mutableCollectionSupportMsg =
    "Mutable collections are not supported by default, as they must often be converted immediately to " +
      "immutable collections in order to be used. To use mutable collections, import " +
      "MutableCollectionSupport.Implicits.support"

  /**
   * A type needed in the implicit scope to allow transformations
   * using mutable collections.
   *
   * Import [[MutableCollectionSupport.Implicits.support]] to use.
   */
  @implicitNotFound(mutableCollectionSupportMsg)
  sealed trait MutableCollectionSupport

  object MutableCollectionSupport {
    private[Streamable] object Impl extends MutableCollectionSupport

    object Implicits {
      implicit def support: MutableCollectionSupport = Impl
    }
  }
}

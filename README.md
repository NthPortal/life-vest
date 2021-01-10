# life-vest

![Build Status](https://img.shields.io/github/workflow/status/NthPortal/life-vest/Continuous%20Integration?logo=github&style=for-the-badge)
[![Coverage Status](https://img.shields.io/coveralls/github/NthPortal/life-vest/main?logo=coveralls&style=for-the-badge)](https://coveralls.io/github/NthPortal/life-vest?branch=main)
[![Maven Central](https://img.shields.io/maven-central/v/lgbt.princess/life-vest_2.13?logo=apache-maven&style=for-the-badge)](https://mvnrepository.com/artifact/lgbt.princess/life-vest)
[![Versioning](https://img.shields.io/badge/versioning-semver%202.0.0-blue.svg?logo=semver&style=for-the-badge)](http://semver.org/spec/v2.0.0.html)
[![Docs](https://www.javadoc.io/badge2/lgbt.princess/life-vest_2.13/docs.svg?color=blue&logo=scala&style=for-the-badge)](https://www.javadoc.io/doc/lgbt.princess/life-vest_2.13)

A library for treating stream and non-stream transformations uniformly when processing data with Akka Streams. The name is a pun on a life vest being the "uniform" of someone working in a stream or river.

## Add to Your sbt Build

**Scala 2.13**

```sbt
libraryDependencies += "lgbt.princess" %% "life-vest" % "0.1.0"
```

## Usage

#### Wrapping collections, `Option`s and `Source`s

The first enumerator in the `for` comprehension must be wrapped in a `Streamable(...)`,
as well as each subsequent enumerator up through the last enumerator that is a `Source`.
See the following examples:

```scala
import akka.stream.scaladsl.Source
import lgbt.princess.lifevest.Streamable

// four enumerators need to be wrapped in `Streamable(...)` because
// the last of them is a `Source`.
def foo: Streamable[Int] = for {
  i <- Streamable(Set(1, 2, 3, 4, 5))
  jStr <- Streamable(List("1", "2"))
  j = jStr.toInt
  k <- Streamable(Some(1))
  x <- Streamable(Source(List(1, 2, 3)))
  if i + x > 6
  y <- Vector(6, 7)
} yield i + j + k + x + y

// only the first enumerator needs to be wrapped in `Streamable(...)`
def bar: Streamable[Int] = for {
  i <- Streamable(Source(List(1, 2, 3, 4, 5)))
  jStr <- List("1", "2")
  j = jStr.toInt
  k <- Some(1).toList
  x <- Set(1, 2, 3)
  if i + x > 6
  y <- Vector(6, 7)
} yield i + j + k + x + y
```

The requirement to wrap multiple enumerators in `Streamable(...)` in some cases is
because collections and `Option` do not have `.flatMap` methods taking a function
returning a `Source`. This is similar to how you sometimes need to call `.toList`
on an `Option` when `flatMap`ing with collections, because `Option#flatMap` takes
a function returning an `Option`, not a function returning a collection.

#### `Streamable`s directly from elements

Besides creating `Streamable`s from collections, `Option`s and `Source`s, you can create
`Streamable`s directly from elements. You can create a `Streamable` containing a single
element using `Streamable.single`, and a `Streamable` from an arbitrary number of elements
using the `Streamable.elems` varargs method. There is also a `Streamable.empty` method for
returning a `Streamable` with no elements.

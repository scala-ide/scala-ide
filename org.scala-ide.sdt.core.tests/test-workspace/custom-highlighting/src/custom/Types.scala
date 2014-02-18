/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package types

trait Base[A] {
  def foo: Int
}

class Concrete(val foo: Int) extends Base[Int]

object ConcreteSingleton extends Concrete(1)

object Types {

  def take(b: Base[_]): Int = b.foo

  ConcreteSingleton.foo

  val c = new Concrete(2)

  c.foo

  val b = new Base[Float] { def foo = 3 }

  b.foo

  take(ConcreteSingleton)

  take(b)

  take(c)
}
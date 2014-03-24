/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package methodsInheritance

trait Base {
  val foo = 1

  def bar = 2

  def baz() = 3
}

class Methods extends Base

class Overrides extends Base {
  override val foo = 11

  override def bar = 12

  override def baz() = 13
}

object MethodsInheritance {
  val a = new Methods

  a.foo

  a.bar

  a.baz()

  val b = new Overrides

  b.foo

  b.bar

  b.baz()
}
/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package methods

class Methods {
  val foo = 1

  def bar = 2

  def baz() = 3
}

object Methods {
  val a = new Methods

  a.foo

  a.bar

  a.baz()
}
/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package mix

class Methods {
  val foo = 1
}

case class foo() extends scala.annotation.StaticAnnotation

class Annotations {
  @foo val foo = 1
}

object Mix {
  val m = new Methods

  m.foo

  val a = new Annotations

  a.foo
}
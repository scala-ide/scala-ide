/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package annotations.varInBody

case class foo() extends scala.annotation.StaticAnnotation

class Entity {
  @foo var bar = 1

  val baz = bar

  val foo = this.bar
}
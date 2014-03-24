/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package annotations.valInBody

case class foo() extends scala.annotation.StaticAnnotation

class Entity {
  @foo val bar = 1

  val baz = bar

  val foo = this.bar
}
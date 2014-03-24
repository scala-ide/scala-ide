/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package annotations.varConstructor

@scala.annotation.meta.field
case class foo() extends scala.annotation.StaticAnnotation

class Entity(@foo var bar: Int)

object Annotations {
  val entity = new Entity(1)

  println(entity.bar)
}
/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package annotations.valConstructor

@scala.annotation.target.field
case class foo() extends scala.annotation.StaticAnnotation

class Entity(@foo val bar: Int)

object Annotations {
  val entity = new Entity(1)

  println(entity.bar)
}
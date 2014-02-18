/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package annotations.valCaseConstructor

@scala.annotation.target.field
case class foo() extends scala.annotation.StaticAnnotation

case class Entity(@foo val bar: Int)

object Annotations {
  val entity = new Entity(1)

  println(entity.bar)
}
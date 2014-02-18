/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package annotations.value

case class foo() extends scala.annotation.StaticAnnotation

class Entity {
  @foo val bar = 1
}

object Annotations {
  val entity = new Entity

  entity.bar
}
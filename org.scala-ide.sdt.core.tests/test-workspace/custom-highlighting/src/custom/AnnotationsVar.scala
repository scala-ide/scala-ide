/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package annotations.variable

case class foo() extends scala.annotation.StaticAnnotation

class Entity {
  @foo var bar = 1
}

object Annotations {
  val entity = new Entity

  entity.bar
}
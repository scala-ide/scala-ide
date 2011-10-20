package ticket_1000412.model

import scala.reflect.BeanProperty

trait TraitA {
  @BeanProperty
  var x: String = _
}

class ClassA extends TraitA {
}
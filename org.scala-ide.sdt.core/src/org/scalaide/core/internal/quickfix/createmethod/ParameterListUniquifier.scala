package org.scalaide.core.internal.quickfix.createmethod

import scala.collection.mutable

object ParameterListUniquifier {
  private val MaxUniqueNames = 999

  def uniquifyParameterNames(parameters: ParameterList) = {
    val paramNames = mutable.Set[String]()
    def uniqueNameFor(name: String) = {
      val uniqueName = if (paramNames.contains(name)) {
        (for (i <- (1 to MaxUniqueNames).toStream if !paramNames.contains(name + i)) yield name + i).head
      } else {
        name
      }
      paramNames.add(uniqueName)
      uniqueName
    }
    for (parameterList <- parameters) yield
      for ((name, tpe) <- parameterList) yield
        ((uniqueNameFor(name), tpe))
  }
}

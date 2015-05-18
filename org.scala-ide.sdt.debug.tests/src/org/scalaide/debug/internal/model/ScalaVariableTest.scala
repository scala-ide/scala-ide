package org.scalaide.debug.internal.model

import org.junit.Test
import org.junit.Assert._

class ScalaVariableTest {

  /**
   * Check the format of the name returned by a scalaArrayElementVariable
   */
  @Test
  def scalaArrayElementVariableName(): Unit = {

    val arrayReference= new ScalaArrayReference(null, null)

    val arrayElementVariable= new ScalaArrayElementVariable(0, arrayReference)

    assertEquals("Bad variable name", "(0)", arrayElementVariable.getName)

    val arrayElementVariable2= new ScalaArrayElementVariable(12, arrayReference)

    assertEquals("Bad variable name", "(12)", arrayElementVariable2.getName)

  }

}
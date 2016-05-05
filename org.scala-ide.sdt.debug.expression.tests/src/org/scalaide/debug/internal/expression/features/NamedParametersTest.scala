/*
 * Copyright (c) 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package features

import org.junit.Test
import org.scalaide.debug.internal.expression.Names.Java

class NamedParametersTest extends BaseIntegrationTest(NamedParametersTest) with DefaultBeforeAfterEach {

  @Test
  def standardMethod(): Unit = {
    eval("""method(123, s = "Ala")""", "Ala123", Java.String)
    eval("""method(s = "Ala", i = 123)""", "Ala123", Java.String)
  }

  @Test
  def varargMethod(): Unit = {
    eval("""varargMethod(1, s = "Ala", ss = "2", "3")""", "Ala123", Java.String)
    eval("""varargMethod(s = "Ala", i = 12, ss = "3")""", "Ala123", Java.String)
    eval("""varargMethod(ss = "3", i = 12, s = "Ala")""", "Ala123", Java.String)
  }

  @Test
  def defaultArgMethod(): Unit = {
    eval("""defaultArgMethod()""", "Ala123", Java.String)
    eval("""defaultArgMethod(c = 4)""", "Ala124", Java.String)
    eval("""defaultArgMethod(s = "Ola")""", "Ola123", Java.String)
    eval("""defaultArgMethod(a = 9, c = 7)""", "Ala927", Java.String)
    eval("""defaultArgMethod(a = 9, c = 7, s = "Ela", b = 8)""", "Ela987", Java.String)
  }

}

object NamedParametersTest extends BaseIntegrationTestCompanion(TestValues.NamedParametersTestCase) with DefaultBeforeAfterAll

/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.features

import org.junit.Test
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.Names.Java

class NamedParameterTest extends BaseIntegrationTest(NamedParameterTest) {

  @Test
  def namedParameter(): Unit =
    eval("libClass.withNamedParameter(left = false)", "left false top true", Java.boxed.String)

  @Test
  def defaultParameter(): Unit =
    eval("libClass.withDefaultValue()", "ala", Java.boxed.String)

  @Test
  def mixExplicitAndDefaultParameter(): Unit =
    eval("""libClass.withExplicitAndDefaultValue("dr")""", "dr ala", Java.boxed.String)

}

object NamedParameterTest extends BaseIntegrationTestCompanion
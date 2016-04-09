package org.scalaide.debug.internal.expression.features

import org.junit.Test
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.TestValues.NestedPackagesTestCase
import org.scalaide.debug.internal.expression.ReflectiveCompilationFailure
import org.scalaide.debug.internal.expression.ScalaExpressionEvaluatorPlugin
import org.scalaide.debug.internal.preferences.ExprEvalPreferencePage

class NestedPackagesTest extends BaseIntegrationTest(NestedPackagesTest) {
  private def toggleAddImportsFromCurrentFile(addImports: Boolean): Unit = {
    val store = ScalaExpressionEvaluatorPlugin().getPreferenceStore
    store.setValue(ExprEvalPreferencePage.AddImportsFromCurrentFile, addImports)
  }

  @Test(expected = classOf[ReflectiveCompilationFailure])
  def shouldFailBecauseNestedPackagesNotSupportedByToolbox(): Unit = {
    toggleAddImportsFromCurrentFile(addImports = true)

    eval("None.getOrElse(0)", "0", Java.primitives.int)
  }

  @Test
  def shouldSucceedAlthoughNestedPackagesNotSupportedByToolboxBecauseImportsAreNotAdded(): Unit = {
    toggleAddImportsFromCurrentFile(addImports = false)

    eval("None.getOrElse(0)", "0", Java.primitives.int)
  }
}

object NestedPackagesTest extends BaseIntegrationTestCompanion(NestedPackagesTestCase)

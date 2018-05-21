/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.features

import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.ScalaExpressionEvaluatorPlugin
import org.scalaide.debug.internal.expression.TestValues.FileImportsTestCase
import org.scalaide.debug.internal.preferences.ExprEvalPreferencePage
import org.junit.Ignore

class ImportsTest extends BaseIntegrationTest(ImportsTest) {

  @Test
  def importFromObject(): Unit =
    eval("import debug.LibObject._; libObj1", "libObj1", Java.String)

  @Test
  @Ignore
  def importFromNestedObject(): Unit =
    eval("import debug.LibObject.LibNestedObject._; libObj2", "libObj2", Java.String)

  @Test
  def importFromTopOfFile(): Unit = {
    eval("ImportedObject.importedObject", "importedObject", Java.String)
    eval("new ImportedClass().importedClass", "importedClass", Java.String)
  }

  @Test
  def importFromMiddleOfFile(): Unit = {
    eval("ImportedObject2.importedObject2", "importedObject2", Java.String)
    eval("new ImportedClass2().importedClass2", "importedClass2", Java.String)
  }
}

object ImportsTest extends BaseIntegrationTestCompanion(FileImportsTestCase) {
  @BeforeClass
  def setupForTest(): Unit = {
    ScalaExpressionEvaluatorPlugin().getPreferenceStore.setValue(ExprEvalPreferencePage.AddImportsFromCurrentFile, true)
  }

  @AfterClass
  def tearDownAfterTest(): Unit = {
    ScalaExpressionEvaluatorPlugin().getPreferenceStore.setValue(ExprEvalPreferencePage.AddImportsFromCurrentFile, false)
  }
}

/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.features

import org.junit.Test
import org.junit.Ignore
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.Names.Java

class ImportsTest extends BaseIntegrationTest(ImportsTest) {

  @Test
  def importFromObject(): Unit =
    eval("import debug.LibObject._; libObj1", "libObj1", Java.boxed.String)

  @Test
  def importFromNestedObject(): Unit =
    eval("import debug.LibObject.LibNestedObject._; libObj2", "libObj2", Java.boxed.String)

  @Test
  def importFromTopOfFile(): Unit = {
    eval("ImportedObject.importedObject", "importedObject", Java.boxed.String)
    eval("new ImportedClass().importedClass", "importedClass", Java.boxed.String)
  }

  @Ignore("TODO - O-6485 - support for overloaded imports")
  @Test
  def importFromMiddleOfFile(): Unit = {
    eval("ImportedObject2.importedObject2", "importedObject2", Java.boxed.String)
    eval("new ImportedClass2.importedClass2", "importedClass2", Java.boxed.String)
  }

}

import org.scalaide.debug.internal.expression.TestValues.FileImports._

object ImportsTest extends BaseIntegrationTestCompanion(
  fileName = fileName,
  lineNumber = breakpointLine
)
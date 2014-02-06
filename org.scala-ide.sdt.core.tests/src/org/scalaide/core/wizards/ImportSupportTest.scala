/*
 * Copyright 2010 LAMP/EPFL
 *
 */
package org.scalaide.core.wizards

import org.junit.Test
import org.junit.Assert
import org.junit.Ignore
import org.scalaide.ui.internal.wizards.ImportSupport

class ImportSupportTest {

  import Assert._

  private val Default_Package = "current.test"

  private val im = ImportSupport(Default_Package)
  private val testData = List(
    "scala.Option",
    "scala.actors.Actor",
    "scala.collection.immutable.Set[T]",
    "scala.collection.immutable.List[T]")

  testData foreach im.addImport

  @Test
  def currentPackageExcluded() {
    assertFalse(im.getImports contains Default_Package)
  }

  @Test
  def correctImportCount() {
    assertTrue(im.getImports.size == 2)
  }

  @Ignore
  @Test
  def packageMembersCombined() {
    val imports = im.getImports
    assertTrue(imports.mkString(", "), imports contains "scala.collection.immutable.{Set, List}")
  }

}

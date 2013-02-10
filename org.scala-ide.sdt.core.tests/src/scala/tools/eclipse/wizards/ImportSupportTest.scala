/*
 * Copyright 2010 LAMP/EPFL
 * @author Tim Clendenen
 *
 */
package scala.tools.eclipse.wizards

import org.junit.{ Test, Assert, Ignore }

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
  def currentPackageExcluded = {
    assertFalse(im.getImports contains Default_Package)
  }

  @Test
  def correctImportCount = {
    assertTrue(im.getImports.size == 2)
  }

  @Ignore
  @Test
  def packageMembersCombined = {
    val imports = im.getImports
    assertTrue(imports.mkString(", "), imports contains "scala.collection.immutable.{Set, List}")
  }

}

package org.scalaide.core.scalaelements

import org.scalaide.core.internal.jdt.model.ScalaSourceTypeElement
import org.scalaide.core.testsetup.SDTTestUtils
import org.scalaide.core.testsetup.TestProjectSetup
import org.scalaide.logging.HasLogger
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test

object ScalaElementsNameTest extends TestProjectSetup("scala-elements") {
  @BeforeClass
  def setup(): Unit = {
    SDTTestUtils.enableAutoBuild(false)
  }

  @AfterClass
  def tearDown(): Unit = {
    SDTTestUtils.deleteProjects(project)
  }

  val mainObject = "test.a.Main$"
  val extendedTrait = "test.b.c.C"
  val clazz = "test.b.B"
  val nestedClass = "test.b.B$BB"
  val nestedObject = "test.b.B$OB$"
}

class ScalaElementsNameTest extends HasLogger {
  import ScalaElementsNameTest._

  @Test
  def shouldCollectAllJavaTypesWithPkgNotRespodingToFoldersStructure(): Unit = {
    cleanProject()
    val cu = scalaCompilationUnit("test/ScalaElementExamples.scala")
    waitUntilTypechecked(cu)

    val allTypes = cu.getAllTypes
    val actualTypes = allTypes.collect {
      case e: ScalaSourceTypeElement => e
    }.map { _.getFullyQualifiedName }.toSet

    val expectedTypes = Set(mainObject, extendedTrait, clazz, nestedClass, nestedObject)
    assert((expectedTypes & actualTypes) == expectedTypes, s"Expected all in expected types, got difference: ${(expectedTypes & actualTypes).mkString(",")}")
  }
}

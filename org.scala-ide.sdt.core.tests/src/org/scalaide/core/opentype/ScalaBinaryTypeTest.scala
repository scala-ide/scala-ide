package org.scalaide.core
package opentype

import testsetup.SDTTestUtils._
import org.junit._
import org.eclipse.core.runtime.Path
import org.scalaide.core.internal.jdt.model.ScalaClassFile
import org.eclipse.jdt.core.IOrdinaryClassFile
import org.scalaide.core.IScalaProject

object ScalaBinaryTypeTest {
  var prj: IScalaProject = _

  @BeforeClass
  def setUp(): Unit = {
    val Seq(prj) = createProjects("bintest")
    this.prj = prj
  }

  @AfterClass
  def tearDown(): Unit = {
    deleteProjects(prj)
  }
}

class ScalaBinaryTypeTest {
  import ScalaBinaryTypeTest._

  @Test
  def topLevelObjectIsFound(): Unit = {
    testForPath("scala/collection/immutable/Nil.class")
  }

  @Test
  def topLevelTraitIsFound(): Unit = {
    testForPath("scala/collection/parallel/mutable/ParSeq.class")
  }

  @Test
  def innerClassInTraitIsFound(): Unit = {
    testForPath("scala/collection/parallel/ParSeqLike$Elements.class")
  }

  private def testForPath(path: String): Unit = {
    val elementsCf = prj.javaProject.findElement(new Path(path)).asInstanceOf[IOrdinaryClassFile]
    Assert.assertNotNull("Couldn't find classfile " + path, elementsCf)
    val elementsClass = elementsCf.getType()
    elementsCf match {
      case scf: ScalaClassFile =>
        Assert.assertTrue("Couldn't find corresponding element " + elementsClass.getElementName, scf.getCorrespondingElement(elementsClass).isDefined)
    }
  }
}
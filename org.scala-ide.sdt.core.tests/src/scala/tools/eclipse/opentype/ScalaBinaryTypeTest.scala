package scala.tools.eclipse.opentype

import scala.tools.eclipse.testsetup.SDTTestUtils._
import org.junit._
import org.eclipse.core.runtime.Path
import scala.tools.eclipse.javaelements.ScalaClassFile
import org.eclipse.jdt.core.IClassFile
import scala.tools.eclipse.ScalaProject

object ScalaBinaryTypeTest {
  var prj: ScalaProject = _

  @BeforeClass
  def setUp() {
    val Seq(prj) = createProjects("bintest")
    this.prj = prj
  }

  @AfterClass
  def tearDown() {
    deleteProjects(prj)
  }
}

class ScalaBinaryTypeTest {
  import ScalaBinaryTypeTest._

  @Test
  def topLevelObjectIsFound() {
    testForPath("scala/collection/immutable/Nil.class")
  }

  @Test
  def topLevelTraitIsFound() {
    testForPath("scala/collection/parallel/mutable/ParSeq.class")
  }

  @Test
  def innerClassInTraitIsFound() {
    testForPath("scala/collection/parallel/ParSeqLike$Elements.class")
  }

  private def testForPath(path: String) {
    val elementsCf = prj.javaProject.findElement(new Path(path)).asInstanceOf[IClassFile]
    Assert.assertNotNull("Couldn't find classfile " + path, elementsCf)
    val elementsClass = elementsCf.getType()
    elementsCf match {
      case scf: ScalaClassFile =>
        Assert.assertTrue("Couldn't find corresponding element " + elementsClass.getElementName, scf.getCorrespondingElement(elementsClass).isDefined)
    }
  }
}
package scala.tools.eclipse
package structurebuilder

import org.junit._
import scala.tools.eclipse.testsetup.SDTTestUtils
import org.eclipse.jdt.core._
import org.eclipse.core.runtime.Path

class StructureBuilderTest {

  var project: ScalaProject = _
  
  def setupWorkspace {
    // auto-building is off
    val desc = SDTTestUtils.workspace.getDescription
    desc.setAutoBuilding(false)
    SDTTestUtils.workspace.setDescription(desc)
    ScalaPlugin
  }
  
  def traversePackage(pkg: IPackageFragment) {
    for (unit <- pkg.getChildren) {
      unit.getOpenable.open(null)
      println("-" * 10 + unit)
    }
  }
  
  @Before def setupProject {
    
    project = SDTTestUtils.setupProject("simple-structure-builder")
    println(project)
  }
  
  @Test def testStructure() {
    val javaProject = JavaCore.create(project.underlying)
    
    javaProject.open(null)
    val srcPackageRoot = javaProject.findPackageFragmentRoot(new Path("/simple-structure-builder/src"))
    Assert.assertNotNull(srcPackageRoot)
    
    srcPackageRoot.open(null)
    for (pkg <- srcPackageRoot.getChildren) pkg match {
      case p: IPackageFragment => traversePackage(p)
      case _ => println("unknown " + pkg)
    }

  }
}
package scala.tools.eclipse.pc

import scala.tools.eclipse._
import org.junit.Test
import scala.tools.eclipse.javaelements.ScalaSourceFile
import org.junit.Assert
import testsetup.SDTTestUtils
import org.eclipse.core.resources.IFile
import scala.tools.eclipse.util.EclipseUtils

object PresentationCompilerRefreshTest extends testsetup.TestProjectSetup("pc_refresh")

class PresentationCompilerRefreshTest {
  import PresentationCompilerRefreshTest._

  @Test def removeExistingFileAndChangeReferenceToNewFile() {
    val unitA = scalaCompilationUnit("a/A.scala")
    
    unitA.doWithSourceFile { (sf, comp) =>
      comp.askReload(unitA, unitA.getContents()).get // synchronize with the presentation compiler
    }
    
    val problemsInA = unitA.asInstanceOf[ScalaSourceFile].getProblems()
    Assert.assertTrue("no errors in A.scala", problemsInA eq null)
    
    EclipseUtils.workspaceRunnableIn(SDTTestUtils.workspace) { monitor =>
      SDTTestUtils.addFileToProject(project.underlying, "src/b/C.scala", C_scala)
      SDTTestUtils.changeContentOfFile(project.underlying, unitA.getResource().getAdapter(classOf[IFile]).asInstanceOf[IFile], new_A_scala)
    }

    unitA.doWithSourceFile { (sf, comp) =>
      comp.askReload(unitA, unitA.getContents()).get // synchronize with the presentation compiler
    }
    

    val problemsInANew = unitA.asInstanceOf[ScalaSourceFile].getProblems()
    
    Assert.assertTrue("no errors in A.scala after change " + problemsInANew, problemsInANew eq null)
  }
  
  val new_A_scala = """
package a
import b._

class A {
  val b = new C()
}
"""
  
  val C_scala = """
package b
    
class C
""" 
}

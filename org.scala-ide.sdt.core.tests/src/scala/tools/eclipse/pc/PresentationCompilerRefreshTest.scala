package scala.tools.eclipse.pc

import scala.tools.eclipse._
import org.junit.Test
import scala.tools.eclipse.javaelements.ScalaSourceFile
import org.junit.Assert
import testsetup.SDTTestUtils
import org.eclipse.core.resources.IFile
import scala.tools.eclipse.util.EclipseUtils
import scala.tools.eclipse.testsetup._

object PresentationCompilerRefreshTest extends TestProjectSetup("pc_refresh") with CustomAssertion

class PresentationCompilerRefreshTest {
  import PresentationCompilerRefreshTest._

  @Test def removeExistingFileAndChangeReferenceToNewFile() {
    val unitA = scalaCompilationUnit("a/A.scala")
    
    unitA.doWithSourceFile { (sf, comp) =>
      comp.askReload(unitA, unitA.getContents()).get // synchronize with the presentation compiler
    }
    
    assertNoErrors(unitA)
    
    EclipseUtils.workspaceRunnableIn(SDTTestUtils.workspace) { monitor =>
      SDTTestUtils.addFileToProject(project.underlying, "src/b/C.scala", C_scala)
      SDTTestUtils.changeContentOfFile(project.underlying, unitA.getResource().getAdapter(classOf[IFile]).asInstanceOf[IFile], new_A_scala)
    }

    unitA.doWithSourceFile { (sf, comp) =>
      comp.askReload(unitA, unitA.getContents()).get // synchronize with the presentation compiler
    }

    assertNoErrors(unitA)
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

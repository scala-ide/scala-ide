package org.scalaide.core
package pc

import org.junit.Test
import org.eclipse.core.resources.IFile
import org.scalaide.util.eclipse.EclipseUtils
import testsetup._

object PresentationCompilerRefreshTest extends TestProjectSetup("pc_refresh") with CustomAssertion

class PresentationCompilerRefreshTest {
  import PresentationCompilerRefreshTest._

  @Test def removeExistingFileAndChangeReferenceToNewFile(): Unit = {
    val unitA = scalaCompilationUnit("a/A.scala")

    unitA.scalaProject.presentationCompiler { comp =>
      comp.askReload(unitA, unitA.lastSourceMap().sourceFile).get // synchronize with the presentation compiler
    }

    assertNoErrors(unitA)

    EclipseUtils.workspaceRunnableIn(SDTTestUtils.workspace) { monitor =>
      SDTTestUtils.addFileToProject(project.underlying, "src/b/C.scala", C_scala)
      SDTTestUtils.changeContentOfFile(unitA.getResource().getAdapter(classOf[IFile]).asInstanceOf[IFile], new_A_scala)
    }

    unitA.scalaProject.presentationCompiler { comp =>
      comp.askReload(unitA, unitA.lastSourceMap().sourceFile).get // synchronize with the presentation compiler
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

package org.scalaide.core
package quickassist

import org.eclipse.jdt.internal.core.util.SimpleDocument
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal
import org.junit.Assert
import java.util.ArrayList
import org.scalaide.core.IScalaProject
import org.scalaide.core.internal.jdt.model.ScalaSourceFile
import testsetup.SDTTestUtils
import scala.util.control.Exception
import scala.collection.immutable

trait QuickAssistTest {
  var project: IScalaProject = _

  def create(name: String) {
    project = SDTTestUtils.createProjectInWorkspace(name)
  }

  def createSourceFile(packageName: String, unitName: String)(contents: String): ScalaSourceFile = {
    val pack = SDTTestUtils.createSourcePackage(packageName)(project)
    SDTTestUtils.createCompilationUnit(pack, unitName, contents).asInstanceOf[ScalaSourceFile]
  }

  def delete() {
    SDTTestUtils.deleteProjects(project)
  }
}

trait QuickAssistTestHelper {

  val quickAssist: QuickAssist

  def createSource(packageName: String, unitName: String)(contents: String): ScalaSourceFile

  def runQuickAssistWith(contents: String)(f: Option[IJavaCompletionProposal] => Unit): Unit = {
    val unit = createSource("test", "Test.scala")(contents.filterNot(_ == '^'))

    try {
      val Seq(pos) = SDTTestUtils.positionsOf(contents.toCharArray(), "^")
      // get all corrections for the problem
      f(quickAssist.compute(InvocationContext(unit, pos, 0, Nil)).headOption)
    } finally
      unit.delete(true, null)
  }

  def noAssistsFor(contents: String): Unit = {
    runQuickAssistWith(contents) { p =>
      Assert.assertTrue(s"Unexpected abstract member $p", p.isEmpty)
    }
  }
}

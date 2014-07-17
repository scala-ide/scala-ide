package org.scalaide.core
package quickassist

import org.eclipse.jdt.internal.core.util.SimpleDocument
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal
import org.junit.Assert
import java.util.ArrayList
import org.scalaide.core.internal.project.ScalaProject
import org.scalaide.core.internal.jdt.model.ScalaSourceFile
import testsetup.SDTTestUtils
import scala.util.control.Exception
import scala.collection.immutable

trait QuickAssistTest {
  var project: ScalaProject = _
  var simulator = new EclipseUserSimulator

  def create(name: String) {
    project = simulator.createProjectInWorkspace(name)
  }

  def createSourceFile(packageName: String, unitName: String)(contents: String): ScalaSourceFile = {
    val pack = SDTTestUtils.createSourcePackage(packageName)(project)
    simulator.createCompilationUnit(pack, unitName, contents).asInstanceOf[ScalaSourceFile]
  }

  def delete() {
    Exception.ignoring(classOf[Exception]) { project.underlying.delete(true, null) }
  }
}

trait QuickAssistTestHelper {

  val quickAssist: { def suggestsFor(ssf: ScalaSourceFile, offset: Int): immutable.Seq[IJavaCompletionProposal] }

  def createSource(packageName: String, unitName: String)(contents: String): ScalaSourceFile

  def runQuickAssistWith(contents: String)(f: Option[IJavaCompletionProposal] => Unit) = {
    val unit = createSource("test", "Test.scala")(contents.filterNot(_ == '^'))

    try {
      val Seq(pos) = SDTTestUtils.positionsOf(contents.toCharArray(), "^")
      // get all corrections for the problem
      f(quickAssist.suggestsFor(unit, pos).headOption)
    } finally
      unit.delete(true, null)
  }

  def noAssistsFor(contents: String) = {
    runQuickAssistWith(contents) { p =>
      Assert.assertTrue(s"Unexpected abstract member $p", p.isEmpty)
    }
  }
}
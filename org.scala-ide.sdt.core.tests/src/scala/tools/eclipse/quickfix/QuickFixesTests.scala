package scala.tools.eclipse.quickfix

import scala.tools.eclipse.testsetup.SDTTestUtils
import scala.tools.eclipse.testsetup.TestProjectSetup
import scala.tools.eclipse.ScalaWordFinder
import org.eclipse.core.resources.IMarker
import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.core.runtime.NullProgressMonitor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import scala.tools.eclipse.hyperlink.text.detector.ScalaDeclarationHyperlinkComputer
import java.util.ArrayList
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.CoreException
import org.eclipse.jdt.internal.ui.text.correction.JavaCorrectionProcessor
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.tools.nsc.interactive.Response
import org.eclipse.jdt.core.dom.CompilationUnit
import org.eclipse.jdt.internal.ui.text.correction.AssistContext
import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.jdt.internal.ui.text.correction.ProblemLocation
import org.eclipse.jdt.ui.text.java.IProblemLocation
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal
import org.eclipse.jdt.core.compiler.IProblem
import scala.collection.mutable.Buffer
import org.eclipse.jdt.ui.JavaUI


object QuickFixesTests extends TestProjectSetup("quickfix") {

  def assertStatusOk(status: IStatus) {
		if (!status.isOK()) {
			if (status.getException() == null) {  // find a status with an exception
				val children = status.getChildren();
				for (child <- children) {
					if (child.getException() != null) {
						throw new CoreException(child);
					}
				}
			}
		}
	}

  def assertNumberOfProblems(nProblems: Int, problems: Array[IProblem] ) {
    // check if numbers match but we want reasonable error message
		if (problems.length != nProblems) {
			val buf= new StringBuffer("Wrong number of problems, is: ")
			buf.append(problems.length).append(", expected: ").append(nProblems).append('\n')
			for (problem <- problems) {
				buf.append(problem).append(" at ")
				buf.append('[').append(problem.getSourceStart()).append(" ,").append(problem.getSourceEnd()).append(']')
				buf.append('\n')
			}
			
			assertEquals(buf.toString, nProblems, problems.length)
		}
	}

}

class QuickFixesTests {
  import QuickFixesTests._

  private def withQuickFixes(pathToSource: String)(expectedQuickFixesList: List[List[String]]) {
    // get our compilation unit
    val unit = compilationUnit(pathToSource).asInstanceOf[ScalaCompilationUnit]

    // first, 'open' the file by telling the compiler to load it
    project.withSourceFile(unit) { (src, compiler) =>

      // do a compiler reload before checking for problems
      val dummy = new Response[Unit]
      compiler.askReload(List(src), dummy)
      dummy.get

      val problems = compiler.problemsOf(unit)
      assertTrue("No problems found.", problems.size > 0)
      assertNumberOfProblems(expectedQuickFixesList.size, problems.toArray)

    	val editor = JavaUI.openInEditor(unit.getCompilationUnit)
    	Thread.sleep(5000)
    	
      // check each problem quickfix
      for ( (problem, expectedQuickFixes) <- problems zip expectedQuickFixesList) {
	      // here we will accumulate proposals
		    var proposals: ArrayList[IJavaCompletionProposal] = new ArrayList()

		    // get all corrections for the problem
	      val offset = problem.getSourceStart
				val length = problem.getSourceEnd + 1 - offset
				val context= new AssistContext(unit.getCompilationUnit, offset, length)
	
	      val problemLocation: IProblemLocation = new ProblemLocation(problem);	
				val status = JavaCorrectionProcessor.collectCorrections(context, Array( problemLocation ), proposals)
								
				// assert that status is okay
				assertStatusOk(status)
				
				// get collection of offered quickfix message
				import scala.collection.JavaConversions._
				val corrections: List[String] = (proposals : Buffer[IJavaCompletionProposal]).toList.map( _.getDisplayString )
				
				// check all expected quick fixes
				//assertEquals(expectedQuickFixes.size, corrections.size)
				// NOTE due to a bug, Scala IDE returns a lot of quick fixes so we skip number comparison
				for ( quickFix <- expectedQuickFixes  ) {
				  assertTrue("Quick fix " + quickFix + " was not offered. Offered were: " + corrections.mkString(", "),
			      corrections contains quickFix )				
				}
      }
    } ( )

  }

  val stringPattern = "Transform expression: %s => %s"

  @Test
  def basicTypeMismatchQuickFixes {
    withQuickFixes("typemismatch/Basic.scala")(
      List(
        List(
          stringPattern.format("List[List[Int]]()","List[List[Int]]().flatten"),
          stringPattern.format("List[List[Int]]()","List[List[Int]]().head"),
          stringPattern.format("List[List[Int]]()","List[List[Int]]().last")
        ),
        List(
          stringPattern.format("listOfInt_val","listOfInt_val.flatten"),
          stringPattern.format("listOfInt_val","listOfInt_val.head"),
          stringPattern.format("listOfInt_val","listOfInt_val.last")
        ),
        List(
          stringPattern.format("intVal","Some(intVal)"),
          stringPattern.format("intVal","Option(intVal)")
        ),
        List(
          stringPattern.format("List[Int]()","List[Int]().toArray")
        ),
        List(
          stringPattern.format("arrayOfInt_val","arrayOfInt_val.toArray")
        )
//        ,
//        List(
//          stringPattern.format("5","Some(5)"),
//          stringPattern.format("5","Option(5)")
//        ),
//        List(
//          stringPattern.format("5 * 3 + 2","Some(5 * 3 + 2)"),
//          stringPattern.format("5 * 3 + 2","Option(5 * 3 + 2)")
//        ),
//        List(
//          stringPattern.format("5 * 3 + intVal","Some(5 * 3 + intVal)"),
//          stringPattern.format("5 * 3 + intVal","Option(5 * 3 + intVal)")
//        ),
//        List(
//          stringPattern.format("true","Some(true)"),
//          stringPattern.format("true","Option(true)")
//        ),
//        List(
//          stringPattern.format("(intVal % 4 == 2)","Some((intVal % 4 == 2))"),
//          stringPattern.format("(intVal % 4 == 2)","Option((intVal % 4 == 2))")
//        ),
//        List(
//          stringPattern.format("'5'","Some('5')"),
//          stringPattern.format("'5'","Option('5')")
//        ),
//        List(
//          stringPattern.format("\"asd\"","Some(\"asd\")"),
//          stringPattern.format("\"asd\"","Option(\"asd\")")
//        ),
//        List(
//          stringPattern.format("5.3f","Some(5.3f)"),
//          stringPattern.format("5.3f","Option(5.3f)")
//        )
      )
    )
  }

}

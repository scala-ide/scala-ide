package scala.tools.eclipse

import org.eclipse.core.runtime.NullProgressMonitor

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.ui.JavaUI;

import org.junit.{ Test, Assert, Ignore, Before, BeforeClass }
import org.junit.Assert._
import org.eclipse.core.resources.IWorkspace;

class ScalaSourceFileEditorTest {
  var scalaProject : ScalaProject = null;
  var compilationUnit : ICompilationUnit = null;
  var workspace : IWorkspace = null;
  val sourceCode = new StringBuffer(); 
  val eclipseInstance = new EclipseUserSimulator;
  
  @Before
  def initialise() {
	import eclipseInstance._
	
    scalaProject = createProjectInWorkspace("test_project");	
	val pack = createPackage("test.top_level");
    
    sourceCode.append(
    """
       package test.top_level; 
       class AClass { 
    	 var a = 1; 
       }
    """);
    Thread.sleep(200)
    compilationUnit = createCompilationUnit(pack, "AClass.scala", sourceCode.toString)
    Thread.sleep(200)
  }
}
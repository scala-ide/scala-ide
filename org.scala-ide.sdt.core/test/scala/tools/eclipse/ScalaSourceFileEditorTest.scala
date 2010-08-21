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
     
  @Test
  def testTopLevelMapRefresh() {
	import eclipseInstance._
	
	openInEditor(compilationUnit)
    sourceCode.append("class AnotherClass {}")
    setContentOfCurrentEditor(sourceCode.toString)
    saveCurrentEditor();
    
    //the TopLevel is computed for the old source 
    var file = scalaProject.topLevelMap.typeToFile.get("test.top_level.AClass")  
    assertEquals("/test_project/src/test/top_level/AClass.scala", file.get.getFullPath.toString)
    
    //BUG
    file = scalaProject.topLevelMap.typeToFile.get("test.top_level.AnotherClass")
    file match {
	  case None => fail("Top level map entry not found")
	  case Some(f) => assertEquals("/test_project/src/test/top_level/AClass.scala", f.getFullPath.toString) 
	}
  }
}
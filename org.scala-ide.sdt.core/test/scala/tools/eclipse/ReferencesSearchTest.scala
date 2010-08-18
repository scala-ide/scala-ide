package scala.tools.eclipse

import org.eclipse.jdt.core.ICompilationUnit;


import org.junit.{ Test, Assert, Ignore, Before, BeforeClass }
import org.junit.Assert._
import org.eclipse.core.resources.IWorkspace;

class ReferenceSearchTest { 

  var scalaProject : ScalaProject = null;
  var compilationUnit1, compilationUnit2 : ICompilationUnit = null;
  var workspace : IWorkspace = null;
  val eclipseInstance = new EclipseUserSimulator;
  
  @Before
  def initialise() {
	import eclipseInstance._
	
    scalaProject = createProjectInWorkspace("test_project");	
	val pack = createPackage("test.top_level");
    Thread.sleep(200)
    
	val sourceCode1 = new StringBuffer(); 
    sourceCode1.append(
    """
       package test.top_level; 
       class ReferencedClass { 
         var a = 1;
         var c : ReferencedClass = null;
       }
    """);
    compilationUnit1 = createCompilationUnit(pack, "ReferencedClass.scala", sourceCode1.toString)
    Thread.sleep(200)
    
    val sourceCode2 = new StringBuffer(); 
    sourceCode2.append(
    """
       package test.top_level; 
       object ReferringObject { 
         var b : ReferencedClass = null;
       }
    """);
    compilationUnit2 = createCompilationUnit(pack, "ReferringObject.scala", sourceCode2.toString)
    Thread.sleep(200)    
    
    buildWorkspace;
    Thread.sleep(200)    
  }
     
  @Test
  def testReferences() {
	import eclipseInstance._
	import scala.tools.eclipse.javaelements._
	
	val references = searchType("ReferencedClass");
	
	assertEquals(3, references.size)
   
	def getReferencesOffsetsFromFile(name : String) = {
	  val filtered = references.filter(r => {
	   	val fileName = r.getResource.getName
	   	fileName.equals(name)
	  })
	  filtered.map(_.getOffset) 		
	}
	 
	assertEquals(getReferencesOffsetsFromFile("ReferringObject.scala").size, 1)
	assertEquals(getReferencesOffsetsFromFile("ReferringObject.scala").head, 83)
	
	assertEquals(getReferencesOffsetsFromFile("ReferencedClass.scala").size, 2)
	
  }
}
package scala.tools.eclipse

import org.eclipse.jdt.core.ICompilationUnit;


import org.junit.{ Test, Assert, Ignore, Before, BeforeClass }
import org.junit.Assert._
import org.eclipse.core.resources.IWorkspace;

class ReferencesSearchTest { 
  var scalaProject : ScalaProject = null;
  var compilationUnit1, compilationUnit2, compilationUnit3, compilationUnit4 : ICompilationUnit = null;
  var workspace : IWorkspace = null;
  val eclipseInstance = new EclipseUserSimulator;
  
  @Before
  def initialise() { 
	import eclipseInstance._ 

	if (ReferenceSearchTest.initialized) //this is a hack to simulate @BeforeClass
	  return;
	ReferenceSearchTest.initialized = true;
	
    scalaProject = createProjectInWorkspace("test_reference_search");	
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
    
    val sourceCode2 = new StringBuffer(); 
    sourceCode2.append(
    """
       package test.top_level; 
       object ReferringObject { 
         var b : ReferencedClass = null;
         var o = ReferencedObject;
         var p = ReferencedObject.a;
       }
       object ReferencedObject { 
         var a = 2;
       }
    """);
    compilationUnit2 = createCompilationUnit(pack, "ReferringObject.scala", sourceCode2.toString)
    
    val sourceCode3 = new StringBuffer(); 
    sourceCode3.append(
    """
       package test.top_level;
       class ATrait {
         object AnObject {
	       def aMeth = 2;
         }
	     AnObject.aMeth
       }
    """);
    compilationUnit3 = createCompilationUnit(pack, "AnotherTestCase.scala", sourceCode3.toString)
    
    val sourceCode4 = new StringBuffer(); 
    sourceCode4.append(
    """
       object moduleObject {
         val member = "a string"
       }
       import moduleObject.{_}
    """);
    compilationUnit4 = createCompilationUnit(pack, "FourthTestCase.scala", sourceCode4.toString)
    
    buildWorkspace; 
    Thread.sleep(1500)     
  }	
  
  @Test
  def testObjectReferences {
	import eclipseInstance._  
    val references = searchType("ReferencedObject");
	 
	assertEquals(3, references.size)
	assertEquals(0, getReferencesOffsetsFromFile(references, "ReferencedClass.scala").size)
	
	assertEquals(3, getReferencesOffsetsFromFile(references, "ReferringObject.scala").size)
	assertEquals(159, getReferencesOffsetsFromFile(references, "ReferringObject.scala").head)
  }
  
  @Test
  def testAnotherObjectReferences {
	import eclipseInstance._  
    val references = searchType("AnObject");
	 
	assertEquals(1, references.size)
	assertEquals(121, getReferencesOffsetsFromFile(references, "AnotherTestCase.scala").head)
  }
  
  @Test
  def testModuleReferences {
	import eclipseInstance._  
    val references = searchType("moduleObject");
	 
	assertEquals(1, references.size)
	assertEquals(79, getReferencesOffsetsFromFile(references, "FourthTestCase.scala").head)
  }
    
  @Test
  def testClassReferences {
	import eclipseInstance._
	import scala.tools.eclipse.javaelements._
	
	val references = searchType("ReferencedClass");
	
	assertEquals(2, references.size)	 
	assertEquals(1, getReferencesOffsetsFromFile(references, "ReferringObject.scala").size)
	assertEquals(83, getReferencesOffsetsFromFile(references, "ReferringObject.scala").head)
	
	assertEquals(1, getReferencesOffsetsFromFile(references, "ReferencedClass.scala").size)
  }
  
  import org.eclipse.jdt.core.search._
  def getReferencesOffsetsFromFile(references : List[SearchMatch], name : String) = {
    val filtered = references.filter(r => {
	  val fileName = r.getResource.getName
   	  fileName.equals(name)
	})
	filtered.map(_.getOffset) 		
  }

}

object ReferenceSearchTest { 
  var initialized = false;
}
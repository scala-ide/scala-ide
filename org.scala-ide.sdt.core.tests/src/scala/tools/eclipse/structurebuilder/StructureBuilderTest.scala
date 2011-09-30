package scala.tools.eclipse
package structurebuilder


import org.junit._
import Assert._
import org.mockito.Mockito._
import org.mockito.Matchers._
import scala.tools.eclipse.testsetup.SDTTestUtils
import org.eclipse.jdt.core._
import org.eclipse.core.runtime.Path
import org.eclipse.jdt.core.search._
import scala.collection.mutable.ListBuffer
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import org.eclipse.jdt.internal.corext.util.JavaModelUtil
import org.eclipse.core.runtime.NullProgressMonitor

object StructureBuilderTest extends testsetup.TestProjectSetup("simple-structure-builder")

class StructureBuilderTest {
  import StructureBuilderTest._
  
  def setupWorkspace {
    // auto-building is off
    val desc = SDTTestUtils.workspace.getDescription
    desc.setAutoBuilding(false)
    SDTTestUtils.workspace.setDescription(desc)
  }
  
  /** Return the toString output for all compilation units in the given fragment. */
  def compilationUnitsStructure(frag: IPackageFragment): String = {
    val buf = new StringBuilder
    frag.open(null)
    frag.getCompilationUnits.foreach { cu =>
      cu.open(null)
      buf.append(cu)
      buf.append('\n')
    }
    buf.toString.trim
  }
  
  @Test def testAnnotations() {
    val annotsPkg = srcPackageRoot.getPackageFragment("annots");
    assertNotNull(annotsPkg)
    val cu = annotsPkg.getCompilationUnit("ScalaTestSuite.scala")
    assertTrue(cu.exists)
    val tpe = cu.findPrimaryType()
    assertNotNull("Primary type should not be null", tpe)
    val m1 = tpe.getMethod("someTestMethod", Array())
    val m2 = tpe.getMethod("anotherTestMethod", Array())
    println(m1.getAnnotations.toList)
    println(m2.getAnnotations.toList)
    
    assertTrue(m1.getAnnotations.length == 1)
    assertTrue(m1.getAnnotation("Test").exists)
    assertTrue(m2.getAnnotations.length == 1)
    assertTrue(m2.getAnnotation("Test").exists)
  }
  
  @Test def testSearchIndexAnnotations() {
    import IJavaSearchConstants._
    val pattern = SearchPattern.createPattern("org.junit.Test", TYPE, ANNOTATION_TYPE_REFERENCE, SearchPattern.R_PREFIX_MATCH)
    val scope = SearchEngine.createJavaSearchScope(Array(srcPackageRoot.getPackageFragment("annots"): IJavaElement))
    
    var elems = Set[IMethod]()
    
    val requestor = new SearchRequestor {
      def acceptSearchMatch(m: SearchMatch) {
        m.getElement match {
          case method: IMethod => elems += method
          case elem => 
            println(elem)
            fail
        }
      }
    }
    
    (new SearchEngine).search(pattern, Array[SearchParticipant](SearchEngine.getDefaultSearchParticipant), scope, requestor, null)
    println(elems)
    assertEquals(2, elems.size)
  }
  
  /** This tests that search for annotations still succeeds after a reconcile. The
   *  reconciler triggers type-checking, which in turn produces typed trees. The indexer
   *  is run again on the document, this time with attributed trees. Type-checked trees
   *  move annotations from the tree to the symbol, hence this test.
   */
  @Test def testSearchAnnotationsAfterReconcile {
    val unit = compilationUnit("annots/ScalaTestSuite.scala")
    unit.becomeWorkingCopy(null)
    unit.getBuffer().append("  ")
    unit.commitWorkingCopy(true, null) // trigger indexing
    unit.discardWorkingCopy()

    // search again for annotations
    testSearchIndexAnnotations()
  }

  /** Test the structure as seen by the JDT. Use the JDT API to 
   *  retrieve the package `traits' and compare the toString output.
   */
  @Test def testStructure() {
    // when
    val fragment = srcPackageRoot.getPackageFragment("traits")
    // then
    val jdtStructure = compilationUnitsStructure(fragment)
    // verify
    assertEquals(TraitsTestOracle.expectedFragment, jdtStructure)
  }
  
  @Test def correctlyExposeToJDT_ScalaArray_1000586() {
    // when
    val fragment = srcPackageRoot.getPackageFragment("t1000586")
    // then
    val jdtStructure = compilationUnitsStructure(fragment)
    // verify
    assertEquals(T1000586TestOracle.expectedFragment, jdtStructure)
  }
  
  @Test def correctlyExposeToJDT_ScalaMethodReturnType_WithTypeParameters_1000568() {
    // when
    val fragment = srcPackageRoot.getPackageFragment("t1000568")
    // then
    val jdtStructure = compilationUnitsStructure(fragment)
    // verify
    assertEquals(T1000568TestOracle.expectedFragment, jdtStructure)
  }
  
  import org.eclipse.jdt.core.compiler.IProblem
  private class ProblemReporterAdapter extends IProblemRequestor {
    def acceptProblem(problem: IProblem) {}
    def beginReporting() {}
    def endReporting() {}
    def isActive(): Boolean = true
  }
  
  @Test
  def t1000524_neg_JavaCodeCannotCall_ScalaModuleMethodThatIsDefinedWithTheSameSignatureInTheCompanionClass() {
    val expectedProblem = "Pb(201) Cannot make a static reference to the non-static method getOpt1(Option<T>) from the type OptTest"
    
    //when
    val unit = compilationUnit("t1000524_neg/opttest/java/OT.java")
    
    val owner = new WorkingCopyOwner() {
      override def getProblemRequestor(unit: org.eclipse.jdt.core.ICompilationUnit): IProblemRequestor =
        new ProblemReporterAdapter {
          override def acceptProblem(problem: IProblem) {
            //verify
            assertEquals(expectedProblem, problem.toString())
          }
        }
    }

    // then
    // this will trigger the java reconciler so that the problems will be reported to the `requestor`
    unit.getWorkingCopy(owner, new NullProgressMonitor)
  }
  
  @Test
  def t1000524_pos_JavaCodeCanCall_ScalaMethodWithParametricTypes() {
    //when
    val requestor = mock(classOf[IProblemRequestor])
    when(requestor.isActive()).thenReturn(true)
    
    val owner = mock(classOf[WorkingCopyOwner])
    when(owner.getProblemRequestor(any())).thenReturn(requestor)
    
    val unit = compilationUnit("t1000524_pos/opttest/java/OT.java")
    
    // then
    // this will trigger the java reconciler so that the problems will be reported to the `requestor`
    unit.getWorkingCopy(owner, new NullProgressMonitor)

    // verify
    verify(requestor, times(0)).acceptProblem(any())
  }
  
  @Test
  def t1000524_1_JavaCodeCanCall_ScalaMethodWithParametricBoundedType() {
    //when
    val requestor = mock(classOf[IProblemRequestor])
    when(requestor.isActive()).thenReturn(true)
    
    val owner = mock(classOf[WorkingCopyOwner])
    when(owner.getProblemRequestor(any())).thenReturn(requestor)
    
    val unit = compilationUnit("t1000524_1/opttest/java/OT.java")
    
    // then
    // this will trigger the java reconciler so that the problems will be reported to the `requestor`
    unit.getWorkingCopy(owner, new NullProgressMonitor)

    // verify
    verify(requestor, times(0)).acceptProblem(any())
  }
  
  @Test
  def t1000524_2_JavaCodeCanCall_ScalaMethodWithParametricBoundedType() {
    //when
    val requestor = mock(classOf[IProblemRequestor])
    when(requestor.isActive()).thenReturn(true)
    
    val owner = mock(classOf[WorkingCopyOwner])
    when(owner.getProblemRequestor(any())).thenReturn(requestor)
    
    val unit = compilationUnit("t1000524_2/opttest/java/OT.java")
    
    // then
    // this will trigger the java reconciler so that the problems will be reported to the `requestor`
    unit.getWorkingCopy(owner, new NullProgressMonitor)

    // verify
    verify(requestor, times(0)).acceptProblem(any())
  }
  
  @Test
  def genericSignature_ofScalaMember_is_correctlyExposedToJDT() {
    //when
    val requestor = mock(classOf[IProblemRequestor])
    when(requestor.isActive()).thenReturn(true)
    
    val owner = mock(classOf[WorkingCopyOwner])
    when(owner.getProblemRequestor(any())).thenReturn(requestor)
    
    val unit = compilationUnit("generic_signature/akka/Actor.java")
    
    // then
    // this will trigger the java reconciler so that the problems will be reported to the `requestor`
    unit.getWorkingCopy(owner, new NullProgressMonitor)

    // verify
    verify(requestor, times(0)).acceptProblem(any())
  }
  
  @Test
  def javaCalls_ScalaMethod_withContravariantArgumentType() {
    //when
    val requestor = mock(classOf[IProblemRequestor])
    when(requestor.isActive()).thenReturn(true)
    
    val owner = mock(classOf[WorkingCopyOwner])
    when(owner.getProblemRequestor(any())).thenReturn(requestor)
    
    val unit = compilationUnit("method_with_type_contravariance/Foo.java")
    
    // then
    // this will trigger the java reconciler so that the problems will be reported to the `requestor`
    unit.getWorkingCopy(owner, new NullProgressMonitor)

    // verify
    verify(requestor, times(0)).acceptProblem(any())
  }
  
  @Test
  def exposeJavaGenericSigOfScalaClass() {
    //when
    val requestor = mock(classOf[IProblemRequestor])
    when(requestor.isActive()).thenReturn(true)
    
    val owner = mock(classOf[WorkingCopyOwner])
    when(owner.getProblemRequestor(any())).thenReturn(requestor)
    
    val unit = compilationUnit("t1000625/MyFoo.java")
    
    // then
    // this will trigger the java reconciler so that the problems will be reported to the `requestor`
    unit.getWorkingCopy(owner, new NullProgressMonitor)

    // verify
    verify(requestor, times(0)).acceptProblem(any())
  }
}
package scala.tools.eclipse
package structurebuilder


import org.junit._
import Assert._
import scala.tools.eclipse.testsetup.SDTTestUtils
import org.eclipse.jdt.core._
import org.eclipse.core.runtime.Path
import org.eclipse.jdt.core.search._
import scala.collection.mutable.ListBuffer
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import org.eclipse.jdt.internal.corext.util.JavaModelUtil

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
    buf.toString
  }
  
  @Test def testAnnotations() {
    val annotsPkg = srcPackageRoot.getPackageFragment("annots");
    assertNotNull(annotsPkg)
    val cu = annotsPkg.getCompilationUnit("ScalaTestSuite.scala")
    assertTrue(cu.exists)
    val tpe = cu.findPrimaryType()
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
    assertEquals(elems.size, 2)
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
    val fragment = srcPackageRoot.getPackageFragment("traits")
    assertNotNull(fragment)
    assertEquals(expectedFragment, compilationUnitsStructure(fragment).trim)
  }
  
  lazy val expectedFragment = """
T1.scala [in traits [in src [in simple-structure-builder]]]
  package traits
  interface T1
    scala.Nothing notImplemented()
    java.lang.Object t
    java.lang.Object t()
    scala.collection.immutable.List xs
    scala.collection.immutable.List xs()
    scala.Nothing m(int, int)
    java.lang.Object n(int, int, int)
    java.lang.Object T
    java.lang.Object U
    java.lang.Object Z
    class Inner
      Inner()
C1.scala [in traits [in src [in simple-structure-builder]]]
  package traits
  import scala.annotation.*
  import scala.reflect.BeanProperty
  import org.junit.Test
  class C
    int _x
    traits.C.T _y
    C(int, traits.C.T)
    C(traits.C.T)
    int x
    int x()
    int lz
    int lz()
    java.lang.String v
    java.lang.String v()
    void v_$eq(java.lang.String)
    java.lang.Object volVar
    java.lang.Object volVar()
    void volVar_$eq(java.lang.Object)
    java.lang.String CONSTANT
    java.lang.String CONSTANT()
    int beanVal
    int beanVal()
    int getBeanVal()
    int beanVar
    int beanVar()
    void beanVar_$eq(int)
    int getBeanVar()
    void setBeanVar(int)
    boolean nullaryMethod()
    void method()
    void annotatedMethod()
    java.lang.Object curriedMethod(int, int)
    boolean nullaryMethod1()
    void method1()
    void method2()
    java.lang.Object curriedMethod1(int, int)
    class InnerC
      int x
      int x()
      InnerC(int)
    java.lang.Object T
    java.lang.Object U
    java.lang.Object map(scala.Function1)
    scala.Null takeArray(scala.Array)
    scala.Null takeArray2(scala.Array)
    java.lang.Object localClass(int)
      class class Object
        $anon()
        void run()
    int localMethod(int)
    long localVals(int)""".trim
}
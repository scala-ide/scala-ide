package scala.tools.eclipse
package structurebuilder


import org.junit._
import Assert._
import scala.tools.eclipse.testsetup.SDTTestUtils
import org.eclipse.jdt.core._
import org.eclipse.core.runtime.Path

class StructureBuilderTest {

  var project: ScalaProject = _
  
  def setupWorkspace {
    // auto-building is off
    val desc = SDTTestUtils.workspace.getDescription
    desc.setAutoBuilding(false)
    SDTTestUtils.workspace.setDescription(desc)
    ScalaPlugin
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
  
  /** Copy the project from 'test-workspace' to the temporary, clean unit-test 
   *  workspace.
   */
  @Before def setupProject {
    project = SDTTestUtils.setupProject("simple-structure-builder")
    println(project)
  }

  /** Test the structure as seen by the JDT. Use the JDT API to 
   *  retrieve the package `traits' and compare the toString output.
   */
  @Test def testStructure() {
    val javaProject = JavaCore.create(project.underlying)
    
    javaProject.open(null)
    val srcPackageRoot = javaProject.findPackageFragmentRoot(new Path("/simple-structure-builder/src"))
    Assert.assertNotNull(srcPackageRoot)
    
    srcPackageRoot.open(null)
    println("children: " + srcPackageRoot.getChildren.toList)
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
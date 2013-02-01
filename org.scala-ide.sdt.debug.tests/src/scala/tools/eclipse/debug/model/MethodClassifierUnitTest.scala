package scala.tools.eclipse.debug.model

import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.core.runtime.IPath
import org.eclipse.core.runtime.Path
import org.junit.Assert
import org.junit.BeforeClass
import org.junit.Test
import org.mockito.Matchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.when
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer

import com.sun.jdi.Field
import com.sun.jdi.Method
import com.sun.jdi.ReferenceType

import scala.tools.eclipse.debug.classfile.ClassfileParser
import scala.tools.eclipse.debug.classfile.ConstantPool
import scala.tools.eclipse.debug.model.MethodClassifier.DefaultGetter
import scala.tools.eclipse.debug.model.MethodClassifier.Getter
import scala.tools.eclipse.debug.model.MethodClassifier.Setter
import scala.tools.eclipse.testsetup.SDTTestUtils
import scala.tools.eclipse.testsetup.TestProjectSetup

object MethodClassifierUnitTest extends TestProjectSetup("constant-pool", bundleName = "org.scala-ide.sdt.debug.tests") {
  @BeforeClass
  def buildProject() {
    project.underlying.build(IncrementalProjectBuilder.CLEAN_BUILD, null)
    project.underlying.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, null)
  }
}

class MethodClassifierUnitTest {

  @Test
  def cp_testConcreteClassExtendsTrait() {
    val resource = SDTTestUtils.workspace.getRoot().getFile("/constant-pool/bin/stepping/ConcreteClass.class")
    ConstantPool.fromFile(resource.getLocation().toFile())
  }

  @Test
  def cp_testDefaultsClass() {
    val resource = SDTTestUtils.workspace.getRoot().getFile("/constant-pool/bin/stepping/Defaults.class")
    ConstantPool.fromFile(resource.getLocation().toFile())
  }

  @Test
  def cp_testDefaultsObjectClass() {
    val resource = SDTTestUtils.workspace.getRoot().getFile("/constant-pool/bin/stepping/Defaults$.class")
    ConstantPool.fromFile(resource.getLocation().toFile())
  }

  @Test
  def cp_testBaseTrait() {
    val resource = SDTTestUtils.workspace.getRoot().getFile("/constant-pool/bin/stepping/BaseTrait.class")
    ConstantPool.fromFile(resource.getLocation().toFile())
  }

  @Test
  def cp_testImplClass() {
    val resource = SDTTestUtils.workspace.getRoot().getFile("/constant-pool/bin/stepping/BaseTrait$class.class")
    ConstantPool.fromFile(resource.getLocation().toFile())
  }

  @Test
  def cp_testMethodClassifier() {
    val resource = SDTTestUtils.workspace.getRoot().getFile("/constant-pool/bin/stepping/MethodClassifiers.class")
    ConstantPool.fromFile(resource.getLocation().toFile())
  }

  lazy val resource = SDTTestUtils.workspace.getRoot().getFile("/constant-pool/bin/stepping/ConcreteClass.class")
  lazy val parser = new ClassfileParser(resource.getLocation().toFile())

  @Test
  def testForwarder_pos_1() {
    assertForwarder("concreteTraitMethod1", true)
  }

  @Test
  def testForwarder_pos_2() {
    assertForwarder("concreteTraitMethod2", true)
  }

  @Test
  def testForwarder_pos_3() {
    assertForwarder("concreteTraitMethod3", true)
  }

  @Test
  def testForwarder_pos_4() {
    assertForwarder("concreteTraitMethod4", true)
  }

  @Test
  def testForwarder_pos_withDefault() {
    assertForwarder("concreteTraitMethodWithDefault", true)
  }

  @Test
  def testForwarder_neg_defaults() {
    assertForwarder("abstractMethodWithDefault", false)
  }

  @Test
  def testForwarder_neg_Java() {
    // this is a call to a Java static method with the same name (but it's not inside an Impl class)
    assertForwarder("console", false)
  }

  @Test
  def testDefaults_pos_1() {
    assertDefaultGetter("init$default$1", true)
  }

  @Test
  def testDefaults_pos_2() {
    assertDefaultGetter("methWithDefaults2$default$4", true)
  }

  @Test
  def testDefaults_pos_3() {
    assertDefaultGetter("methWithDefaults2$default$2", true)
  }

  @Test
  def testGetter_pos_concrete() {
    assertGetter("concreteField1", true)
  }

  @Test
  def testGetter_pos_concrete_mutable() {
    assertGetter("concreteMField1", true)
  }

  @Test
  def testGetter_pos_abs() {
    assertGetter("abstractField1", true)
  }

  @Test
  def testGetter_pos_abs_mutable() {
    assertGetter("abstractMField1", true)
  }

  @Test
  def testSetter_pos_concrete() {
    assertSetter("concreteMField1_$eq", true)
  }

  @Test
  def testSetter_pos_abs() {
    assertSetter("abstractMField1_$eq", true)
  }

  @Test
  def testGetter_nonPrivate() {
    assertGetter("stepping$ConcreteClass$$fakePrivate", true)
  }

  @Test
  def testSetter_nonPrivate() {
    assertSetter("stepping$ConcreteClass$$fakePrivate_$eq", true)
  }

  def assertForwarder(method: String, forwarder: Boolean) {
    Assert.assertEquals("Forwarder test failed", forwarder, MethodClassifier.isForwarderBytecode(parser.methods(method).bytecode, parser.constantPoolBytes, parser.pool.size, method))
  }

  def assertDefaultGetter(name: String, expected: Boolean) {
    val method = mock(classOf[Method])

    when(method.name()).thenReturn(name)
    Assert.assertEquals("Default getter", expected, MethodClassifier.is(DefaultGetter, method))
  }

  def prepareGetterMock(name: String) = {
    val method = mock(classOf[Method])
    val refType = mock(classOf[ReferenceType])

    when(method.name()).thenReturn(name)
    when(method.declaringType()).thenReturn(refType)

    when(refType.fieldByName(anyString())) thenAnswer { inv: InvocationOnMock =>
      inv.getArguments()(0) match {
        case name: String =>
          val field = mock(classOf[Field])
          parser.fields.get(name).map(_ => field).getOrElse(null)
      }
    }
    method
  }

  def assertGetter(name: String, expected: Boolean) {
    Assert.assertEquals("Getter", expected, MethodClassifier.is(Getter, prepareGetterMock(name)))
  }

  def assertSetter(name: String, expected: Boolean) {
    Assert.assertEquals("Setter", expected, MethodClassifier.is(Setter, prepareGetterMock(name)))
  }

  // implicits
  implicit def toPath(str: String): IPath = new Path(str)

  implicit def toAnswer[A](f: InvocationOnMock => A): Answer[A] = new Answer[A] {
    def answer(inv: InvocationOnMock): A = f(inv)
  }
}

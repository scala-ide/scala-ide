package org.scalaide.debug.internal.model

import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.core.runtime.IPath
import org.eclipse.core.runtime.Path
import org.junit.Assert
import org.junit.BeforeClass
import org.junit.Ignore
import org.junit.Test
import org.mockito.Matchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.when
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalaide.core.testsetup.SDTTestUtils
import org.scalaide.core.testsetup.TestProjectSetup
import org.scalaide.debug.internal.classfile.ClassfileParser
import org.scalaide.debug.internal.classfile.ConstantPool
import org.scalaide.debug.internal.model.MethodClassifier.DefaultGetter
import org.scalaide.debug.internal.model.MethodClassifier.Getter
import org.scalaide.debug.internal.model.MethodClassifier.Setter

import com.sun.jdi.Field
import com.sun.jdi.Method
import com.sun.jdi.ReferenceType

object MethodClassifierUnitTest extends TestProjectSetup("constant-pool", bundleName = "org.scala-ide.sdt.debug.tests") {
  @BeforeClass
  def buildProject(): Unit = {
    project.underlying.build(IncrementalProjectBuilder.CLEAN_BUILD, null)
    project.underlying.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, null)
  }
}

class MethodClassifierUnitTest {

  @Test
  def cp_testConcreteClassExtendsTrait(): Unit = {
    val resource = SDTTestUtils.workspace.getRoot().getFile("/constant-pool/bin/stepping/ConcreteClass.class")
    ConstantPool.fromFile(resource.getLocation().toFile())
  }

  @Test
  def cp_testDefaultsClass(): Unit = {
    val resource = SDTTestUtils.workspace.getRoot().getFile("/constant-pool/bin/stepping/Defaults.class")
    ConstantPool.fromFile(resource.getLocation().toFile())
  }

  @Test
  def cp_testDefaultsObjectClass(): Unit = {
    val resource = SDTTestUtils.workspace.getRoot().getFile("/constant-pool/bin/stepping/Defaults$.class")
    ConstantPool.fromFile(resource.getLocation().toFile())
  }

  @Test
  def cp_testBaseTrait(): Unit = {
    val resource = SDTTestUtils.workspace.getRoot().getFile("/constant-pool/bin/stepping/BaseTrait.class")
    ConstantPool.fromFile(resource.getLocation().toFile())
  }

  @Test
  def cp_testImplClass(): Unit = {
    val resource = SDTTestUtils.workspace.getRoot().getFile("/constant-pool/bin/stepping/BaseTrait.class")
    ConstantPool.fromFile(resource.getLocation().toFile())
  }

  @Test
  def cp_testMethodClassifier(): Unit = {
    val resource = SDTTestUtils.workspace.getRoot().getFile("/constant-pool/bin/stepping/MethodClassifiers.class")
    ConstantPool.fromFile(resource.getLocation().toFile())
  }

  lazy val resource = SDTTestUtils.workspace.getRoot().getFile("/constant-pool/bin/stepping/ConcreteClass.class")
  lazy val parser = new ClassfileParser(resource.getLocation().toFile())

  @Ignore("There is no more forwarder class in Java 8. Probably to remove or refactoring.")
  @Test
  def testForwarder_pos_1(): Unit = {
    assertForwarder("concreteTraitMethod1", true)
  }

  @Ignore("There is no more forwarder class in Java 8. Probably to remove or refactoring.")
  @Test
  def testForwarder_pos_2(): Unit = {
    assertForwarder("concreteTraitMethod2", true)
  }

  @Ignore("There is no more forwarder class in Java 8. Probably to remove or refactoring.")
  @Test
  def testForwarder_pos_3(): Unit = {
    assertForwarder("concreteTraitMethod3", true)
  }

  @Ignore("There is no more forwarder class in Java 8. Probably to remove or refactoring.")
  @Test
  def testForwarder_pos_4(): Unit = {
    assertForwarder("concreteTraitMethod4", true)
  }

  @Ignore("There is no more forwarder class in Java 8. Probably to remove or refactoring.")
  @Test
  def testForwarder_pos_withDefault(): Unit = {
    assertForwarder("concreteTraitMethodWithDefault", true)
  }

  @Test
  def testForwarder_neg_defaults(): Unit = {
    assertForwarder("abstractMethodWithDefault", false)
  }

  @Test
  def testForwarder_neg_Java(): Unit = {
    // this is a call to a Java static method with the same name (but it's not inside an Impl class)
    assertForwarder("console", false)
  }

  @Test
  def testDefaults_pos_1(): Unit = {
    assertDefaultGetter("init$default$1", true)
  }

  @Test
  def testDefaults_pos_2(): Unit = {
    assertDefaultGetter("methWithDefaults2$default$4", true)
  }

  @Test
  def testDefaults_pos_3(): Unit = {
    assertDefaultGetter("methWithDefaults2$default$2", true)
  }

  @Test
  def testGetter_pos_concrete(): Unit = {
    assertGetter("concreteField1", true)
  }

  @Test
  def testGetter_pos_concrete_mutable(): Unit = {
    assertGetter("concreteMField1", true)
  }

  @Test
  def testGetter_pos_abs(): Unit = {
    assertGetter("abstractField1", true)
  }

  @Test
  def testGetter_pos_abs_mutable(): Unit = {
    assertGetter("abstractMField1", true)
  }

  @Test
  def testSetter_pos_concrete(): Unit = {
    assertSetter("concreteMField1_$eq", true)
  }

  @Test
  def testSetter_pos_abs(): Unit = {
    assertSetter("abstractMField1_$eq", true)
  }

  @Test
  def testGetter_nonPrivate(): Unit = {
    assertGetter("stepping$ConcreteClass$$fakePrivate", true)
  }

  @Test
  def testSetter_nonPrivate(): Unit = {
    assertSetter("stepping$ConcreteClass$$fakePrivate_$eq", true)
  }

  def assertForwarder(method: String, forwarder: Boolean): Unit = {
    Assert.assertEquals("Forwarder test failed", forwarder, MethodClassifier.isForwarderBytecode(parser.methods(method).bytecode, parser.constantPoolBytes, parser.pool.size, method))
  }

  def assertDefaultGetter(name: String, expected: Boolean): Unit = {
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

  def assertGetter(name: String, expected: Boolean): Unit = {
    Assert.assertEquals("Getter", expected, MethodClassifier.is(Getter, prepareGetterMock(name)))
  }

  def assertSetter(name: String, expected: Boolean): Unit = {
    Assert.assertEquals("Setter", expected, MethodClassifier.is(Setter, prepareGetterMock(name)))
  }

  implicit def toPath(str: String): IPath = new Path(str)

  implicit def toAnswer[A](f: InvocationOnMock => A): Answer[A] = new Answer[A] {
    def answer(inv: InvocationOnMock): A = f(inv)
  }
}

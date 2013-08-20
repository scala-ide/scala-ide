package scala.tools.eclipse.launching

import scala.tools.eclipse.EclipseUserSimulator
import scala.tools.eclipse.ScalaProject
import scala.tools.eclipse.FlakyTest
import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.eclipse.testsetup.SDTTestUtils._
import scala.tools.eclipse.testsetup.SDTTestUtils
import scala.tools.eclipse.testsetup.TestProjectSetup
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test

/** This class checks the functionality behind Run As > JUnit Test, triggered when a user right clicks on a source
  * file in the package explorer and hovers on "Run As". If the source file contains any runnable JUnit4 test class,
  * then a "JUnit Test" option is displayed.
  *
  * Mind that the tests in this class don't actually check the UI functionality, but only the underlying logic.
  * Furthermore, right clicking on a source or on a package (or a project) it's not the same.
  */
class JUnit4TestFinderTest {

  private final val TestProjectName = "runAsJunit"

  private val simulator = new EclipseUserSimulator
  private var projectSetup: TestProjectSetup = _

  @Before
  def createProject() {
    val scalaProject = simulator.createProjectInWorkspace(TestProjectName, withSourceRoot = true)
    projectSetup = new TestProjectSetup(TestProjectName) {
      override lazy val project = scalaProject
    }
    createNeededJunitAnnotations()
  }

  /** All tests defined in this class need some of the types defined in the JUnit library. Since the JUnit library is bundled together with JDT,
    * one would have expected that adding `JavaCore.newContainerEntry(JUnitCore.JUNIT4_CONTAINER_PATH)` to the `projectSetup` classpath would have
    * been enough to have all types defined in the JUnit library available. Well, that is not the case. While doing so works just fine when the
    * test are run _inside_ Eclipse, the JUnit library is __not__ available when the test are run from maven. I've spent a fair amount of time
    * trying to figure out a solution for this, but without luck so far. Adding the different junit bundles in the org.scala-ide.sdt.core.tests
    * MANIFEST didn't help either. It really looks like this is a limitation/bug of the tycho-surfire-plugin (but I can't tell for sure).
    * Interestingly enough, I was not the first one hitting this problem (@see http://dev.eclipse.org/mhonarc/lists/tycho-user/msg00508.html).
    *
    * Until someone finds a better solution, for the moment we simply create the JUnit types that we need to access in the tests and we add
    * them to the `projectSetup` source directory.
    */
  private def createNeededJunitAnnotations(): Unit = {
    projectSetup.createSourceFile("org.junit", "Test.scala") {
      """
         |package org.junit
         |class Test extends scala.annotation.ClassfileAnnotation
      """.stripMargin
    }
    projectSetup.createSourceFile("org.junit.runner", "RunWith.scala") {
      """
         |package org.junit.runner
         |class RunWith(clazz: Class[_]) extends annotation.StaticAnnotation
      """.stripMargin
    }
    projectSetup.createSourceFile("org.junit.runners", "JUnit4.scala") {
      """
         |package org.junit.runners
         |final class JUnit4
      """.stripMargin
    }
  }

  @After
  def deleteProject() {
    SDTTestUtils.deleteProjects(project)
  }

  def project: ScalaProject = projectSetup.project

  @Test
  def findSimpleTestClass() = FlakyTest.retry("findSimpleTestClass") {
    val cu = projectSetup.createSourceFile("test", "MyTest.scala") {
      """
        |package test
        |import org.junit.Test
        |class MyTest {
        |  @Test
        |  def test1() {}
        |}
      """.stripMargin
    }

    runnableJUnitTestClassesIn(cu) matches Set("MyTest")
  }

  @Test
  def findSimpleTestClass_inEmptyPackage() =  FlakyTest.retry("findSimpleTestClass_inEmptyPackage", "expected:<Set(MyTest)> but was:<Set()>") {
    val cu = projectSetup.createSourceFile("", "MyTest.scala") {
      """
        |import org.junit.Test
        |class MyTest {
        |  @Test
        |  def test1() {}
        |}
      """.stripMargin
    }

    runnableJUnitTestClassesIn(cu) matches Set("MyTest")
  }

  @Test
  def findSimpleTestClass_afterTypecheckingSource() = FlakyTest.retry("findSimpleTestClass_afterTypecheckingSource") {
    val cu = projectSetup.createSourceFile("test", "MyTest.scala") {
      """
        |package test
        |import org.junit.Test
        |class MyTest {
        |  @Test
        |  def test1() {}
        |}
      """.stripMargin
    }
    projectSetup.waitUntilTypechecked(cu)

    runnableJUnitTestClassesIn(cu) matches Set("MyTest")
  }

  @Test
  def findTestClass_WhenThereAreParseErrors() = FlakyTest.retry("findTestClass_WhenThereAreParseErrors") {
    val cu = projectSetup.createSourceFile("test", "MyTest.scala") {
      """
        |package test
        |import org.junit.Test
        |class MyTest {
        |  @Test
        |  def test1() {}
        | // unclosed class
      """.stripMargin
    }

    runnableJUnitTestClassesIn(cu) matches Set("MyTest")
  }

  @Test
  def findTestClass_WhenThereAreTypecheckingError() = FlakyTest.retry("findTestClass_WhenThereAreTypecheckingError") {
    val cu = projectSetup.createSourceFile("test", "MyTest.scala") {
      """
        |package test
        |import org.junit.Test
        |class MyTest {
        |  s //unknown identifier
        |  @Test
        |  def test1() {}
        |}
      """.stripMargin
    }
    projectSetup.waitUntilTypechecked(cu)

    runnableJUnitTestClassesIn(cu) matches Set("MyTest")
  }

  @Test
  def findMultipleTestClasses() = FlakyTest.retry("findMultipleTestClasses") {
    val cu = projectSetup.createSourceFile("test", "MyTest.scala") {
      """
        |package test
        |import org.junit.Test
        |class MyTest1 {
        |  @Test
        |  def test1() {}
        |}
        |
        |class MyTest2 {
        |  @Test
        |  def test1() {}
        |}
      """.stripMargin
    }

    runnableJUnitTestClassesIn(cu) matches Set("MyTest1", "MyTest2")
  }

  @Test
  def cannotRunModuleAsJUnitTestClass() = FlakyTest.retry("cannotRunModuleAsJUnitTestClass") {
    val cu = projectSetup.createSourceFile("test", "MyTest.scala") {
      """
        |package test
        |import org.junit.Test
        |object MyTest {
        |  @Test
        |  def test1() {}
        |}
      """.stripMargin
    }

    runnableJUnitTestClassesIn(cu) matches Set.empty
  }

  @Test
  def cannotRunTraitAsJUnitTestClass() = FlakyTest.retry("cannotRunTraitAsJUnitTestClass") {
    val cu = projectSetup.createSourceFile("test", "SuperTest.scala") {
      """
        |package test
        |import org.junit.Test
        |trait SuperTest {
        |  @Test
        |  def test1() {}
        |}
      """.stripMargin
    }

    runnableJUnitTestClassesIn(cu) matches Set.empty
  }

  @Test
  def cannotRunAbstractJUnitTestClass() = FlakyTest.retry("cannotRunAbstractJUnitTestClass") {
    val cu = projectSetup.createSourceFile("test", "AbstractTest.scala") {
      """
        |package test
        |import org.junit.Test
        |abstract class AbstractTest {
        |  @Test
        |  def test1() {}
        |}
      """.stripMargin
    }

    runnableJUnitTestClassesIn(cu) matches Set.empty
  }

  @Test
  def findTestClass_WhenTestMethodIsDefinedInAbstractParent() = FlakyTest.retry("findTestClass_WhenTestMethodIsDefinedInAbstractParent", "expected:<Set(MyTest)> but was:<Set()>") {
    val cu = projectSetup.createSourceFile("test", "MyTest.scala") {
      """
        |package test
        |import org.junit.Test
        |abstract class SuperTest {
        |  @Test
        |  def test1() {}
        |}
        |class MyTest extends SuperTest
      """.stripMargin
    }

    runnableJUnitTestClassesIn(cu) matches Set("MyTest")
  }

  @Test
  def findTestClass_InEmptyPackage_WhenTestMethodIsDefinedInAbstractParent() = FlakyTest.retry("findTestClass_InEmptyPackage_WhenTestMethodIsDefinedInAbstractParent", "expected:<Set(MyTest)> but was:<Set()>") {
    projectSetup.createSourceFile("test", "SuperTest.scala") {
      """
        |package test
        |import org.junit.Test
        |abstract class SuperTest {
        |  @Test
        |  def test1() {}
        |}
      """.stripMargin
    }
    val cu = projectSetup.createSourceFile("", "MyTest.scala") {
      """
        |import test.SuperTest
        |class MyTest extends SuperTest
      """.stripMargin
    }

    runnableJUnitTestClassesIn(cu) matches Set("MyTest")
  }

  @Test
  def findTestClass_WhenDefinedInAbstractParent_andSeparateCompilationUnit() = FlakyTest.retry("findTestClass_WhenDefinedInAbstractParent_andSeparateCompilationUnit", "expected:<Set(MyTest)> but was:<Set()>") {
    projectSetup.createSourceFile("test", "SuperTest.scala") {
      """
        |package test
        |import org.junit.Test
        |abstract class SuperTest {
        |  @Test
        |  def test1() {}
        |}
      """.stripMargin
    }
    val cu = projectSetup.createSourceFile("test", "MyTest.scala") {
      """
        |package test
        |class MyTest extends SuperTest
      """.stripMargin
    }

    runnableJUnitTestClassesIn(cu) matches Set("MyTest")
  }

  @Test
  def findTestClass_WhenTestMethodIsDefinedInParentTrait() = FlakyTest.retry("findTestClass_WhenTestMethodIsDefinedInParentTrait") {
    val cu = projectSetup.createSourceFile("test", "MyTest.scala") {
      """
        |package test
        |import org.junit.Test
        |trait SuperTest {
        |  @Test
        |  def test1() {}
        |}
        |class MyTest extends SuperTest
      """.stripMargin
    }

    runnableJUnitTestClassesIn(cu) matches Set("MyTest")
  }

  @Test
  def findSimpleTestClass_thatUsesRunWithAnnotation() = FlakyTest.retry("findSimpleTestClass_thatUsesRunWithAnnotation", "expected:<Set(MyTest)> but was:<Set()>") {
    val cu = projectSetup.createSourceFile("test", "MyTest.scala") {
      """
        |package test
        |import org.junit.runner.RunWith
        |import org.junit.runners.JUnit4
        |@RunWith(classOf[JUnit4])
        |class MyTest
      """.stripMargin
    }

    runnableJUnitTestClassesIn(cu) matches Set("MyTest")
  }

  @Test
  def findSimpleTestClass_thatInheritsRunWithAnnotation_FromParentAbstractClass() = FlakyTest.retry("findSimpleTestClass_thatInheritsRunWithAnnotation_FromParentAbstractClass", "expected:<Set(MyTest)> but was:<Set()>") {
    val cu = projectSetup.createSourceFile("test", "MyTest.scala") {
      """
        |package test
        |import org.junit.runner.RunWith
        |import org.junit.runners.JUnit4
        |@RunWith(classOf[JUnit4])
        |abstract class SuperTest
        |
        |class MyTest extends SuperTest
      """.stripMargin
    }

    runnableJUnitTestClassesIn(cu) matches Set("MyTest")
  }

  @Test
  def findSimpleTestClass_thatInheritsRunWithAnnotation_FromParentTrait() = FlakyTest.retry("findSimpleTestClass_thatInheritsRunWithAnnotation_FromParentTrait") {
    val cu = projectSetup.createSourceFile("test", "MyTest.scala") {
      """
        |package test
        |import org.junit.runner.RunWith
        |import org.junit.runners.JUnit4
        |@RunWith(classOf[JUnit4])
        |trait SuperTest
        |
        |class MyTest extends SuperTest
      """.stripMargin
    }

    runnableJUnitTestClassesIn(cu) matches Set("MyTest")
  }

  @Test
  def RunWithAnnotation_NotMeaningfulOnModule() = FlakyTest.retry("RunWithAnnotation_NotMeaningfulOnModule") {
    val cu = projectSetup.createSourceFile("test", "ModuleTest.scala") {
      """
        |package test
        |import org.junit.runner.RunWith
        |import org.junit.runners.JUnit4
        |@RunWith(classOf[JUnit4])
        |object ModuleTest
      """.stripMargin
    }

    runnableJUnitTestClassesIn(cu) matches Set.empty
  }

  @Test
  def RunWithAnnotation_NotMeaningfulOnTrait() = FlakyTest.retry("RunWithAnnotation_NotMeaningfulOnTrait") {
    val cu = projectSetup.createSourceFile("test", "TraitTest.scala") {
      """
        |package test
        |import org.junit.runner.RunWith
        |import org.junit.runners.JUnit4
        |@RunWith(classOf[JUnit4])
        |trait TraitTest
      """.stripMargin
    }

    runnableJUnitTestClassesIn(cu) matches Set.empty
  }

  @Test
  def RunWithAnnotation_NotMeaningfulOnAbstractClasses() = FlakyTest.retry("RunWithAnnotation_NotMeaningfulOnAbstractClasses") {
    val cu = projectSetup.createSourceFile("test", "AbstractTest.scala") {
      """
        |package test
        |import org.junit.runner.RunWith
        |import org.junit.runners.JUnit4
        |@RunWith(classOf[JUnit4])
        |abstract class AbstractTest
      """.stripMargin
    }

    runnableJUnitTestClassesIn(cu) matches Set.empty
  }

  private def runnableJUnitTestClassesIn(source: ScalaSourceFile) = new {
    def matches(expectedClassNames: Set[String]): Unit = {
      val jUnitClasses = ScalaLaunchShortcut.getJunitTestClasses(source).toList.map(_.getElementName)
      Assert.assertEquals("test classes found.", expectedClassNames, jUnitClasses.toSet)
    }
  }
}
package org.scalaide.core
package launching

import org.scalaide.core.IScalaProject
import testsetup.SDTTestUtils
import testsetup.TestProjectSetup
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.scalaide.core.internal.launching.ScalaLaunchShortcut

class MainMethodFinderTest {

  private final val TestProjectName = "launchable"

  private var projectSetup: TestProjectSetup = _

  @Before
  def createProject(): Unit = {
    val scalaProject = SDTTestUtils.createProjectInWorkspace(TestProjectName, withSourceRoot = true)
    projectSetup = new TestProjectSetup(TestProjectName) {
      override lazy val project = scalaProject
    }
  }

  @After
  def deleteProject(): Unit = {
    SDTTestUtils.deleteProjects(project)
  }

  def project: IScalaProject = projectSetup.project

  @Test
  def findMainMethods(): Unit = {
    val cu = projectSetup.createSourceFile("test", "MyMain.scala") {
      """
        |package test
        |
        |object OuterWithWronMain {
        |  def main(args: Seq[String]): Unit = {} // not Array[String]
        |}
        |
        |object OuterWithGoodMain {
        |  def main(args: Array[String]): Unit = {}
        |}
        |
        |class ClassWithGoodMain {
        |  def main(args: Array[String]): Unit = {}  // it's a class, should not be reported
        |}
        |
        |object ObjectExtendsApp extends App {} // should be reported
        |
        |object Outer {
        |  object Inner extends App {} // not top level, should not be reported
        |}
      """.stripMargin
    }

    val mainClasses = ScalaLaunchShortcut.getMainMethods(cu).map(_.getElementName)
    Assert.assertEquals("main classes found.", Set("OuterWithGoodMain$", "ObjectExtendsApp$"), mainClasses.toSet)
  }
}

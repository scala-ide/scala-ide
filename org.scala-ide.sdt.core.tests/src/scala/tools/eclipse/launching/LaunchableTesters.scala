package scala.tools.eclipse
package launching

import testsetup.SDTTestUtils._
import scala.tools.eclipse.launching.ScalaLaunchShortcut
import org.junit.Test
import org.junit.Assert
import org.eclipse.jdt.core.ICompilationUnit
import scala.collection.mutable.ListBuffer
import org.junit.After

class LaunchableTesters {

  private val simulator = new EclipseUserSimulator
  private val tempProjects = ListBuffer[ScalaProject]()

  @Test
  def findMainMethods() {
    val contents = """
package test
      
object OuterWithWronMain {
  def main(args: Seq[String]) {} // not Array[String]
}

object OuterWithGoodMain {
  def main(args: Array[String]) {} 
}
      
class ClassWithGoodMain {
  def main(args: Array[String]) {}  // it's a class, should not be reported
}

object ObjectExtendsApp extends App {} // should be reported

object Outer {
  object Inner extends App {} // not top level, should not be reported
}
      
"""
    val cu = prepareProject("main-test", "MyMain.scala", contents)

    val mainClasses = ScalaLaunchShortcut.getMainMethods(cu).toList.map(_.getElementName)
    println(mainClasses)
    Assert.assertEquals("main classes found.", Set("OuterWithGoodMain$", "ObjectExtendsApp$"), mainClasses.toSet)
  }

  @Test
  def findTestClasses() {
    val contents = """

package org {
  package junit {
    class Test extends scala.annotation.ClassfileAnnotation       
      
class MyTest {
  @org.junit.Test
  def test1()
}
      
"""
    val cu = prepareProject("test-test", "MyTest.scala", contents)

    val testClasses = ScalaLaunchShortcut.getMainMethods(cu).toList.map(_.getElementName)
    println(testClasses)
    Assert.assertEquals("main classes found.", Set("MyTest"), testClasses.toSet)
  }

  @After
  def deleteTmepProjects() {
    deleteProjects(tempProjects: _*)
  }

  private def prepareProject(name: String, unitName: String, contents: String): ICompilationUnit = {
    val Seq(prj) = createProjects(name)
    tempProjects += prj
    val pack = createSourcePackage("test")(prj)
    simulator.createCompilationUnit(pack, unitName, contents)
  }
}
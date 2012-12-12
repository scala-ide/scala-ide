package scala.tools.eclipse.launching

import scala.tools.eclipse.testsetup.TestProjectSetup
import org.junit.Test
import org.eclipse.jdt.core.IType
import org.eclipse.core.runtime.NullProgressMonitor
import org.junit.Assert
import org.eclipse.core.runtime.Path
import collection.JavaConverters._
import org.eclipse.core.resources.IResource
import scala.collection.mutable

object TestFinderTest extends TestProjectSetup("test-finder")

class TestFinderTest {
  import TestFinderTest._

  @Test
  def project_possibleMatches() {
    val finder = new JUnit4TestFinder

    val result = new mutable.HashSet[IType]
    val possibleMatches = finder.filteredTestResources(project, project.javaProject, new NullProgressMonitor)
    val expected = Set(getResource("/src/packa/TestA.scala"),
      getResource("/src/packa/FakeTest.scala"),
      getResource("/src/jpacka/JTestA.java"),
      getResource("/src/jpacka/RunWithTest.java"),
      getResource("/src/packb/TestB.scala"),
      getResource("/src/jpackb/FakeJavaTest.java"),
      getResource("/src/jpackb/JTestB.java"),
      getResource("/src/TestInEmptyPackage.scala"))
    Assert.assertEquals("wrong filtered files", expected, possibleMatches.toSet)
  }

  @Test
  def scala_package_possibleMatches() {
    val finder = new JUnit4TestFinder

    val result = new mutable.HashSet[IType]
    val possibleMatches = finder.filteredTestResources(project, project.javaProject.findPackageFragment("/test-finder/src/packa"), new NullProgressMonitor)
    val expected = Set(getResource("/src/packa/TestA.scala"), getResource("/src/packa/FakeTest.scala"))
    Assert.assertEquals("wrong filtered files", expected, possibleMatches.toSet)
  }

  @Test
  def scala_package_matches() {
    val finder = new JUnit4TestFinder

    val result = new mutable.HashSet[IType]
    finder.findTestsInContainer(project.javaProject.findPackageFragment("/test-finder/src/packa"), result, new NullProgressMonitor)

    val expected = Set(getType("packa.TestA"), getType("packa.TestA1"))
    Assert.assertEquals("wrong tests found", expected, result)
  }

  @Test
  def java_package_matches() {
    val finder = new JUnit4TestFinder

    val result = new java.util.HashSet[IType]
    finder.findTestsInContainer(project.javaProject.findPackageFragment("/test-finder/src/jpacka"), result, new NullProgressMonitor)

    val expected = Set(getType("jpacka.JTestA"), getType("jpacka.RunWithTest"))
    Assert.assertEquals("wrong tests found", expected, result.asScala.toSet)
  }

  @Test
  def java_package_matches_inherited() {
    val finder = new JUnit4TestFinder

    val result = new java.util.HashSet[IType]
    finder.findTestsInContainer(project.javaProject.findPackageFragment("/test-finder/src/jpackb"), result, new NullProgressMonitor)

    val expected = Set(getType("jpackb.JTestB"))
    Assert.assertEquals("wrong tests found", expected, result.asScala.toSet)
  }

  @Test
  def project_matches() {
    val finder = new JUnit4TestFinder

    val result = new java.util.HashSet[IType]
    finder.findTestsInContainer(project.javaProject, result, new NullProgressMonitor)

    val expected = Set(
      getType("jpacka.JTestA"),
      getType("jpacka.RunWithTest"),
      getType("jpackb.JTestB"),
      getType("packa.TestA"),
      getType("packa.TestA1"),
      getType("packb.TestB"),
      getType("TestInEmptyPackage"))
    Assert.assertEquals("wrong tests found", expected, result.asScala.toSet)
  }

  @Test
  def src_folder_matches() {
    val finder = new JUnit4TestFinder

    val result = new java.util.HashSet[IType]
    finder.findTestsInContainer(project.javaProject.findPackageFragmentRoot("/test-finder/src"), result, new NullProgressMonitor)

    val expected = Set(
      getType("jpacka.JTestA"),
      getType("jpacka.RunWithTest"),
      getType("jpackb.JTestB"),
      getType("packa.TestA"),
      getType("packa.TestA1"),
      getType("TestInEmptyPackage"),
      getType("packb.TestB"))
    Assert.assertEquals("wrong tests found", expected, result.asScala.toSet)
  }

  @Test
  def only_one_type_element_matches() {
    val finder = new JUnit4TestFinder

    val result = new java.util.HashSet[IType]
    val element = getType("packa.TestA")
    finder.findTestsInContainer(element, result, new NullProgressMonitor)

    // there are two types in the same source, we want only the one that we passed to the finder to be returned
    val expected = Set(element)
    Assert.assertEquals("wrong tests found", expected, result.asScala.toSet)
  }

  @Test
  def empty_package_matches() {
    val finder = new JUnit4TestFinder

    val result = new java.util.HashSet[IType]
    val element = getType("TestInEmptyPackage")
    finder.findTestsInContainer(element, result, new NullProgressMonitor)

    val expected = Set(element)
    Assert.assertEquals("wrong tests found", expected, result.asScala.toSet)
  }

  @Test
  def method_matches() {
    val finder = new JUnit4TestFinder

    val result = new java.util.HashSet[IType]
    val testClass = getType("packa.TestA1")
    val element = testClass.getMethod("testMethod1", Array())
    finder.findTestsInContainer(element, result, new NullProgressMonitor)

    val expected = Set(testClass)
    Assert.assertEquals("wrong tests found", expected, result.asScala.toSet)
  }

  private def getType(fullyQualifiedName: String): IType =
    project.javaProject.findType(fullyQualifiedName)

  private def getResource(absolutePath: String): IResource =
    project.underlying.findMember(absolutePath)

  implicit def stringsArePaths(str: String): Path = new Path(str)
}
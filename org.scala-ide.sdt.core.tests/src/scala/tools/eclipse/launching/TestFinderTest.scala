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
import org.junit.ComparisonFailure

object TestFinderTest extends TestProjectSetup("test-finder")

class TestFinderTest {
  import TestFinderTest._

  @Test
  def project_possibleMatches() {
    val finder = new JUnit4TestFinder

    val possibleMatches = finder.filteredTestResources(project, project.javaProject, new NullProgressMonitor)
    val expected = Set(getResource("/src/packa/TestA.scala"),
      getResource("/src/packa/FakeTest.scala"),
      getResource("/src/packc/TestC.scala"),
      getResource("/src/jpacka/JTestA.java"),
      getResource("/src/jpacka/RunWithTest.java"),
      getResource("/src/packb/TestB.scala"),
      getResource("/src/jpackb/FakeJavaTest.java"),
      getResource("/src/jpackb/JTestB.java"),
      getResource("/src/TestInEmptyPackage.scala"))
    assertEqualsSets("wrong filtered files", expected, possibleMatches.toSet)
  }

  @Test
  def scala_package_possibleMatches() {
    val finder = new JUnit4TestFinder

    val possibleMatches = finder.filteredTestResources(project, project.javaProject.findPackageFragment("/test-finder/src/packa"), new NullProgressMonitor)
    val expected = Set(getResource("/src/packa/TestA.scala"), getResource("/src/packa/FakeTest.scala"))
    assertEqualsSets("wrong filtered files", expected, possibleMatches.toSet)
  }

  @Test
  def scala_package_matches() {
    val finder = new JUnit4TestFinder

    val result = new mutable.HashSet[IType]
    finder.findTestsInContainer(project.javaProject.findPackageFragment("/test-finder/src/packa"), result, new NullProgressMonitor)

    val expected = Set(getType("packa.TestA"), getType("packa.TestA1"))
    assertEqualsSets("wrong tests found", expected, result)
  }

  @Test
  def java_package_matches() {
    val finder = new JUnit4TestFinder

    val result = new java.util.HashSet[IType]
    finder.findTestsInContainer(project.javaProject.findPackageFragment("/test-finder/src/jpacka"), result, new NullProgressMonitor)

    val expected = Set(getType("jpacka.JTestA"), getType("jpacka.RunWithTest"))
    assertEqualsSets("wrong tests found", expected, result.asScala.toSet)
  }

  @Test
  def java_package_matches_inherited() {
    val finder = new JUnit4TestFinder

    val result = new java.util.HashSet[IType]
    finder.findTestsInContainer(project.javaProject.findPackageFragment("/test-finder/src/jpackb"), result, new NullProgressMonitor)

    val expected = Set(getType("jpackb.JTestB"))
    assertEqualsSets("wrong tests found", expected, result.asScala.toSet)
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
      getType("packc.TestC"),
      getType("packc.TestCInherited1"),
      getType("packc.TestCInherited2"),
      getType("TestInEmptyPackage"))
    assertEqualsSets("wrong tests found", expected, result.asScala.toSet)
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
      getType("packc.TestC"),
      getType("packc.TestCInherited1"),
      getType("packc.TestCInherited2"),
      getType("TestInEmptyPackage"),
      getType("packb.TestB"))
    assertEqualsSets("wrong tests found", expected, result.asScala.toSet)
  }

  @Test
  def only_one_type_element_matches() {
    val finder = new JUnit4TestFinder

    val result = new java.util.HashSet[IType]
    val element = getType("packa.TestA")
    finder.findTestsInContainer(element, result, new NullProgressMonitor)

    // there are two types in the same source, we want only the one that we passed to the finder to be returned
    val expected = Set(element)
    assertEqualsSets("wrong tests found", expected, result.asScala.toSet)
  }

  @Test
  def empty_package_matches() {
    val finder = new JUnit4TestFinder

    val result = new java.util.HashSet[IType]
    val element = getType("TestInEmptyPackage")
    finder.findTestsInContainer(element, result, new NullProgressMonitor)

    val expected = Set(element)
    assertEqualsSets("wrong tests found", expected, result.asScala.toSet)
  }

  @Test
  def method_matches() {
    val finder = new JUnit4TestFinder

    val result = new java.util.HashSet[IType]
    val testClass = getType("packa.TestA1")
    val element = testClass.getMethod("testMethod1", Array())
    finder.findTestsInContainer(element, result, new NullProgressMonitor)

    val expected = Set(testClass)
    assertEqualsSets("wrong tests found", expected, result.asScala.toSet)
  }

  @Test
  def search_test_methods_decls() {
    val finder = new JUnit4TestFinder
    val testClass = getType("packa.TestA1")
    val result = finder.getTestMethods(project.javaProject, testClass)
    val expected = Set("testMethod1", "testMethod2")
    assertEqualsSets("wrong test methods", expected, result.asScala)
  }

  @Test
  def search_test_methods_inherited_from_class() {
    val finder = new JUnit4TestFinder
    val testClass = getType("packc.TestC")
    val result = finder.getTestMethods(project.javaProject, testClass)
    val expected = Set("testMethod1", "testMethod2", "derivedTestMethod1")
    assertEqualsSets("wrong test methods", expected, result.asScala)
  }

  @Test
  def search_test_methods_inherited_from_trait() {
    val finder = new JUnit4TestFinder
    val testClass = getType("packc.TestCInherited1")
    val result = finder.getTestMethods(project.javaProject, testClass)
    val expected = Set("traitTestMethod1")
    assertEqualsSets("wrong test methods", expected, result.asScala)
  }

  @Test
  def search_test_methods_inherited_from_abstract_class() {
    val finder = new JUnit4TestFinder
    val testClass = getType("packc.TestCInherited2")
    val result = finder.getTestMethods(project.javaProject, testClass)
    val expected = Set("abstractClassTestMethod1")
    assertEqualsSets("wrong test methods", expected, result.asScala)
  }

  @Test
  def search_test_methods_in_empty_package() {
    val finder = new JUnit4TestFinder
    val testClass = getType("TestInEmptyPackage")
    val result = finder.getTestMethods(project.javaProject, testClass)
    val expected = Set("foo")
    assertEqualsSets("wrong test methods", expected, result.asScala)
  }
  private def getType(fullyQualifiedName: String): IType =
    project.javaProject.findType(fullyQualifiedName)

  private def getResource(absolutePath: String): IResource =
    project.underlying.findMember(absolutePath)

  implicit def stringsArePaths(str: String): Path = new Path(str)

  def assertEqualsSets[T](msg: String, set1: collection.Set[T], set2: collection.Set[T]) = {
    if (set1 != set2) throw new ComparisonFailure(msg, set1.toString, set2.toString)
  }
}
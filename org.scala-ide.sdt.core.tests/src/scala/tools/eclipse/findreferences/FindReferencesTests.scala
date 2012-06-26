package scala.tools.eclipse.findreferences

import scala.tools.eclipse.EclipseUserSimulator
import scala.tools.eclipse.ScalaProject
import scala.tools.eclipse.ScalaWordFinder
import scala.tools.eclipse.javaelements.ScalaClassElement
import scala.tools.eclipse.javaelements.ScalaDefElement
import scala.tools.eclipse.javaelements.ScalaModuleElement
import scala.tools.eclipse.javaelements.ScalaTypeElement
import scala.tools.eclipse.javaelements.ScalaValElement
import scala.tools.eclipse.javaelements.ScalaVarElement
import scala.tools.eclipse.testsetup.FileUtils
import scala.tools.eclipse.testsetup.SDTTestUtils
import scala.tools.eclipse.testsetup.SearchOps
import scala.tools.eclipse.testsetup.TestProjectSetup
import org.eclipse.core.resources.IProject
import org.eclipse.core.runtime.IPath
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.core.runtime.Path
import org.eclipse.jdt.core.IJavaElement
import org.eclipse.jdt.internal.core.SourceType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import scala.tools.eclipse.logging.HasLogger
import org.eclipse.jdt.internal.core.JavaElement
import org.eclipse.core.resources.IResource

@RunWith(classOf[JUnit4])
class FindReferencesTests extends FindReferencesTester with HasLogger {
  private final val TestProjectName = "find-references"

  private val simulator = new EclipseUserSimulator

  private var projectSetup: TestProjectSetup = _

  def project: ScalaProject = projectSetup.project

  @Before
  def createProject() {
    val scalaProject = simulator.createProjectInWorkspace(TestProjectName, withSourceRoot = true)
    projectSetup = new TestProjectSetup(TestProjectName) {
      override lazy val project = scalaProject
    }
  }

  @After
  def deleteProject() {
    project.underlying.delete( /*deleteContent*/ true, /*force*/ true, new NullProgressMonitor)
  }

  def runTest(testProjectName: String, sourceName: String, testDefinition: TestBuilder): Unit = {
    val testWorkspaceLocation = SDTTestUtils.sourceWorkspaceLoc(projectSetup.bundleName)
    val findReferencesTestWorkspace = testWorkspaceLocation.append(new Path(TestProjectName))
    val testProject = findReferencesTestWorkspace.append(testProjectName)

    mirrorContentOf(testProject)

    runTest(sourceName, testDefinition.testMarker, testDefinition.toExpectedTestResult)
  }

  private def mirrorContentOf(sourceProjectLocation: IPath): Unit = {
    val target = project.underlying.getLocation.toFile
    val from = sourceProjectLocation.toFile

    FileUtils.copyDirectory(from, target)

    project.underlying.refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor)
  }

  private def runTest(source: String, marker: String, expected: TestResult): Unit = {
    // Set up
    val unit = projectSetup.scalaCompilationUnit(source)
    // FIXME: This should not be necessary, but if not done then tests randomly fail: 
    //        "scala.tools.nsc.interactive.NoSuchUnitError: no unit found for file XXX"
    projectSetup.reload(unit)
    val offsets = projectSetup.findMarker(marker) in unit

    if (offsets.isEmpty) fail("Test failed for source `%s`. Reason: could not find test marker `%s` in the sourcefile.".format(source, marker))
    else if (offsets.length > 1) fail("Test failed for source `%s`. Reason: only one occurrence of `%s` per test file is allowed".format(source, marker))

    val offset = offsets.head

    val wordRegion = ScalaWordFinder.findWord(unit.getContents, offset)
    val word = new String(unit.getContents.slice(wordRegion.getOffset, wordRegion.getOffset + wordRegion.getLength))

    if (word.trim.isEmpty) fail("No word found at offset: " + offset)

    logger.debug("Searching references of (%s) @ %d".format(word, offset))

    val elements = unit.codeSelect(wordRegion.getOffset, wordRegion.getLength)
    if (elements.isEmpty) fail("cannot find code element for " + word)
    val element = elements(0).asInstanceOf[JavaElement]

    // SUT
    val matches = SearchOps.findReferences(element, wordRegion)

    // verify
    val convertedMatches = matches.map(searchMatch => jdtElement2testElement(searchMatch.getElement().asInstanceOf[JavaElement])).toSet
    val result = TestResult(jdtElement2testElement(element), convertedMatches)
    assertEquals(expected, result)
  }

  private def jdtElement2testElement(e: JavaElement): Element = {
    val testElement: String => Element = e match {
      case e: ScalaDefElement => Method.apply _
      case e: ScalaVarElement => FieldVar.apply _
      case e: ScalaValElement => FieldVal.apply _
      case e: ScalaClassElement => Clazz.apply _
      case e: ScalaTypeElement => TypeAlias.apply _
      case e: ScalaModuleElement => Module.apply _
      case e: SourceType => Clazz.apply _
      case _ =>
        val msg = "Don't know how to convert element `%s` of type `%s`".format(e.getElementName, e.getClass)
        throw new IllegalArgumentException(msg)
    }
    testElement(e.readableName)
  }

  @Test
  def findReferencesOfClassFieldVar_bug1000067_1() {
    val expected = fieldVar("Referred.aVar") isReferencedBy method("Referring.anotherMethod") and method("Referring.yetAnotherMethod")
    runTest("bug1000067_1", "FindReferencesOfClassFieldVar.scala", expected)
  }

  @Test
  def findReferencesOfClassMethod_bug1000067_2() {
    val expected = method("Referred.aMethod") isReferencedBy method("Referring.anotherMethod") and method("Referring.yetAnotherMethod")
    runTest("bug1000067_2", "FindReferencesOfClassMethod.scala", expected)
  }

  @Test
  def findReferencesOfClassFieldVal_bug1000067_3() {
    val expected = fieldVal("Referred.aVal") isReferencedBy method("Referring.anotherMethod") and method("Referring.yetAnotherMethod")
    runTest("bug1000067_3", "FindReferencesOfClassFieldVal.scala", expected)
  }

  @Test
  def findReferencesOfClassConstructor_bug1000063_1() {
    val expected = clazz("ReferredClass") isReferencedBy method("ReferringClass.foo") and method("ReferringClass.bar")
    runTest("bug1000063_1", "FindReferencesOfClassConstructor.scala", expected)
  }

  @Test
  def findReferencesOfClassTypeInMethodTypeBound_bug1000063_2() {
    val expected = clazz("ReferredClass") isReferencedBy clazzConstructor("ReferringClass") and typeAlias("ReferringClass.typedSet") and method("ReferringClass.foo")
    runTest("bug1000063_2", "FindReferencesOfClassType.scala", expected)
  }

  @Test
  def findReferencesOfClassType_bug1001084() {
    val expected = clazz("Foo") isReferencedBy clazz("Bar")
    runTest("bug1001084", "FindReferencesOfClassType.scala", expected)
  }

  @Test
  def findReferencesInsideCompanionObject_ex1() {
    val expected = fieldVal("Foo$.ss") isReferencedBy moduleConstructor("Foo")
    runTest("ex1", "Ex1.scala", expected)
  }
}
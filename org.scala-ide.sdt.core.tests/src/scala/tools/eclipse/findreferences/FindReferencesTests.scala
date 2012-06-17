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

@RunWith(classOf[JUnit4])
class FindReferencesTests extends FindReferencesTester {

  private val simulator = new EclipseUserSimulator

  private var projectSetup: TestProjectSetup = _

  def project: ScalaProject = projectSetup.project

  @Before
  def createProject() {
    val projectName = "find-references"
    val scalaProject = simulator.createProjectInWorkspace(projectName, withSourceRoot = true)
    projectSetup = new TestProjectSetup(projectName) {
      override lazy val project = scalaProject
    }
  }

  @After
  def deleteProject() {
    project.underlying.delete( /*deleteContent*/ true, /*force*/ true, new NullProgressMonitor)
  }

  def runTest(projectFolderName: String, sourceName: String, testDefinition: TestBuilder): Unit = {
    val testProjectSrcFolder = SDTTestUtils.sourceWorkspaceLoc(projectSetup.bundleName).append(new Path("find-references").append(projectFolderName)).append("src")

    copyAllInSrcFolder(project.underlying, testProjectSrcFolder)

    runTest(sourceName, testDefinition.testMarker, testDefinition.toExpectedTestResult)
  }

  private def copyAllInSrcFolder(project: IProject, from: IPath): Unit = {
    for (file <- from.toFile.listFiles) {
      val filePath = file.getAbsolutePath
      val relativeFilePath = new Path(filePath).makeRelativeTo(from)
      SDTTestUtils.addFileToProject(project, new Path("src").append(relativeFilePath).toPortableString, FileUtils.read(file))
    }
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

    println("Searching references of (%s) @ %d".format(word, offset))

    val elements = unit.codeSelect(wordRegion.getOffset, wordRegion.getLength)
    if (elements.isEmpty) fail("cannot find code element for " + word)
    val element = elements(0)

    // SUT
    val matches = SearchOps.findReferences(element, wordRegion)

    // verify
    val convertedMatches = matches.map(searchMatch => jdtElement2testElement(searchMatch.getElement().asInstanceOf[IJavaElement])).toSet
    val result = TestResult(jdtElement2testElement(element), convertedMatches)
    assertEquals(expected, result)
  }

  private def jdtElement2testElement(e: IJavaElement): Element = e match {
    case e: ScalaDefElement => Method(e.getElementName)
    case e: ScalaVarElement => FieldVar(e.getElementName)
    case e: ScalaValElement => FieldVal(e.getElementName)
    case e: ScalaClassElement => Clazz(e.getElementName)
    case e: ScalaTypeElement => TypeAlias(e.getElementName)
    case e: ScalaModuleElement => Module(e.getElementName)
    case e: SourceType => Clazz(e.getElementName)
    case _ =>
      val msg = "Don't know how to convert element `%s` of type `%s`".format(e.getElementName, e.getClass)
      throw new IllegalArgumentException(msg)
  }

  @Test
  def findReferencesOfClassFieldVar_bug1000067_1() {
    val expected = fieldVar("aVar") isReferencedBy method("anotherMethod") and method("yetAnotherMethod")
    runTest("bug1000067_1", "FindReferencesOfClassFieldVar.scala", expected)
  }

  @Test
  def findReferencesOfClassMethod_bug1000067_2() {
    val expected = method("aMethod") isReferencedBy method("anotherMethod") and method("yetAnotherMethod")
    runTest("bug1000067_2", "FindReferencesOfClassMethod.scala", expected)
  }

  @Test
  def findReferencesOfClassFieldVal_bug1000067_3() {
    val expected = fieldVal("aVal") isReferencedBy method("anotherMethod") and method("yetAnotherMethod")
    runTest("bug1000067_3", "FindReferencesOfClassFieldVal.scala", expected)
  }
}
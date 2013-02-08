package scala.tools.eclipse.semantichighlighting

import scala.collection.immutable
import scala.tools.eclipse.EclipseUserSimulator
import scala.tools.eclipse.InteractiveCompilationUnit
import scala.tools.eclipse.ScalaPlugin
import scala.tools.eclipse.ScalaProject
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.tools.eclipse.properties.syntaxcolouring.ScalaSyntaxClasses
import scala.tools.eclipse.semantichighlighting.classifier.SymbolInfo
import scala.tools.eclipse.ui.InteractiveCompilationUnitEditor
import scala.tools.eclipse.util.EclipseUtils
import scala.util.matching.Regex
import scala.util.matching.Regex.Match
import org.eclipse.core.internal.filebuffers.SynchronizableDocument
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.core.runtime.Status
import org.eclipse.core.runtime.jobs.Job
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.source.ISourceViewer
import org.junit.{ Before, Test }
import org.junit.After
import org.junit.Assert
import org.mockito.Mockito._
import scala.tools.eclipse.util.CurrentThread
import scala.tools.eclipse.semantichighlighting.classifier.SymbolTypes
import org.eclipse.jface.text.Region

class SemanticHighlightingPositionsTest {
  import SemanticHighlightingPositionsTest._

  private val MarkerRegex: Regex = """/\*\^\*/""".r
  private val Marker = "/*^*/"

  protected val simulator = new EclipseUserSimulator
  private var project: ScalaProject = _

  private var sourceView: ISourceViewer = _
  private var document: IDocument = _

  private val preferences: Preferences = {
    val store = mock(classOf[IPreferenceStore])
    when(store.getBoolean(ScalaSyntaxClasses.USE_SYNTACTIC_HINTS)).thenReturn(true)
    new Preferences(store)
  }

  private var editor: TextPresentationStub = _
  private var presenter: Presenter = _

  private var testCode: String = _
  private var unit: ScalaCompilationUnit = _

  @Before
  def createProject() {
    project = simulator.createProjectInWorkspace("semantic-highlighting-positions-update", true)
  }

  @After
  def deleteProject() {
    EclipseUtils.workspaceRunnableIn(ScalaPlugin.plugin.workspaceRoot.getWorkspace) { _ =>
      project.underlying.delete(true, null)
    }
  }

  @Before
  def setupMocks() {
    sourceView = mock(classOf[ISourceViewer])
    document = mock(classOf[IDocument])
    when(sourceView.getDocument()).thenReturn(document)
  }

  trait Edit {
    def newText: String = ""
    def newPositions: List[Position] = Nil
  }
  case class AddText(override val newText: String, override val newPositions: List[Position] = Nil) extends Edit
  case object RemoveText extends Edit

  private def setTestCode(code: String): Unit = {
    testCode = code.stripMargin
    val emptyPkg = simulator.createPackage("")
    unit = simulator.createCompilationUnit(emptyPkg, "A.scala", testCode).asInstanceOf[ScalaCompilationUnit]
    document.set(testCode)
  }

  private def createSemanticHighlightingPresenter(): Unit = {
    editor = TextPresentationStub(sourceView)
    presenter = new Presenter(unit, editor, preferences, CurrentThread)
    presenter.initialize(forceRefresh = false)
  }

  private def positionsInRegion(region: Region): List[Position] = {
    val positions = editor.positionsTracker.positionsInRegion(region)
    // We clone the `highlightedPositions` because the semantic highlighting component works by 
    // performing side-effect on the document's positions each time the reconciler is triggered.
    positions.map(pos => new Position(pos.getOffset, pos.getLength, pos.kind, pos.deprecated))
  }

  private def findAllMarkersIn(code: String): List[Match] =
    MarkerRegex.findAllIn(code).matchData.toList

  private def runTest(edit: Edit)(code: String): Unit = {
    setTestCode(code)

    createSemanticHighlightingPresenter()

    val highlightedBeforeEdition = positionsInRegion(new Region(0, code.length))

    val markers = findAllMarkersIn(testCode)

    val (start, end) = markers.size match {
      case 1    => (markers.head.start, markers.head.end)
      case 2    => (markers.head.start, markers.last.end)
      case size => throw new AssertionError("Unsupported test definition. Found %d occurrences of %s in test code:\n%s".format(size, Marker, testCode))
    }
    val length = end - start

    val positionShift = edit.newText.length - length
    val expectedHighlightedPositionsAfterEdition: List[Position] = {
      // collect all positions in the document that are expected to still be highlighted after the edition
      val positions = highlightedBeforeEdition.filterNot { _.overlapsWith(start, length) }
      // of the above positions, shift the ones that are affected by the edition
      val shiftedPositions = positions.map { pos =>
        if (pos.getOffset() >= start) pos.setOffset(pos.getOffset() + positionShift)
        pos
      }
      // merge together the shifted positions and any additional position that is expected to be created by the edit
      val merged = (edit.newPositions ++ positions)
      // Sort the position. This is needed because position's added to the document are expected to be sorted. 
      val sorted = merged.sorted
      sorted
    }

    // perform edit
    unit.getBuffer().replace(start, length, edit.newText)

    // checks edit's postcondition
    val currentTestCode = unit.getContents.mkString
    if (findAllMarkersIn(currentTestCode).nonEmpty)
      throw new AssertionError("After edition, no marker `%s` should be present in test code:\n%s".format(Marker, currentTestCode))

    // This will trigger semantic highlighting computation, which in turn will update the document's positions (sequential execution!) 
    editor.reconcileNow()

    val highlightedAfterEdition = positionsInRegion(new Region(0, currentTestCode.length))

    Assert.assertEquals(expectedHighlightedPositionsAfterEdition.size, highlightedAfterEdition.size)
    Assert.assertEquals(expectedHighlightedPositionsAfterEdition, highlightedAfterEdition)
  }

  @Test
  def highlighted_positions_not_affected_by_edition() {
    runTest(AddText("\n\n")) {
      """
        |class A {
        |  def foo(): Unit = {}
        |  /*^*/
        |}
      """
    }
  }

  @Test
  def existing_highlighted_positions_are_shifted() {
    runTest(AddText("\n\n")) {
      """
        |class A {
        |  /*^*/
        |  def foo(): Unit = {}
        |}
      """
    }
  }

  @Test
  def new_highlighted_positions_are_reported() {
    runTest(AddText("val bar = 2", List(new Position(17, 3, SymbolTypes.TemplateVal, deprecated = false)))) {
      """
        |class A {
        |  /*^*/
        |  def foo(): Unit = {}
        |}
      """
    }
  }

  @Test
  def highlighted_positions_in_the_document_are_removed_on_deletion() {
    runTest(RemoveText) {
      """
        |class A {
        |  /*^*/
        |  def foo(): Unit = {}
        |  /*^*/
        |}
      """
    }

  }

  @Test
  def highlighted_positions_around_deletion_action_are_correct() {
    runTest(RemoveText) {
      """
        |class A {
        |/*^*/
        |  if (true)
        |/*^*/    println("abc")
        |}
      """
    }
  }
}

object SemanticHighlightingPositionsTest {

  class TextPresentationStub(override val sourceViewer: ISourceViewer) extends TextPresentationHighlighter {
    @volatile private var reconciler: Job = _
    @volatile var positionsTracker: PositionsTracker = _

    override def initialize(reconciler: Job, positionsTracker: PositionsTracker): Unit = {
      this.reconciler = reconciler
      this.positionsTracker = positionsTracker
      reconcileNow()
    }

    override def dispose(): Unit = ()

    def reconcileNow(): Unit = {
      // `Job.run` is protected, but when we subclass it in `Presenter$Reconciler` we make the `run` method public, which is really useful for running the reconciler within the same thread of the test.
      reconciler.asInstanceOf[{ def run(monitor: IProgressMonitor): IStatus }].run(new NullProgressMonitor)
    }

    override def updateTextPresentation(damage: IRegion): Unit = ()
  }

  object TextPresentationStub {
    def apply(sourceViewer: ISourceViewer): TextPresentationStub = new TextPresentationStub(sourceViewer)
  }
}
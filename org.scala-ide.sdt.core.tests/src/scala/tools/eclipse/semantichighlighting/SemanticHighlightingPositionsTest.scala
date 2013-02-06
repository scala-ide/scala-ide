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
import org.eclipse.jface.text.Position
import org.eclipse.jface.text.source.ISourceViewer
import org.junit.{ Before, Test }
import org.junit.After
import org.junit.Assert
import org.mockito.Mockito._

class SemanticHighlightingPositionsTest {
  import SemanticHighlightingPositionsTest._

  private val MarkerRegex: Regex = """/\*\^\*/""".r
  private val Marker = "/*^*/"

  protected val simulator = new EclipseUserSimulator
  private var project: ScalaProject = _

  private var sourceView: ISourceViewer = _
  private var document: IDocument = _

  private var preferences: Preferences = _

  private var testCode: String = _
  private var unit: ScalaCompilationUnit = _

  private var editor: EditorStub = _
  private var presenter: Presenter = _

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
    document = new SynchronizableDocument
    when(sourceView.getDocument()).thenReturn(document)

    val store = mock(classOf[IPreferenceStore])
    when(store.getBoolean(ScalaSyntaxClasses.USE_SYNTACTIC_HINTS)).thenReturn(true)
    preferences = new Preferences(store)
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
    editor = EditorStub(sourceView)
    presenter = new Presenter(unit, editor, PositionFactory(preferences), preferences)
    presenter.initialize()
  }

  private def currentlyHighlightedPositionsInDocument(): Array[Position] = {
    val highlighedPositions = document.getPositions(editor.positionCategory)
    // We clone the `highlightedPositions` because the semantic highlighting component works by 
    // performing side-effect on the document's positions each time the reconciler is triggered.
    highlighedPositions.map(pos => new Position(pos.getOffset(), pos.getLength()))
  }
  
  private def findAllMarkersIn(code: String): List[Match] = 
    MarkerRegex.findAllIn(code).matchData.toList

  private def runTest(edit: Edit)(code: String): Unit = {
    setTestCode(code)

    createSemanticHighlightingPresenter()

    val highlightedBeforeEdition = currentlyHighlightedPositionsInDocument()

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
      val sorted = merged.sorted(Presenter.PositionsByOffset)
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

    val highlightedAfterEdition = currentlyHighlightedPositionsInDocument().toList

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
    runTest(AddText("val bar = 2", List(new Position(17, 3)))) {
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

  class EditorStub(override val sourceViewer: ISourceViewer) extends TextPresentationHighlighter {
    @volatile private var reconciler: Job = _
    @volatile var positionCategory: String = _

    override def initialize(reconciler: Job, positionCategory: String): Unit = {
      this.reconciler = reconciler
      this.positionCategory = positionCategory
      reconcileNow()
    }

    override def dispose(): Unit = {}

    def reconcileNow(): Unit = {
      // `Job.run` is protected, but when we subclass it in `Presenter$Reconciler` we make the `run` method public, which is really useful for running the reconciler within the same thread of the test.
      reconciler.asInstanceOf[{ def run(monitor: IProgressMonitor): IStatus }].run(new NullProgressMonitor)
      // Sleeping to give some time to the `DocumentPositions` actor to add/remove positions to the document.   
      Thread.sleep(200)
    }

    override def updateTextPresentation(positionsChange: DocumentPositionsChange): Unit = ()
  }

  object EditorStub {
    def apply(sourceViewer: ISourceViewer): EditorStub = new EditorStub(sourceViewer)
  }

  class PositionFactory(preferences: Preferences) extends (List[SymbolInfo] => immutable.HashSet[Position]) {
    def apply(symbolInfos: List[SymbolInfo]): immutable.HashSet[Position] = {
      // FIXME: This is duplicated logic, should be reusing HighlightedPosition, but it can't at the moment because it contains some UI logic...
      (for {
        SymbolInfo(symbolType, regions, isDeprecated) <- symbolInfos
        region <- regions
        if region.getLength > 0
      } yield new Position(region.getOffset, region.getLength))(collection.breakOut)
    }
  }

  object PositionFactory {
    def apply(implicit preferences: Preferences): PositionFactory = new PositionFactory(preferences)
  }
}
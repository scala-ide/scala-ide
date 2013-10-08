package scala.tools.eclipse.semantichighlighting

import scala.tools.eclipse.EclipseUserSimulator
import scala.tools.eclipse.ScalaPlugin
import scala.tools.eclipse.ScalaProject
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.tools.eclipse.jface.text.EmptyRegion
import scala.tools.eclipse.properties.syntaxcolouring.ScalaSyntaxClasses
import scala.tools.eclipse.semantichighlighting.classifier.SymbolTypes
import scala.tools.eclipse.ui.InteractiveCompilationUnitEditor
import scala.tools.eclipse.util.CurrentThread
import scala.tools.eclipse.util.EclipseUtils
import scala.util.matching.Regex
import scala.util.matching.Regex.Match

import org.eclipse.core.internal.filebuffers.SynchronizableDocument
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.core.runtime.jobs.Job
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.Region
import org.eclipse.jface.text.source.ISourceViewer
import org.junit.Before
import org.junit.Test
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

  private val preferences: Preferences = {
    val store = mock(classOf[IPreferenceStore])
    when(store.getBoolean(ScalaSyntaxClasses.USE_SYNTACTIC_HINTS)).thenReturn(true)
    new Preferences(store)
  }

  private var editor: TextPresentationStub = _
  private var presenter: Presenter = _

  private var testCode: String = _
  private var unit: ScalaCompilationUnit = _
  private val compilationUnitEditor: InteractiveCompilationUnitEditor =
    mock(classOf[InteractiveCompilationUnitEditor])

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
    when(compilationUnitEditor.getInteractiveCompilationUnit).thenReturn(unit)
    document.set(testCode)
  }

  private def createSemanticHighlightingPresenter(): Unit = {
    editor = TextPresentationStub(sourceView)
    presenter = new Presenter(compilationUnitEditor, editor, preferences, CurrentThread)
    presenter.initialize(forceRefresh = false)
  }

  private def positionsInRegion(region: Region): List[Position] = {
    val positions = editor.positionsTracker.positionsInRegion(region).filterNot(_.isDeleted())
    // We clone the `positions` because the semantic highlighting component works by performing side-effects
    // on the existing positions. And, the `runTest`, expects the returned positions to not change.
    positions.map(pos => new Position(pos.getOffset, pos.getLength, pos.kind, pos.deprecated, pos.inInterpolatedString)).toList
  }

  private def findAllMarkersIn(code: String): List[Match] =
    MarkerRegex.findAllIn(code).matchData.toList

  /** Test that semantic highlighting positions are correctly created for the passed `code` before
    * and after performing the `edit`. It also checks that the damaged region caused by the `edit`
    * is the smallest contiguous region that includes all positions affected by the `edit`.
    *
    * The passed `code` should contain at least one, and at most two, `Marker`.
    *
    * - One `Marker`: the new text carried by the `edit` should replace the `Marker` in
    *                the passed `code`.
    *
    * - Two `Marker`'s: the new text carried by the `edit` should replace the whole text contained
    *                  within the two `Marker`s in the passed `code`.
    *
    * @param edit The edit action to perform on the passed `code`.
    * @param code The test code.
    */
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
      positions.foreach { pos =>
        // mutate position offsets to shift them
        if (pos.getOffset() >= start) pos.setOffset(pos.getOffset() + positionShift)
      }
      // merge together the shifted positions and any additional position that is expected to be created by the edit
      val merged = (edit.newPositions ++ positions)
      // Sort the position. This is needed because position's added to the document are expected to be sorted.
      val sorted = merged.sorted
      sorted
    }

    def editTestCode(offset: Int, length: Int, newText: String): Unit = {
      document.replace(offset, length, edit.newText) // triggers the IUpdatePosition listener
      unit.getBuffer().replace(offset, length, edit.newText) // needed by the semantic highlighting reconciler
    }

    // perform edit
    editTestCode(start, length, edit.newText)

    // checks edit's postcondition
    val currentTestCode = unit.getContents.mkString
    if (findAllMarkersIn(currentTestCode).nonEmpty)
      throw new AssertionError("After edition, no marker `%s` should be present in test code:\n%s".format(Marker, currentTestCode))

    // This will trigger semantic highlighting computation, which in turn will update the document's positions (sequential execution!)
    editor.reconcileNow()

    val newPositions = positionsInRegion(new Region(start, edit.newText.length()))
    val affectedRegion = PositionsChange(newPositions, Nil).affectedRegion()

    val highlightedAfterEdition = positionsInRegion(new Region(0, currentTestCode.length))

    Assert.assertEquals("Wrong start of damaged region", affectedRegion.getOffset(), editor.damagedRegion.getOffset())
    Assert.assertEquals("Wrong length of damaged region", affectedRegion.getLength(), editor.damagedRegion.getLength())
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
    runTest(AddText("val bar = 2", List(new Position(17, 3, SymbolTypes.TemplateVal, deprecated = false, inInterpolatedString = false)))) {
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

  @Test
  def correctly_compute_damagedRegion_whenDeletingText() {
    runTest(RemoveText) {
      """
        |object A {
        |  /*^*/def f = 0
        |  /*^*/class C
        |}
      """
    }
  }
}

object SemanticHighlightingPositionsTest {

  class TextPresentationStub(override val sourceViewer: ISourceViewer) extends TextPresentationHighlighter {

    import scala.language.reflectiveCalls

    @volatile private var reconciler: Job = _
    @volatile var positionsTracker: PositionsTracker = _
    @volatile var damagedRegion: IRegion = _

    override def initialize(reconciler: Job, positionsTracker: PositionsTracker): Unit = {
      this.reconciler = reconciler
      this.positionsTracker = positionsTracker
      reconcileNow()
    }

    override def dispose(): Unit = ()

    def reconcileNow(): Unit = {
      damagedRegion = EmptyRegion
      // `Job.run` is protected, but when we subclass it in `Presenter$Reconciler` we make the `run` method public, which is really useful for running the reconciler within the same thread of the test.
      reconciler.asInstanceOf[{ def run(monitor: IProgressMonitor): IStatus }].run(new NullProgressMonitor)
    }

    override def updateTextPresentation(damage: IRegion): Unit = damagedRegion = damage
  }

  object TextPresentationStub {
    def apply(sourceViewer: ISourceViewer): TextPresentationStub = new TextPresentationStub(sourceViewer)
  }
}
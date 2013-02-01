package scala.tools.eclipse.semantichighlighting

import scala.collection.immutable
import scala.tools.eclipse.InteractiveCompilationUnit
import scala.tools.eclipse.semantichighlighting.classifier.SymbolInfo
import scala.tools.eclipse.testsetup.TestProjectSetup
import scala.tools.eclipse.ui.InteractiveCompilationUnitEditor
import org.eclipse.core.internal.filebuffers.SynchronizableDocument
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.Status
import org.eclipse.core.runtime.jobs.Job
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.Position
import org.eclipse.jface.text.source.ISourceViewer
import org.junit.{ Before, Test }
import org.mockito.Mockito._
import scala.tools.eclipse.ScalaPlugin
import org.junit.Assert
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.NullProgressMonitor
import scala.tools.eclipse.util.EclipseUtils
import org.junit.After
import scala.tools.eclipse.ScalaProject
import scala.tools.eclipse.EclipseUserSimulator
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import org.junit.BeforeClass
import org.junit.AfterClass
import org.eclipse.jdt.core.IBuffer

class SemanticHighlightingPositionsTest {
  import SemanticHighlightingPositionsTest._

  private val MarkerChar = '^'
  private val Marker = "/*" + MarkerChar + "*/"

  protected val simulator = new EclipseUserSimulator
  private var project: ScalaProject = _

  private var sourceView: ISourceViewer = _
  private var document: IDocument = _

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
    editor = EditorStub(unit, sourceView)
    presenter = new Presenter(editor, PositionFactory(PreferencesStub), PreferencesStub)
    presenter.initialize()
  }

  private def currentlyHighlightedPositionsInDocument(): Array[Position] = {
    val highlighedPositions = document.getPositions(editor.positionCategory)
    // We clone the `highlightedPositions` because the semantic highlighting component works by 
    // performing side-effect on the document's positions each time the reconciler is triggered.
    highlighedPositions.map(pos => new Position(pos.getOffset(), pos.getLength()))
  }

  private def runTest(edit: Edit)(code: String): Unit = {
    setTestCode(code)

    createSemanticHighlightingPresenter()

    val highlightedBeforeEdition = currentlyHighlightedPositionsInDocument()

    val markerOccurrences = testCode.count(_ == MarkerChar)

    val offset = testCode.indexOf(Marker) // position of the first marker
    val length: Int = {
      if (markerOccurrences == 1) { // we are just adding some text at the offset delimited by the marker
        Marker.length
      } 
      else if (markerOccurrences == 2) { // we are deleting the text that is in-between the markers (included the markers)
        val to = testCode.indexOf(Marker, offset + Marker.length) + Marker.length
        to - offset
      } // sanity check 
      else throw new AssertionError("Unsupported test definition. Found %d occurrences of %s in test code:\n%s".format(markerOccurrences, Marker, testCode))
    }

    val positionShift = edit.newText.length - length
    val expectedHighlightedPositionsAfterEdition = {
      // collect all positions in the document that are expected to still be highlighted after the edition
      val positions = highlightedBeforeEdition.filterNot { _.overlapsWith(offset, length) }
      // of the above positions, shift the ones that are affected by the edition
      val shiftedPositions = positions.map { pos =>
        if (pos.getOffset() >= offset) pos.setOffset(pos.getOffset() + positionShift)
        pos
      }
      // merge together the shifted positions and any additional position that is expected to be created by the test
      val merged = (edit.newPositions  ++ positions)
      // Sort the position. This is needed because position's added to the document are expected to be sorted. 
      val sorted = merged.sorted(Presenter.PositionsByOffset)
      sorted
    }

    // perform edition
    unit.getBuffer().replace(offset, length, edit.newText)
    
    // sanity check
    val currentTestCode = unit.getContents.mkString
    if (currentTestCode.count(_ == MarkerChar) != 0)
      throw new AssertionError("After edition, no marker `%s` should be present in test code:\n%s".format(MarkerChar, currentTestCode))

    // this will trigger semantic highlighting computation, which in turn will update the document's positions
    editor.reconcileNow()

    val highlightedAfterEdition = currentlyHighlightedPositionsInDocument()

    Assert.assertEquals(expectedHighlightedPositionsAfterEdition.size, highlightedAfterEdition.size)
    Assert.assertEquals(expectedHighlightedPositionsAfterEdition.toList, highlightedAfterEdition.toList)
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

  class EditorStub(override val getInteractiveCompilationUnit: InteractiveCompilationUnit, override val sourceViewer: ISourceViewer) extends TextPresentationProxy with InteractiveCompilationUnitEditor {
    @volatile private var reconciler: Job = _
    @volatile var positionCategory: String = _

    override def initialize(reconciler: Job, positionCategory: String): Unit = {
      this.reconciler = reconciler
      this.positionCategory = positionCategory
      reconcileNow()
    }
    override def dispose(): Unit = {}

    override def updateTextPresentation(documentProxy: DocumentProxy, positionsChange: DocumentProxy#DocumentPositionsChange, damage: IRegion): IStatus = {
      println(damage)
      super.updateTextPresentation(documentProxy, positionsChange, damage)
    }

    def reconcileNow(): Unit = {
      reconciler.asInstanceOf[{ def run(monitor: IProgressMonitor): IStatus }].run(new NullProgressMonitor)
    }
  }

  object EditorStub {
    def apply(unit: InteractiveCompilationUnit, sourceViewer: ISourceViewer): EditorStub = new EditorStub(unit, sourceViewer)
  }

  implicit object PreferencesStub extends Preferences {
    override def isEnabled(): Boolean = true
    override def isStrikethroughDeprecatedDecorationEnabled(): Boolean = true
    override def isUseSyntacticHintsEnabled(): Boolean = true

    override def store: IPreferenceStore = null
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
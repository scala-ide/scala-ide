package scala.tools.eclipse
package ui

import completion._
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension6
import org.eclipse.jface.text.contentassist.IContextInformation
import org.eclipse.swt.graphics.Image
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.viewers.ISelectionProvider
import org.eclipse.jface.viewers.StyledString
import org.eclipse.jface.text.TextSelection
import org.eclipse.jface.text.ITextViewer
import org.eclipse.jdt.internal.ui.JavaPluginImages
import refactoring.EditorHelpers
import refactoring.EditorHelpers._
import scala.tools.refactoring.implementations.AddImportStatement
import org.eclipse.jface.text.link._
import org.eclipse.jface.text.Position
import org.eclipse.ui.texteditor.link.EditorLinkedModeUI
import org.eclipse.jdt.internal.ui.text.java.AbstractJavaCompletionProposal.ExitPolicy
import org.eclipse.jface.text.link.LinkedModeUI.IExitPolicy
import org.eclipse.swt.events.VerifyEvent
import org.eclipse.jface.text.link.LinkedModeUI.ExitFlags
import org.eclipse.swt.SWT
import scala.tools.refactoring.common.TextChange
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension2
import org.eclipse.jdt.internal.ui.JavaPlugin
import org.eclipse.jdt.ui.PreferenceConstants
import org.eclipse.jface.preference.PreferenceConverter
import org.eclipse.swt.graphics.Color
import org.eclipse.jface.text.ITextViewerExtension4
import org.eclipse.jface.text.TextPresentation
import org.eclipse.swt.custom.StyleRange
import org.eclipse.jface.text.ITextPresentationListener
import org.eclipse.jface.text.ITextViewerExtension2
import org.eclipse.jface.text.ITextViewerExtension5
import org.eclipse.jface.text.DocumentEvent
import org.eclipse.jface.text.IRegion

/** A UI class for displaying completion proposals.
 *
 *  It adds parenthesis at the end of a proposal if it has parameters, and places the caret
 *  between them.
 */
class ScalaCompletionProposal(proposal: CompletionProposal, selectionProvider: ISelectionProvider)
  extends IJavaCompletionProposal
  with ICompletionProposalExtension
  with ICompletionProposalExtension2
  with ICompletionProposalExtension6 {

  import proposal._
  import ScalaCompletionProposal._

  private var cachedStyleRange: StyleRange = null
  private val ScalaProposalCategory = "ScalaProposal"

  def getRelevance = relevance

  private lazy val image = {
    import MemberKind._

    kind match {
      case Def           => defImage
      case Class         => classImage
      case Trait         => traitImage
      case Package       => packageImage
      case PackageObject => packageObjectImage
      case Object =>
        if (isJava) javaClassImage
        else objectImage
      case Type => typeImage
      case _    => valImage
    }
  }

  def getImage = image

  /** `getParamNames` is expensive, save this result once computed.
   *
   *  @note This field should be lazy to avoid unnecessary computation.
   */
  private lazy val explicitParamNames = getParamNames()

  /** The string that will be inserted in the document if this proposal is chosen.
   *  By default, it consists of the method name, followed by all explicit parameter sections,
   *  and inside each section the parameter names, delimited by commas. If `overwrite`
   *  is on, it won't add parameter names
   *
   *  @note It triggers the potentially expensive `getParameterNames` operation.
   */
  def completionString(overwrite: Boolean) =
    if (explicitParamNames.isEmpty || overwrite)
      completion
    else {
      val buffer = new StringBuffer(completion)

      for (section <- explicitParamNames)
        buffer.append(section.mkString("(", ", ", ")"))
      buffer.toString
    }

  /** Position after the opening parenthesis of this proposal */
  val startOfArgumentList = startPos + completion.length + 1

  /** The information that is displayed in a small hover window above the completion, showing parameter names and types. */
  def getContextInformation(): IContextInformation =
    if (tooltip.length > 0)
      new ScalaContextInformation(display, tooltip, image, startOfArgumentList)
    else null

  /** A simple display string
   */
  def getDisplayString() = display

  /** A display string with grayed out extra details
   */
  def getStyledDisplayString(): StyledString = {
    val styledString = new StyledString(display)
    if (displayDetail != null && displayDetail.length > 0)
      styledString.append(" - ", StyledString.QUALIFIER_STYLER).append(displayDetail, StyledString.QUALIFIER_STYLER)
    styledString
  }

  /** Some additional info (like javadoc ...)
   */
  def getAdditionalProposalInfo() = null
  def getSelection(d: IDocument) = null
  def apply(d: IDocument) { throw new IllegalStateException("Shouldn't be called") }

  def apply(d: IDocument, trigger: Char, offset: Int) {
    throw new IllegalStateException("Shouldn't be called")
  }

  def apply(viewer: ITextViewer, trigger: Char, stateMask: Int, offset: Int): Unit = {
    val d: IDocument = viewer.getDocument()
    val overwrite = !insertCompletion ^ ((stateMask & SWT.CTRL) != 0)

    val completionFullString = completionString(overwrite)
    val importSize = withScalaFileAndSelection { (scalaSourceFile, textSelection) =>

      val completionChange = scalaSourceFile.withSourceFile { (sourceFile, _) =>
        val endPos = if (overwrite) startPos + existingIdentifier(d, offset).getLength() else offset
        TextChange(sourceFile, startPos, endPos, completionFullString)
      }

      val importStmt =
        if (needImport) { // add an import statement if required
          scalaSourceFile.withSourceFile { (_, compiler) =>
            val refactoring = new AddImportStatement { val global = compiler }
            refactoring.addImport(scalaSourceFile.file, fullyQualifiedName)
          } getOrElse (Nil)
        } else Nil

      // Apply the two changes in one step, if done separately we would need an
      // another `waitLoadedType` to update the positions for the refactoring
      // to work properly.
      EditorHelpers.applyChangesToFileWhileKeepingSelection(
        d, textSelection, scalaSourceFile.file, completionChange.toList ::: importStmt)

      importStmt.headOption.map(_.text.length)
    }

    if (!overwrite) selectionProvider match {
      case viewer: ITextViewer if explicitParamNames.flatten.nonEmpty =>
        addArgumentTemplates(d, viewer, completionFullString)
      case _ => ()
    }
    else
      EditorHelpers.doWithCurrentEditor { editor =>
        editor.selectAndReveal(startPos + completionFullString.length() + importSize.getOrElse(0), 0)
    }
  }

  def getTriggerCharacters = null
  def getContextInformationPosition = startOfArgumentList

  def isValidFor(d: IDocument, pos: Int) =
    prefixMatches(completion.toArray, d.get.substring(startPos, pos).toArray)

  /** Insert a completion proposal, with placeholders for each explicit argument.
   *  For each argument, it inserts its name, and puts the editor in linked mode.
   *  This means that TAB can be used to navigate to the next argument, and Enter or Esc
   *  can be used to exit this mode.
   */
  def addArgumentTemplates(document: IDocument, textViewer: ITextViewer, completionFullString: String) {
    val model = new LinkedModeModel()

    document.addPositionCategory(ScalaProposalCategory)
    var offset = startPos + completion.length

    for (section <- explicitParamNames) {
      offset += 1 // open parenthesis
      var idx = 0 // the index of the current argument
      for (proposal <- section) {
        val group = new LinkedPositionGroup()
        val positionOffset = offset + 2 * idx // each argument is followed by ", "
        val positionLength = proposal.length
        offset += positionLength

        document.addPosition(ScalaProposalCategory, new Position(positionOffset, positionLength))
        group.addPosition(new LinkedPosition(document, positionOffset, positionLength, LinkedPositionGroup.NO_STOP))
        model.addGroup(group)
        idx += 1
      }
      offset += 1 + 2 * (idx - 1) // close parenthesis around section (and the last argument isn't followed by comma and space)
    }

    model.addLinkingListener(new ILinkedModeListener() {
      def left(environment: LinkedModeModel, flags: Int) {
        document.removePositionCategory(ScalaProposalCategory)
      }

      def suspend(environment: LinkedModeModel) {}
      def resume(environment: LinkedModeModel, flags: Int) {}
    })

    model.forceInstall()

    val ui = mkEditorLinkedMode(document, textViewer, model, completionFullString.length)
    ui.enter()
  }

  /** Prepare a linked mode for the given editor. */
  private def mkEditorLinkedMode(document: IDocument, textViewer: ITextViewer, model: LinkedModeModel, len: Int): EditorLinkedModeUI = {
    val ui = new EditorLinkedModeUI(model, textViewer)
    ui.setExitPosition(textViewer, startPos + len, 0, Integer.MAX_VALUE)
    ui.setExitPolicy(new IExitPolicy {
      def doExit(environment: LinkedModeModel, event: VerifyEvent, offset: Int, length: Int) = {
        event.character match {
          case ';' =>
            // go to the end of the completion proposal
            new ExitFlags(ILinkedModeListener.UPDATE_CARET, !environment.anyPositionContains(offset))

          case SWT.CR if (offset > 0 && document.getChar(offset - 1) == '{') =>
            // if we hit enter after opening a brace, it's probably a closure. Exit linked mode
            new ExitFlags(ILinkedModeListener.EXIT_ALL, true)

          case _ =>
            // stay in linked mode otherwise
            null
        }
      }
    })
    ui.setCyclingMode(LinkedModeUI.CYCLE_WHEN_NO_PARENT)
    ui.setDoContextInfo(true)
    ui
  }

  /** Highlight the part of the text that would be overwritten by the current selection
   */
  override def selected(viewer: ITextViewer, smartToggle: Boolean) {
    repairPresentation(viewer)
    if (!insertCompletion() ^ smartToggle) {
      cachedStyleRange = createStyleRange(viewer)
      if (cachedStyleRange == null)
        return

      viewer match {
        case viewerExtension4: ITextViewerExtension4 =>
          this.viewer = viewer
          viewerExtension4.addTextPresentationListener(presListener)
        case _ => ()
      }
      repairPresentation(viewer)
    }
  }

  override def unselected(viewer: ITextViewer) {
    viewer.asInstanceOf[ITextViewerExtension4].removeTextPresentationListener(presListener)
    repairPresentation(viewer)
    cachedStyleRange = null
  }

  private def repairPresentation(viewer: ITextViewer) {
    if (cachedStyleRange != null) viewer match {
      case viewer2: ITextViewerExtension2 =>
        // attempts to reduce the redraw area
        viewer2.invalidateTextPresentation(cachedStyleRange.start, cachedStyleRange.length)
      case _ =>
        viewer.invalidateTextPresentation()
    }
  }

  /** Create the style range to highlight the part that would be overwritten
   *  by this completion.
   *
   *  @note It uses the same settings as Java for the foreground/background color
   */
  private def createStyleRange(viewer: ITextViewer): StyleRange = {
    val text = viewer.getTextWidget()
    if (text == null || text.isDisposed())
      return null

    val widgetCaret = text.getCaretOffset()

    var modelCaret = 0
    viewer match {
      case viewer2: ITextViewerExtension5 =>
        modelCaret = viewer2.widgetOffset2ModelOffset(widgetCaret)
      case _ =>
        val visibleRegion = viewer.getVisibleRegion()
        modelCaret = widgetCaret + visibleRegion.getOffset()
    }

    if (modelCaret > startPos + completion.length)
      return null

    val region = existingIdentifier(viewer.getDocument(), modelCaret)
    val length = startPos + region.getLength() - modelCaret

    new StyleRange(modelCaret, length, getForegroundColor, getBackgroundColor)
  }

  private def existingIdentifier(doc: IDocument, offset: Int): IRegion = {
    ScalaWordFinder.findWord(doc, offset)
  }

  private var viewer: ITextViewer = null

  object presListener extends ITextPresentationListener {
    override def applyTextPresentation(textPresentation: TextPresentation) {
      if (viewer ne null) {
        cachedStyleRange = createStyleRange(viewer)
        if (cachedStyleRange != null)
          textPresentation.mergeStyleRange(cachedStyleRange)
      }
    }
  }

  def validate(doc: IDocument, offset: Int, event: DocumentEvent): Boolean = {
    isValidFor(doc, offset)
  }
}

object ScalaCompletionProposal {
  import ScalaImages._
  val defImage = PUBLIC_DEF.createImage()
  val classImage = SCALA_CLASS.createImage()
  val traitImage = SCALA_TRAIT.createImage()
  val objectImage = SCALA_OBJECT.createImage()
  val packageObjectImage = SCALA_PACKAGE_OBJECT.createImage()
  val typeImage = SCALA_TYPE.createImage()
  val valImage = PUBLIC_VAL.createImage()

  val javaInterfaceImage = JavaPluginImages.get(JavaPluginImages.IMG_OBJS_INTERFACE)
  val javaClassImage = JavaPluginImages.get(JavaPluginImages.IMG_OBJS_CLASS)
  val packageImage = JavaPluginImages.get(JavaPluginImages.IMG_OBJS_PACKAGE)

  def apply(selectionProvider: ISelectionProvider)(proposal: CompletionProposal) = new ScalaCompletionProposal(proposal, selectionProvider)

  def insertCompletion(): Boolean = {
    val preference = JavaPlugin.getDefault().getPreferenceStore()
    preference.getBoolean(PreferenceConstants.CODEASSIST_INSERT_COMPLETION)
  }

  private def colorFor(name: String) = {
    val preference = JavaPlugin.getDefault().getPreferenceStore()
    val rgb = PreferenceConverter.getColor(preference, name)
    val textTools = JavaPlugin.getDefault().getJavaTextTools()
    textTools.getColorManager().getColor(rgb)
  }

  def getForegroundColor(): Color = colorFor(PreferenceConstants.CODEASSIST_REPLACEMENT_FOREGROUND)

  def getBackgroundColor(): Color = colorFor(PreferenceConstants.CODEASSIST_REPLACEMENT_BACKGROUND)
}

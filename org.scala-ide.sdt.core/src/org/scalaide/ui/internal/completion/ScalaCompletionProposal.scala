package org.scalaide.ui.internal.completion

import org.scalaide.ui.internal.ScalaImages
import org.scalaide.util.internal.ScalaWordFinder
import org.scalaide.core.completion._
import scala.tools.refactoring.common.TextChange
import scala.tools.refactoring.implementations.AddImportStatement
import org.eclipse.jdt.internal.ui.JavaPlugin
import org.eclipse.jdt.internal.ui.JavaPluginImages
import org.eclipse.jdt.ui.PreferenceConstants
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal
import org.eclipse.jface.preference.PreferenceConverter
import org.eclipse.jface.text.DocumentEvent
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.ITextPresentationListener
import org.eclipse.jface.text.ITextViewer
import org.eclipse.jface.text.ITextViewerExtension2
import org.eclipse.jface.text.ITextViewerExtension4
import org.eclipse.jface.text.ITextViewerExtension5
import org.eclipse.jface.text.Position
import org.eclipse.jface.text.TextPresentation
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension2
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension6
import org.eclipse.jface.text.contentassist.IContextInformation
import org.eclipse.jface.text.link._
import org.eclipse.jface.text.link.LinkedModeUI.ExitFlags
import org.eclipse.jface.text.link.LinkedModeUI.IExitPolicy
import org.eclipse.jface.viewers.ISelectionProvider
import org.eclipse.jface.viewers.StyledString
import org.eclipse.swt.SWT
import org.eclipse.swt.custom.StyleRange
import org.eclipse.swt.events.VerifyEvent
import org.eclipse.swt.graphics.Color
import org.eclipse.ui.texteditor.link.EditorLinkedModeUI
import org.scalaide.util.internal.eclipse.EditorUtils
import org.eclipse.jface.text.TextSelection

/** A UI class for displaying completion proposals.
 *
 *  It adds parenthesis at the end of a proposal if it has parameters, and places the caret
 *  between them.
 */
class ScalaCompletionProposal(proposal: CompletionProposal)
  extends IJavaCompletionProposal
  with ICompletionProposalExtension
  with ICompletionProposalExtension2
  with ICompletionProposalExtension6 {

  import proposal._
  import ScalaCompletionProposal._

  private var cachedStyleRange: StyleRange = null
  private val ScalaProposalCategory = "ScalaProposal"

  override def getRelevance = relevance

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

  override def getImage = image

  /** Position after the opening parenthesis of this proposal */
  val startOfArgumentList = startPos + completion.length + 1

  /** The information that is displayed in a small hover window above the completion, showing parameter names and types. */
  override def getContextInformation(): IContextInformation =
    if (context.contextType != CompletionContext.ImportContext && tooltip.length > 0)
      new ScalaContextInformation(display, tooltip, image, startOfArgumentList)
    else null

  /** A simple display string
   */
  override def getDisplayString() = display

  /** A display string with grayed out extra details
   */
  override def getStyledDisplayString(): StyledString = {
    val styledString = new StyledString(display)
    if (displayDetail != null && displayDetail.length > 0)
      styledString.append(" - ", StyledString.QUALIFIER_STYLER).append(displayDetail, StyledString.QUALIFIER_STYLER)
    styledString
  }

  /** Some additional info (like javadoc ...)
   */
  override def getAdditionalProposalInfo() = null
  override def getSelection(d: IDocument) = null
  override def apply(d: IDocument) { throw new IllegalStateException("Shouldn't be called") }

  override def apply(d: IDocument, trigger: Char, offset: Int) {
    throw new IllegalStateException("Shouldn't be called")
  }

  /**
   * Applies the actual completion to the document, while considering if completion
   * overwrite is enabled.
   */
  def applyCompletionToDocument(viewer: ITextViewer, offset: Int, overwrite: Boolean): Unit = {
    val d: IDocument = viewer.getDocument()
    // lazy val necessary because the operation may be unnecessary and the
    // underlying document changes during completion insertion
    lazy val paramsProbablyExists = doParamsProbablyExist(d, offset)
    val completionFullString = completionString(overwrite, paramsProbablyExists)

    EditorUtils.withScalaFileAndSelection { (scalaSourceFile, textSelection) =>
      scalaSourceFile.withSourceFile { (sourceFile, compiler) =>
        val endPos = if (overwrite) startPos + existingIdentifier(d, offset).getLength() else offset
        val completedIdent = TextChange(sourceFile, startPos, endPos, completionFullString)

        val importStmt =
          if (!needImport)
            Nil
          else {
            val refactoring = new AddImportStatement { val global = compiler }
            refactoring.addImport(scalaSourceFile.file, fullyQualifiedName)
          }

        // Apply the two changes in one step, if done separately we would need an
        // another `waitLoadedType` to update the positions for the refactoring
        // to work properly.
        EditorUtils.applyChangesToFileWhileKeepingSelection(
          d, new TextSelection(d, endPos, 0), scalaSourceFile.file, completedIdent +: importStmt)

        if (context.contextType != CompletionContext.ImportContext
            && (!overwrite || !paramsProbablyExists)
            && explicitParamNames.flatten.nonEmpty)
          addArgumentTemplates(d, viewer, completionFullString)
      }
    }
  }

  override def apply(viewer: ITextViewer, trigger: Char, stateMask: Int, offset: Int): Unit = {
    val showOnlyTooltips = context.contextType == CompletionContext.NewContext || context.contextType == CompletionContext.ApplyContext

    if (!showOnlyTooltips) {
      val overwrite = !insertCompletion ^ ((stateMask & SWT.CTRL) != 0)

      applyCompletionToDocument(viewer, offset, overwrite)
    }
  }

  override def getTriggerCharacters = null
  override def getContextInformationPosition = startOfArgumentList

  override def isValidFor(d: IDocument, pos: Int) =
    prefixMatches(completion.toArray, d.get.substring(startPos, pos).toArray)

  /** Insert a completion proposal, with placeholders for each explicit argument.
   *  For each argument, it inserts its name, and puts the editor in linked mode.
   *  This means that TAB can be used to navigate to the next argument, and Enter or Esc
   *  can be used to exit this mode.
   */
  def addArgumentTemplates(document: IDocument, textViewer: ITextViewer, completionFullString: String) {
    val model = new LinkedModeModel()

    document.addPositionCategory(ScalaProposalCategory)

    linkedModeGroups foreach {
      case (offset, len) =>
        document.addPosition(ScalaProposalCategory, new Position(offset, len))
        val group = new LinkedPositionGroup()
        group.addPosition(new LinkedPosition(document, offset, len, LinkedPositionGroup.NO_STOP))
        model.addGroup(group)
    }

    model.addLinkingListener(new ILinkedModeListener() {
      override def left(environment: LinkedModeModel, flags: Int) {
        document.removePositionCategory(ScalaProposalCategory)
      }

      override def suspend(environment: LinkedModeModel) {}
      override def resume(environment: LinkedModeModel, flags: Int) {}
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
      override def doExit(environment: LinkedModeModel, event: VerifyEvent, offset: Int, length: Int) = {
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

  override def validate(doc: IDocument, offset: Int, event: DocumentEvent): Boolean = {
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

  @deprecated("Use the constructor of ScalaCompletionProposal instead", "4.0")
  def apply(selectionProvider: ISelectionProvider)(proposal: CompletionProposal) = new ScalaCompletionProposal(proposal)

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

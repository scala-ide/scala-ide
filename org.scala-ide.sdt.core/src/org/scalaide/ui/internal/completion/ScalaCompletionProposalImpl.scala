package org.scalaide.ui.internal.completion

import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jdt.internal.ui.JavaPlugin
import org.eclipse.jdt.internal.ui.JavaPluginImages
import org.eclipse.jdt.ui.PreferenceConstants
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal
import org.eclipse.jface.preference.PreferenceConverter
import org.eclipse.jface.text.DocumentEvent
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.IInformationControlCreator
import org.eclipse.jface.text.ITextPresentationListener
import org.eclipse.jface.text.ITextViewer
import org.eclipse.jface.text.ITextViewerExtension2
import org.eclipse.jface.text.ITextViewerExtension4
import org.eclipse.jface.text.ITextViewerExtension5
import org.eclipse.jface.text.Position
import org.eclipse.jface.text.TextPresentation
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension2
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension3
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension5
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension6
import org.eclipse.jface.text.contentassist.IContextInformation
import org.eclipse.jface.text.link._
import org.eclipse.jface.text.link.LinkedModeUI.ExitFlags
import org.eclipse.jface.text.link.LinkedModeUI.IExitPolicy
import org.eclipse.jface.viewers.StyledString
import org.eclipse.swt.SWT
import org.eclipse.swt.custom.StyleRange
import org.eclipse.swt.events.VerifyEvent
import org.eclipse.swt.graphics.Color
import org.eclipse.swt.graphics.Point
import org.eclipse.ui.texteditor.link.EditorLinkedModeUI
import org.scalaide.core.completion.CompletionContext
import org.scalaide.core.completion.CompletionProposal
import org.scalaide.util.internal.Commons
import org.scalaide.util.ScalaWordFinder
import org.scalaide.util.eclipse.EditorUtils
import org.eclipse.jdt.internal.ui.text.java.hover.JavadocHover
import org.scalaide.ui.internal.editor.hover.HoverControlCreator
import org.scalaide.ui.internal.editor.hover.FocusedControlCreator
import org.scalaide.ui.editor.hover.IScalaHover
import org.scalaide.core.completion.MemberKind
import org.scalaide.ui.ScalaImages
import org.eclipse.jface.text.contentassist.ICompletionProposal
import org.scalaide.ui.completion.ScalaCompletionProposal
import org.scalaide.core.internal.ScalaPlugin
import org.scalaide.core.internal.statistics.Features.CodeAssist
import org.scalaide.core.internal.statistics.Features.CharactersSaved

/** A completion proposal for the Eclipse completion framework. It wraps a [[CompletionProposal]] returned
 *  by the presentation compiler, and implements the methods needed by the platform.
 *
 *  Use [[org.scalaide.core.completion.ScalaCompletionProposal.apply]] to create an instance of this class.
 */
class ScalaCompletionProposalImpl(proposal: CompletionProposal)
    extends IJavaCompletionProposal with ICompletionProposalExtension with ICompletionProposalExtension2
    with ICompletionProposalExtension3 with ICompletionProposalExtension5 with ICompletionProposalExtension6 {

  import proposal._
  import ScalaCompletionProposalImpl._
  import ScalaCompletionProposal._

  private var viewer: ITextViewer = _
  private var cachedStyleRange: StyleRange = _
  private val ScalaProposalCategory = "ScalaProposal"

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

  /** Position after the opening parenthesis of this proposal */
  private val startOfArgumentList = startPos + completion.length + 1

  override def getRelevance = relevance
  override def getImage = image

  /** The information that is displayed in a small hover window above the completion, showing parameter names and types. */
  override def getContextInformation(): IContextInformation =
    if (context != CompletionContext.ImportContext && tooltip.length > 0)
      new ScalaContextInformation(display, tooltip, image, startOfArgumentList)
    else null

  /** A simple display string
   */
  override def getDisplayString(): String = display

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
  override def getAdditionalProposalInfo(): String = null  // Rather the next method is called.
  override def getAdditionalProposalInfo(monitor: IProgressMonitor): AnyRef = proposal.documentation().orNull

  override def getSelection(d: IDocument): Point = null
  override def apply(d: IDocument): Unit = { throw new IllegalStateException("Shouldn't be called") }

  override def apply(d: IDocument, trigger: Char, offset: Int): Unit = {
    throw new IllegalStateException("Shouldn't be called")
  }

  override def apply(viewer: ITextViewer, trigger: Char, stateMask: Int, offset: Int): Unit = {
    CodeAssist.incUsageCounter()

    val showOnlyTooltips = context == CompletionContext.NewContext || context == CompletionContext.ApplyContext

    if (!showOnlyTooltips) {
      val overwrite = !insertCompletion ^ ((stateMask & SWT.CTRL) != 0)
      val d = viewer.getDocument()

      EditorUtils.withScalaFileAndSelection { (scalaSourceFile, _) =>
        applyCompletionToDocument(d, scalaSourceFile, offset, overwrite) foreach {
          case (cursorPos, applyLinkedMode) =>
            if (applyLinkedMode) {
              val ui = mkEditorLinkedMode(d, viewer, mkLinkedModeModel(d), cursorPos)
              ui.enter()
            }
            else
              EditorUtils.doWithCurrentEditor { _.selectAndReveal(cursorPos, 0) }
        }
        None
      }

    }
  }

  override def getTriggerCharacters = null
  override def getContextInformationPosition = startOfArgumentList

  override def isValidFor(d: IDocument, pos: Int) =
    Commons.prefixMatches(completion, d.get.substring(startPos, pos))

  /** Highlight the part of the text that would be overwritten by the current selection
   */
  override def selected(viewer: ITextViewer, smartToggle: Boolean): Unit = {
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

  override def unselected(viewer: ITextViewer): Unit = {
    viewer.asInstanceOf[ITextViewerExtension4].removeTextPresentationListener(presListener)
    repairPresentation(viewer)
    cachedStyleRange = null
  }

  override def validate(doc: IDocument, offset: Int, event: DocumentEvent): Boolean =
    isValidFor(doc, offset)

  /** Insert a completion proposal, with placeholders for each explicit argument.
   *  For each argument, it inserts its name, and puts the editor in linked mode.
   *  This means that TAB can be used to navigate to the next argument, and Enter or Esc
   *  can be used to exit this mode.
   */
  private def mkLinkedModeModel(document: IDocument): LinkedModeModel = {
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
      override def left(environment: LinkedModeModel, flags: Int): Unit = {
        document.removePositionCategory(ScalaProposalCategory)
      }

      override def suspend(environment: LinkedModeModel): Unit = {}
      override def resume(environment: LinkedModeModel, flags: Int): Unit = {}
    })

    model.forceInstall()
    model
  }

  /** Prepare a linked mode for the given editor. */
  private def mkEditorLinkedMode(document: IDocument, textViewer: ITextViewer, model: LinkedModeModel, cursorPosition: Int): EditorLinkedModeUI = {
    val ui = new EditorLinkedModeUI(model, textViewer)
    ui.setExitPosition(textViewer, cursorPosition, 0, Integer.MAX_VALUE)
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

  // ICompletionProposalExtension3
  override def getInformationControlCreator: IInformationControlCreator =
    new HoverControlCreator(new JavadocHover.HoverControlCreator(new FocusedControlCreator(IScalaHover.HoverFontId), true), IScalaHover.HoverFontId)

  override def getPrefixCompletionStart(d: IDocument, offset: Int): Int = startPos
  override def getPrefixCompletionText(d: IDocument, offset: Int): CharSequence = null

  private def repairPresentation(viewer: ITextViewer): Unit = {
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

    val modelCaret = viewer match {
      case viewer2: ITextViewerExtension5 =>
        viewer2.widgetOffset2ModelOffset(widgetCaret)
      case _ =>
        val visibleRegion = viewer.getVisibleRegion()
        widgetCaret + visibleRegion.getOffset()
    }

    if (modelCaret > startPos + completion.length)
      return null

    val wordLen = ScalaWordFinder.identLenAtOffset(viewer.getDocument(), modelCaret)
    val length = startPos + wordLen - modelCaret

    new StyleRange(modelCaret, length, getForegroundColor, getBackgroundColor)
  }

  private object presListener extends ITextPresentationListener {
    override def applyTextPresentation(textPresentation: TextPresentation): Unit = {
      if (viewer ne null) {
        cachedStyleRange = createStyleRange(viewer)
        if (cachedStyleRange != null)
          textPresentation.mergeStyleRange(cachedStyleRange)
      }
    }
  }
}

object ScalaCompletionProposalImpl {
  private def insertCompletion(): Boolean = {
    val preference = JavaPlugin.getDefault().getPreferenceStore()
    preference.getBoolean(PreferenceConstants.CODEASSIST_INSERT_COMPLETION)
  }

  private def colorFor(name: String) = {
    val preference = JavaPlugin.getDefault().getPreferenceStore()
    val rgb = PreferenceConverter.getColor(preference, name)
    val textTools = JavaPlugin.getDefault().getJavaTextTools()
    textTools.getColorManager().getColor(rgb)
  }

  private def getForegroundColor(): Color = colorFor(PreferenceConstants.CODEASSIST_REPLACEMENT_FOREGROUND)

  private def getBackgroundColor(): Color = colorFor(PreferenceConstants.CODEASSIST_REPLACEMENT_BACKGROUND)
}

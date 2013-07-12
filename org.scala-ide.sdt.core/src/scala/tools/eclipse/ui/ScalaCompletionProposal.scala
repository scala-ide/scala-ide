package scala.tools.eclipse
package ui

import completion._
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension6
import org.eclipse.jface.text.contentassist.IContextInformation
import org.eclipse.swt.graphics.Image
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jface.viewers.ISelectionProvider
import org.eclipse.jface.viewers.StyledString
import org.eclipse.jface.text.Position
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.TextSelection
import org.eclipse.jface.text.ITextViewer
import org.eclipse.jface.text.DefaultInformationControl
import org.eclipse.jface.text.link._
import org.eclipse.jface.text.link.LinkedModeUI.ExitFlags
import org.eclipse.jface.text.link.LinkedModeUI.IExitPolicy
import org.eclipse.jdt.internal.ui.JavaPluginImages
import refactoring.EditorHelpers
import refactoring.EditorHelpers._
import scala.tools.refactoring.implementations.AddImportStatement
import org.eclipse.jface.text.link._
import org.eclipse.jface.text.Position
import org.eclipse.ui.texteditor.link.EditorLinkedModeUI
import org.eclipse.jdt.internal.ui.text.java.AbstractJavaCompletionProposal.ExitPolicy
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
    extends IJavaCompletionProposal with ICompletionProposalExtension with ICompletionProposalExtension6 {

  import proposal._
  import ScalaCompletionProposal._

  def getRelevance = relevance

  private lazy val image = {
    import MemberKind._

    kind match {
      case Def           => defImage
      case Class         => classImage
      case Trait         => traitImage
      case Package       => packageImage
      case PackageObject => packageObjectImage
      case Object        =>
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
   *  It consists of the method name, followed by all explicit parameter sections,
   *  and inside each section the parameter names, delimited by commas.
   *
   *  @note This field needs to be lazy because it triggers the potentially expensive
   *        `getParameterNames` operation.
   */
  private lazy val completionString =
    if (explicitParamNames.isEmpty)
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

  /**
   * A simple display string
   */
  def getDisplayString() = display

  /**
   * A display string with grayed out extra details
   */
  def getStyledDisplayString() : StyledString = {
       val styledString= new StyledString(display)
       if (displayDetail != null && displayDetail.length > 0)
         styledString.append(" - ", StyledString.QUALIFIER_STYLER).append(displayDetail, StyledString.QUALIFIER_STYLER)
      styledString
    }

  /**
   * Some additional info (like javadoc ...)
   */
  def getAdditionalProposalInfo() = null
  def getSelection(d: IDocument) = null
  def apply(d: IDocument) { throw new IllegalStateException("Shouldn't be called") }

  def apply(d: IDocument, trigger: Char, offset: Int) {

    withScalaFileAndSelection { (scalaSourceFile, textSelection) =>

      val completionChange = scalaSourceFile.withSourceFile { (sourceFile, _) =>
        TextChange(sourceFile, startPos, offset, completionString)
      }()

      val importStmt = if (needImport) { // add an import statement if required
        scalaSourceFile.withSourceFile { (_, compiler) =>
          val refactoring = new AddImportStatement { val global = compiler }
          refactoring.addImport(scalaSourceFile.file, fullyQualifiedName)
        }(Nil)
      } else {
        Nil
      }

      // Apply the two changes in one step, if done separately we would need an
      // another `waitLoadedType` to update the positions for the refactoring
      // to work properly.
      EditorHelpers.applyChangesToFileWhileKeepingSelection(
          d, textSelection, scalaSourceFile.file, completionChange :: importStmt)

      None
    }

    selectionProvider match {
      case viewer: ITextViewer if explicitParamNames.flatten.nonEmpty =>
        addArgumentTemplates(d, viewer)
      case _ => ()
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
  def addArgumentTemplates(document: IDocument, textViewer: ITextViewer) {
    val model= new LinkedModeModel()

    document.addPositionCategory(ScalaProposalCategory)
    var offset = startPos + completion.length

    for (section <- explicitParamNames) {
      offset += 1 // open parenthesis
      var idx = 0 // the index of the current argument
      for (proposal <- section) {
        val group = new LinkedPositionGroup();
        val positionOffset = offset + 2 * idx // each argument is followed by ", "
        val positionLength = proposal.length
        offset += positionLength

        document.addPosition(ScalaProposalCategory, new Position(positionOffset, positionLength))
        group.addPosition(new LinkedPosition(document, positionOffset, positionLength, LinkedPositionGroup.NO_STOP))
        model.addGroup(group);
        idx += 1
      }
      offset += 1 + 2 * (idx - 1) // close parenthesis around section (and the last argument isn't followed by comma and space)
    }

    model.addLinkingListener(new ILinkedModeListener() {
      def left(environment: LinkedModeModel, flags: Int) {
        document.removePositionCategory(ScalaProposalCategory);
      }

      def suspend(environment: LinkedModeModel) {}
      def resume(environment: LinkedModeModel, flags: Int) {}
    })

    model.forceInstall();

    val ui = mkEditorLinkedMode(document, textViewer, model)
    ui.enter();
  }

  /** Prepare a linked mode for the given editor. */
  private def mkEditorLinkedMode(document: IDocument, textViewer: ITextViewer, model: LinkedModeModel): EditorLinkedModeUI = {
    val ui = new EditorLinkedModeUI(model, textViewer)
    ui.setExitPosition(textViewer, startPos + completionString.length(), 0, Integer.MAX_VALUE)
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
    });
    ui.setCyclingMode(LinkedModeUI.CYCLE_WHEN_NO_PARENT);
    ui.setDoContextInfo(true);
    ui
  }

  private val ScalaProposalCategory = "ScalaProposal"
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
}
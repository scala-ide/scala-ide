/*
 * Copyright 2005-2009 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.eclipse

import org.eclipse.jdt.core.{ ICompilationUnit, IJavaElement, JavaModelException }
import org.eclipse.jdt.internal.corext.util.JavaModelUtil
import org.eclipse.jdt.internal.ui.JavaPlugin
import org.eclipse.jdt.internal.ui.javaeditor.ClassFileDocumentProvider
import org.eclipse.ui.IEditorInput

import scala.util.Sorting

import java.{ util => ju }

import org.eclipse.core.resources.{IWorkspaceRunnable,IMarker,IResource};
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.internal.ui.javaeditor.{ CompilationUnitEditor, JavaSourceViewer }
import org.eclipse.jdt.ui.text.java.ContentAssistInvocationContext
import org.eclipse.jface.util.PropertyChangeEvent
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text.{TextPresentation,ITypedRegion,DocumentEvent,DefaultInformationControl,IInformationControlCreator,IDocumentListener,IDocument,DocumentCommand,IAutoEditStrategy,ITextViewer,ITextHover,ITextHoverExtension,IRegion,Region};
import org.eclipse.jface.text.contentassist.{ContentAssistant,ICompletionProposal,IContentAssistant,IContentAssistProcessor,IContextInformation,IContextInformationPresenter,IContextInformationValidator};
import org.eclipse.jface.text.hyperlink.{IHyperlink,IHyperlinkDetector};
import org.eclipse.jface.text.presentation.{IPresentationDamager,IPresentationRepairer,PresentationReconciler};
import org.eclipse.jface.text.source.{AnnotationModelEvent,IAnnotationModel,ICharacterPairMatcher,IOverviewRuler,ISourceViewer,IVerticalRuler,SourceViewerConfiguration,IAnnotationModelListener};
import org.eclipse.jface.text.source.projection.{ProjectionAnnotationModel,ProjectionSupport,ProjectionViewer,IProjectionListener};
import org.eclipse.ui.{IEditorPart,IFileEditorInput};
import org.eclipse.ui.editors.text.{TextEditor};
import org.eclipse.ui.texteditor.{ContentAssistAction,SourceViewerDecorationSupport,IAbstractTextEditorHelpContextIds,ITextEditorActionConstants,ITextEditorActionDefinitionIds,IWorkbenchActionDefinitionIds,TextOperationAction};
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.{ExtendedModifyEvent,ExtendedModifyListener};
import org.eclipse.swt.events.{KeyListener,KeyEvent,FocusListener,FocusEvent};
import org.eclipse.swt.widgets.{Composite,Shell};



import scala.tools.eclipse.contribution.weaving.jdt.ui.javaeditor.{ ScalaCompilationUnitDocumentProvider, ScalaEditor } 
import scala.tools.eclipse.contribution.weaving.jdt.ui.text.java.ScalaCompletionProcessor

class Editor extends ScalaEditor with IAutoEditStrategy {
  import lampion.core.Dirs._

  val plugin : ScalaUIPlugin = Driver.driver

  showChangeInformation(true) 
  setSourceViewerConfiguration(sourceViewerConfiguration)
  setPartName("Lampion Editor");

  var file : Option[plugin.File] = None
  private var isDocumentCommand = false
  private var modifying = false
  
  private def w2m(o : Int) = getSourceViewer0.widgetOffset2ModelOffset(o)
  private def m2w(o : Int) = getSourceViewer0.modelOffset2WidgetOffset(o)
  
  def customizeDocumentCommand(document : IDocument, command : DocumentCommand) : Unit = if (!modifying && !file.isEmpty) try {
    //Console.println("BEFORE")
    modifying = true
    isDocumentCommand = true
    val edit = new plugin.Edit(command.offset, command.length, command.text)
    val external = this.file.get.external
    val file = external.file
    val edit0 = file.beforeEdit(edit)
    if (edit eq edit0) {

      return // no edit
    }
    val sv = getSourceViewer0
    if (edit0 ne plugin.NoEdit) {
      assert(isDocumentCommand)
      val offset = m2w(edit0.offset)
      sv.getTextWidget.replaceTextRange(offset, edit0.length, edit0.text.mkString)
      assert(!isDocumentCommand)
      isDocumentCommand = true
      edit0.afterEdit
      // look at original edit
      val edits = file.afterEdit(edit.offset, edit.text.length, edit.length)
      var offset0 : Int = edit.offset
      assert(isDocumentCommand)
      if (edit0.offset < offset0) offset0 = offset0 + edit0.text.length - edit0.length
      edits.foreach{e => 
        if (e.offset < offset0) offset0 = offset0 + e.text.length - e.length
        val offset = m2w(e.offset)
        assert(isDocumentCommand)
        sv.getTextWidget.replaceTextRange(offset, e.length, e.text.mkString)
        assert(!isDocumentCommand)
        isDocumentCommand = true
        e.afterEdit
        assert(isDocumentCommand)
      }
      command.length = 0
      command.offset = offset0
      command.shiftsCaret = true
      var moveCursorTo = edit0.moveCursorTo
      edits.foreach{e => if (e.moveCursorTo != -1) moveCursorTo = e.moveCursorTo}
      command.text = ""
      if (moveCursorTo != -1) {
        command.caretOffset = moveCursorTo
      } else {
        command.caretOffset = command.offset + 1
      }
      catchUp
    } else {
      val edits = file.afterEdit(edit.offset, edit.text.length, edit.length)
      // XXX: type annotation important or compiler breaks.
      var offset0 : Int = edit.offset
      edits.foreach{e => 
        if (e.offset < offset0) offset0 = offset0 + e.text.length - e.length
        val offset = m2w(e.offset)
        sv.getTextWidget.replaceTextRange(offset, e.length, e.text.mkString)
        e.afterEdit
      }
      command.offset = offset0
      assert(command.offset == offset0)
      command.length = 0
      command.text = ""
      command.shiftsCaret = true
      command.caretOffset = command.offset + 1
    }
  } finally { modifying = false }

  object sourceViewerConfiguration extends SourceViewer.Configuration(plugin.editorPreferenceStore, Editor.this) {
    
    override def getAutoEditStrategies(sv : ISourceViewer, contentType : String) = 
      Array(Editor.this : IAutoEditStrategy);
    override def getContentAssistant(sv : ISourceViewer) = {
      val assistant = new ContentAssistant;
      assistant.setDocumentPartitioning(getConfiguredDocumentPartitioning(sv));
      assistant.setProposalPopupOrientation(IContentAssistant.PROPOSAL_OVERLAY);
      assistant.setContextInformationPopupOrientation(IContentAssistant.CONTEXT_INFO_ABOVE);
      assistant.setInformationControlCreator(getInformationControlCreator(sv));
      assistant.setEmptyMessage("No completions available.");
      assistant.enableAutoActivation(true);
      assistant.setAutoActivationDelay(500);
      assistant.enableAutoInsert(true);
      assistant.setContextInformationPopupBackground(sv.getTextWidget().getDisplay().getSystemColor(SWT.COLOR_WHITE));
      assistant.setContextInformationPopupForeground(sv.getTextWidget().getDisplay().getSystemColor(SWT.COLOR_BLACK));
      assistant.setContextSelectorBackground(sv.getTextWidget().getDisplay().getSystemColor(SWT.COLOR_WHITE));
      assistant.setContextSelectorForeground(sv.getTextWidget().getDisplay().getSystemColor(SWT.COLOR_BLACK));
      assistant.setContentAssistProcessor(contentAssistProcessor, IDocument.DEFAULT_CONTENT_TYPE);
      assistant;
    }
  }
  
  override protected def createActions : Unit = {
    super.createActions
    val action = new ContentAssistAction(plugin.bundle, "ContentAssistProposal.", this)
    action.setActionDefinitionId(ITextEditorActionDefinitionIds.CONTENT_ASSIST_PROPOSALS)
    setAction("ContentAssistProposal", action)
    import org.eclipse.jface.action._
    import org.eclipse.jface.text._
    val openAction = new Action {
      override def toString = "OpenAction"
      override def run = {
        getSelectionProvider.getSelection match {
        case region : ITextSelection =>
          val external = Editor.this.file.get.external
          val file = external.file
          val result = external.project.hyperlink(file, region.getOffset)
          result.foreach(_.open)
        case _ =>
        }
      }
    }
    
    // reuse the JDT F3 action.
    openAction.setActionDefinitionId("org.eclipse.jdt.ui.edit.text.java.open.editor")
    setAction("OpenAction", openAction)
    
    val cutAction = new TextOperationAction(EditorMessages.bundleForConstructedKeys, "Editor.Cut.", this, ITextOperationTarget.CUT); //$NON-NLS-1$
    cutAction.setHelpContextId(IAbstractTextEditorHelpContextIds.CUT_ACTION);
    cutAction.setActionDefinitionId(IWorkbenchActionDefinitionIds.CUT);
    setAction(ITextEditorActionConstants.CUT, cutAction);

    val copyAction = new TextOperationAction(EditorMessages.bundleForConstructedKeys, "Editor.Copy.", this, ITextOperationTarget.COPY, true); //$NON-NLS-1$
    copyAction.setHelpContextId(IAbstractTextEditorHelpContextIds.COPY_ACTION);
    copyAction.setActionDefinitionId(IWorkbenchActionDefinitionIds.COPY);
    setAction(ITextEditorActionConstants.COPY, copyAction);

    val pasteAction = new TextOperationAction(EditorMessages.bundleForConstructedKeys, "Editor.Paste.", this, ITextOperationTarget.PASTE); //$NON-NLS-1$
    pasteAction.setHelpContextId(IAbstractTextEditorHelpContextIds.PASTE_ACTION);
    pasteAction.setActionDefinitionId(IWorkbenchActionDefinitionIds.PASTE);
    setAction(ITextEditorActionConstants.PASTE, pasteAction);
  }
  
  def getSourceViewer0 = super.getSourceViewer.asInstanceOf[SourceViewer with ProjectionViewer];
  
  private object documentListener extends IDocumentListener {
    def documentAboutToBeChanged(e : DocumentEvent) = {
      assert(e.getDocument == getSourceViewer0.getDocument)
      if (e.getLength != 0 || (e.getText != null && e.getText.length > 0)) {
        val external = Editor.this.file.get.external
        val file = external.file
        file.editing = true
        catchUp
      }
    }
    def documentChanged(e : DocumentEvent) = {
      assert(e.getDocument == getSourceViewer0.getDocument)
      if (e.getLength != 0 || (e.getText != null && e.getText.length > 0)) {
        val external = Editor.this.file.get.external
        val file = external.file
        plugin.assert(file.editing)
        plugin.assert(e.getOffset <= file.content.length)
        val l0 = e.getDocument.getLength
        val l1 = file.content.length
        val ee = Editor.this
        plugin.assert(e.getDocument.getLength == file.content.length)
        val timer = new lampion.util.BenchTimer
        timer.enable
        val txt = if (e.getText == null) "" else e.getText
        file.repair(e.getOffset, txt.length, e.getLength)
        timer.update
        timer.disable
        if (timer.elapsed > .05)
          Console.println("REPAIR: " + timer.elapsedString)
      }      
      if (!isDocumentCommand) // won't trigger the modified listener.
        catchUp
    }
  } 

  private object modifyListener extends ExtendedModifyListener {
    def modifyText(event : ExtendedModifyEvent) : Unit = {
      if (!isDocumentCommand) 
        return
      isDocumentCommand = false
      //Console.println("MODIFIED")
      if (!modifying) try {
        modifying = true
        val timer = new lampion.util.BenchTimer
        timer.enable
        doAutoEdit(w2m(event.start), event.length, event.replacedText.length);
        timer.update
        timer.disable
        //Console.println("AUTO: " + timer.elapsedString)
      } finally {
        modifying = false
        catchUp
      }
    }
  }
  
  private def doAutoEdit(offset : Int, added : Int, removed : Int) : Unit = {
    val external = Editor.this.file.get.external
    val file = external.file
    val project = external.project
    assert(file.editing)    
    val edits = file.afterEdit(offset, added, removed)
    val isEmpty0 =  edits.isEmpty
    val isEmpty1 = !edits.elements.hasNext
    
    if (edits.isEmpty) return
    file.resetEdit
    val sv = getSourceViewer0
    var cursorMoved = false
    var newCursor = -1
    for (e <- edits) {
      val offset = m2w(e.offset)
      assert(!isDocumentCommand)
      isDocumentCommand = true
      sv.getTextWidget.replaceTextRange(offset, e.length, e.text.mkString)
      e.afterEdit
      if (e.moveCursorTo != -1) {
        assert(newCursor == -1)
        newCursor = e.moveCursorTo
      }
    }
    if (newCursor != -1) {
      val offset = m2w(newCursor);
      sv.getTextWidget.setCaretOffset(offset)
      sv.getTextWidget.showSelection()
    }
    return
  }
  
  override def createJavaSourceViewer(parent : Composite, ruler : IVerticalRuler, overviewRuler : IOverviewRuler, isOverviewRulerVisible : Boolean, styles :  Int, store : IPreferenceStore) : JavaSourceViewer = {
    val viewer = new {
      override val plugin : Editor.this.plugin.type = Editor.this.plugin
    } with SourceViewer(parent, ruler, overviewRuler, isOverviewRulerVisible, styles, store) {
      override def doCreatePresentation = 
        super.doCreatePresentation && !modifying
      
      override def editor = Some(Editor.this)
      override def file = Editor.this.file.asInstanceOf[Option[File]]
      override def getSharedColors = Editor.this.getSharedColors
      override def getAnnotationAccess = Editor.this.getAnnotationAccess
      override def load = {
        getDocument.addDocumentListener(documentListener)
        val doc = getDocument
        assert(Editor.this.file.isEmpty)
        val neutral = Editor.this.plugin.fileFor(Editor.this.getEditorInput)
        Editor.this.file = (neutral) 
        if (Editor.this.file.isDefined) {
          val external = Editor.this.file.get.external
          val file = external.file
        
          //Editor.this.file.get.clear
          file.underlying match {
            case Editor.this.plugin.NormalFile(file0) => {
              val workspace = file0.getWorkspace 
              if (!workspace.isTreeLocked)
                workspace.run(new IWorkspaceRunnable {
                  def run(monitor : IProgressMonitor) = 
                    file0.deleteMarkers(IMarker.PROBLEM, false, IResource.DEPTH_ZERO)
                }, null)
            }  
            case _ =>
          }
        }
        super.load
      }
      override def unload = if (!Editor.this.file.isEmpty) {
        assert(!Editor.this.file.isEmpty)
        getDocument.removeDocumentListener(documentListener)
        super.unload
        Editor.this.file = None
      }
    }
    //viewer.projection
    val svds = getSourceViewerDecorationSupport(viewer)
    assert(svds != null);
    val tw = viewer.getTextWidget
    tw.addExtendedModifyListener(modifyListener)
    viewer
  }
  
  object matcher extends ICharacterPairMatcher {
    import ICharacterPairMatcher._
    def clear = {   }
    private var anchor = 0
    def getAnchor = anchor
    def `match`(document : IDocument, offset : Int) : IRegion = {
      if (offset < 1 || document == null) return null
      assert(getSourceViewer.getDocument eq document)
      val external = Editor.this.file.get.external
      val file = external.file
      val project = external.project
      if (offset - 1 >= file.content.length) return null
      file.findMatch(offset) match {
        case Some((dir,m)) =>
          anchor = if (dir == PREV) LEFT else RIGHT
          return new Region(m.from, m.until - m.from)
        case _ => return null
      }
    }
    def dispose = {  }
  }
  
  //val EDITOR_MATCHING_BRACKETS= "matchingBrackets"; //$NON-NLS-1$
  //val EDITOR_MATCHING_BRACKETS_COLOR=  "matchingBracketsColor"; //$NON-NLS-1$

  override protected def configureSourceViewerDecorationSupport(support : SourceViewerDecorationSupport) : Unit = {
    support.setCharacterPairMatcher(matcher)   
    val EDITOR_MATCHING_BRACKETS= "matchingBrackets"; //$NON-NLS-1$
    val EDITOR_MATCHING_BRACKETS_COLOR=  "matchingBracketsColor"; //$NON-NLS-1$
    support.setMatchingCharacterPainterPreferenceKeys("matchingBrackets", "matchingBracketsColor")
    support.setMatchingCharacterPainterPreferenceKeys(EDITOR_MATCHING_BRACKETS, EDITOR_MATCHING_BRACKETS_COLOR)
    plugin.getPreferenceStore.setValue(EDITOR_MATCHING_BRACKETS, true)
    plugin.getPreferenceStore.setValue(EDITOR_MATCHING_BRACKETS_COLOR, "0,100,0")
    super.configureSourceViewerDecorationSupport(support)
  }
  
  object contentAssistProcessor
    extends ScalaCompletionProcessor(Editor.this, new ContentAssistant, IDocument.DEFAULT_CONTENT_TYPE) {
    override def collectProposals0(tv : ITextViewer, offset : Int, monitor : IProgressMonitor,  context : ContentAssistInvocationContext) : ju.List[_] = {
      catchUp
      val external = Editor.this.file.get.external
      val file = external.file
      val completions = file.doComplete(offset)
      ju.Arrays.asList(completions.toArray : _*)
    }
  }

  override def dispose = {
    val viewer = getSourceViewer
    if (viewer != null)
      viewer.setDocument(null)
    
    super.dispose
  }
  
  override protected def initializeEditor = {
    super.initializeEditor
  }
  
  def catchUp = getSourceViewer0.catchUp
  
  override protected def handlePreferenceStoreChanged(event : PropertyChangeEvent) = {
    super.handlePreferenceStoreChanged(event)
    val plugin = this.plugin
    import plugin._
    def ck(id :String) = event.getProperty.endsWith(id)
    if (ck(backgroundId) ||
      ck(foregroundId) ||
        ck(boldId) ||
          ck(underlineId) ||
            ck(italicsId) ||
              ck(strikeoutId)) {
      if (file != null && file.isDefined) {
        val external = Editor.this.file.get.external
        val file = external.file
        val viewer = getSourceViewer0
        viewer.invalidateTextPresentation(0, file.content.length)
      }
    }
  }
  
  override def setSourceViewerConfiguration(configuration : SourceViewerConfiguration) {
    super.setSourceViewerConfiguration(
      configuration match {
        case svc : SourceViewer.Configuration => svc
        case _ => sourceViewerConfiguration
      })
  }

  override def doSetInput(input : IEditorInput) = {
    input match {
      case null =>
      case input : plugin.ClassFileInput =>
        setDocumentProvider(new ClassFileDocumentProvider)
      case _ =>
        setDocumentProvider(new ScalaCompilationUnitDocumentProvider)
    }
    super.doSetInput(input)
  }

  override def getCorrespondingElement(element : IJavaElement ) : IJavaElement = element

  override def getElementAt(offset : Int) : IJavaElement = getElementAt(offset, true)

  override def getElementAt(offset : Int, reconcile : Boolean) : IJavaElement = {
    getInputJavaElement match {
      case unit : ICompilationUnit => 
        try {
          if (reconcile) {
            JavaModelUtil.reconcile(unit)
            unit.getElementAt(offset)
          } else if (unit.isConsistent())
            unit.getElementAt(offset)
          else
            null
        } catch {
          case ex : JavaModelException => {
            if (!ex.isDoesNotExist)
              JavaPlugin.log(ex.getStatus)
            null
          }
        }
      case _ => null
    }
  }
}

object EditorMessages {
  private val EDITOR_BUNDLE_FOR_CONSTRUCTED_KEYS = "org.eclipse.ui.texteditor.ConstructedEditorMessages"
  val bundleForConstructedKeys = ju.ResourceBundle.getBundle(EDITOR_BUNDLE_FOR_CONSTRUCTED_KEYS)
}

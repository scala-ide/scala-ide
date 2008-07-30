/*
 * Copyright 2005-2008 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package lampion.eclipse

import scala.util.Sorting

import org.eclipse.core.resources.{IWorkspaceRunnable,IMarker,IResource};
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.internal.ui.javaeditor.{ CompilationUnitEditor, JavaEditor }
import org.eclipse.jface.util.PropertyChangeEvent
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text.{TextPresentation,ITypedRegion,DocumentEvent,DefaultInformationControl,IInformationControlCreator,IDocumentListener,IDocument,DocumentCommand,IAutoEditStrategy,ITextViewer,ITextHover,ITextHoverExtension,IRegion,Region};
import org.eclipse.jface.text.contentassist.{ContentAssistant,ICompletionProposal,IContentAssistant,IContentAssistProcessor,IContextInformation,IContextInformationPresenter,IContextInformationValidator};
import org.eclipse.jface.text.hyperlink.{IHyperlink,IHyperlinkDetector};
import org.eclipse.jface.text.presentation.{IPresentationDamager,IPresentationRepairer,PresentationReconciler};
import org.eclipse.jface.text.source.{AnnotationModelEvent,IAnnotationModel,ICharacterPairMatcher,IOverviewRuler,ISourceViewer,IVerticalRuler,SourceViewerConfiguration,IAnnotationModelListener};
import org.eclipse.jface.text.source.projection.{ProjectionAnnotationModel,ProjectionSupport,ProjectionViewer,IProjectionListener};
import org.eclipse.ui.{IFileEditorInput};
import org.eclipse.ui.editors.text.{TextEditor};
import org.eclipse.ui.texteditor.{ContentAssistAction,SourceViewerDecorationSupport,ITextEditorActionDefinitionIds};
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.{ExtendedModifyEvent,ExtendedModifyListener};
import org.eclipse.swt.events.{KeyListener,KeyEvent,FocusListener,FocusEvent};
import org.eclipse.swt.widgets.{Composite,Shell};

abstract class Editor extends JavaEditor with IAutoEditStrategy  {
  val plugin : UIPlugin
  import lampion.core.Dirs._

  showChangeInformation(true) 
  setSourceViewerConfiguration(SourceViewerConfiguration)
  
  var file : Option[plugin.File] = None
  
  private def w2m(o : Int) = getSourceViewer0.widgetOffset2ModelOffset(o)
  private def m2w(o : Int) = getSourceViewer0.modelOffset2WidgetOffset(o)
  private var isDocumentCommand = false
  
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

  object SourceViewerConfiguration extends SourceViewer.Configuration(plugin.editorPreferenceStore, Editor.this) {
    
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
  
  setPartName("Lampion Editor");

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
      if (!isDocumentCommand) { // won't trigger the modified listener.
        assert(true)
        catchUp
        assert(true)
      }
    }
  } 
  private object keyListener extends KeyListener {
    override def keyReleased(e : KeyEvent) : Unit = {
    }
    override def keyPressed(e : KeyEvent) : Unit = {
      
    }
  }
  private var modifying = false
  private object modifyListener extends ExtendedModifyListener {
    def modifyText(event : ExtendedModifyEvent) : Unit = {
      if (!isDocumentCommand) {
        assert(true)
        assert(false)
        return
      }
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
  import org.eclipse.jface.viewers._
  import org.eclipse.ui.texteditor._
  import org.eclipse.swt.dnd._

  override def createJavaSourceViewer(parent : Composite, ruler : IVerticalRuler, overviewRuler : IOverviewRuler, isOverviewRulerVisible : Boolean, styles :  Int, store : IPreferenceStore) = {
  //override protected def createSourceViewer(parent : Composite, ruler : IVerticalRuler, styles : Int) = {
    val viewer = new {
      override val plugin : Editor.this.plugin.type = Editor.this.plugin
    } with lampion.eclipse.SourceViewer(parent, ruler, overviewRuler, isOverviewRulerVisible, styles) {
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
          case Editor.this.plugin.NormalFile(file0) => file0.getWorkspace.run(new IWorkspaceRunnable {
            def run(monitor : IProgressMonitor) = 
              file0.deleteMarkers(IMarker.PROBLEM, false, IResource.DEPTH_ZERO)
          }, null)
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
    tw.addKeyListener(keyListener)
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
  object contentAssistProcessor extends IContentAssistProcessor with IContextInformationValidator with IContextInformationPresenter {
    override def computeCompletionProposals(tv : ITextViewer, offset : Int) = {
      catchUp
      val external = Editor.this.file.get.external
      val file = external.file
      val completions = file.doComplete(offset)
      if (!completions.isEmpty)
        Sorting.stableSort(completions, (a : ICompletionProposal, b : ICompletionProposal) => a.getDisplayString < b.getDisplayString).toArray
      else null
    }
    override def computeContextInformation(tv : ITextViewer, offset : Int) = null
    override def getCompletionProposalAutoActivationCharacters = Array('.')
    override def getContextInformationAutoActivationCharacters = null
    override def getContextInformationValidator = this
    override def getErrorMessage = null
    private var installOffset = -1
    override def install(info : IContextInformation, tv : ITextViewer, offset : Int) = installOffset = offset
    override def updatePresentation(pos : Int, presentation : TextPresentation) = false
    override def isContextInformationValid(offset : Int) =
      true || Math.abs(installOffset - offset) < 5
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
}

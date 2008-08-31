/*
 * Copyright 2005-2008 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package lampion.eclipse;

import org.eclipse.core.resources.{ IWorkspaceRunnable, IMarker, IResource }
import org.eclipse.core.runtime.{ IProgressMonitor, NullProgressMonitor }
import org.eclipse.core.resources.{IWorkspaceRunnable,IMarker,IResource}
import org.eclipse.jdt.internal.ui.JavaPlugin
import org.eclipse.jdt.internal.ui.javaeditor.JavaSourceViewer
import org.eclipse.jdt.internal.ui.text.{ JavaColorManager, JavaCompositeReconcilingStrategy, JavaReconciler }
import org.eclipse.jdt.ui.text.JavaSourceViewerConfiguration
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text.{TextPresentation,ITextInputListener,ITypedRegion,DocumentEvent,DefaultInformationControl,IInformationControlCreator,IDocumentListener,IDocument,DocumentCommand,IAutoEditStrategy,ITextViewer,ITextHover,ITextHoverExtension,IRegion,Region}
import org.eclipse.jface.text.contentassist.{ContentAssistant,IContentAssistant,IContentAssistProcessor,IContextInformation,IContextInformationPresenter,IContextInformationValidator}
import org.eclipse.jface.text.hyperlink.{IHyperlink,IHyperlinkDetector}
import org.eclipse.jface.text.presentation.{IPresentationDamager,IPresentationRepairer,PresentationReconciler}
import org.eclipse.jface.text.reconciler.IReconciler
import org.eclipse.jface.text.source._
import org.eclipse.jface.text.source.projection.{ProjectionAnnotationModel,ProjectionSupport,ProjectionViewer,IProjectionListener}
import org.eclipse.swt.SWT
import org.eclipse.swt.custom.{ExtendedModifyEvent,ExtendedModifyListener}
import org.eclipse.swt.events.{KeyListener,KeyEvent,FocusListener,FocusEvent,VerifyEvent}
import org.eclipse.swt.widgets.{Composite,Shell}
import org.eclipse.ui.{IFileEditorInput}
import org.eclipse.ui.texteditor.{ContentAssistAction,SourceViewerDecorationSupport,ITextEditor, ITextEditorActionDefinitionIds}
import org.eclipse.ui.editors.text.{ TextEditor, TextSourceViewerConfiguration }

import lampion.util.ReflectionUtils

abstract class SourceViewer(parent : Composite, vertical : IVerticalRuler, overview : IOverviewRuler, showAnnotationsOverview : Boolean, styles : Int, store: IPreferenceStore) extends 
  JavaSourceViewer(parent,vertical,overview,showAnnotationsOverview,styles, store) with IAnnotationModelListener with FocusListener with ITextInputListener {
  val plugin : UIPlugin
  type File = plugin.ProjectImpl#FileImpl
  def file : Option[File]
  private[eclipse] var busy = false
  
  override def configure(configuration : SourceViewerConfiguration) {
    super.configure(configuration)
    SourceViewer.setIsSetVisibleDocumentDelayed(this, false)
  }
  
  override protected def handleVerifyEvent(e : VerifyEvent) = try {
    super.handleVerifyEvent(e)
  } catch {
  case ex : IllegalArgumentException =>
    assert(true)
    plugin.logError(ex)
  case ex : IllegalStateException =>
    assert(true)
    plugin.logError(ex)
  }
  
  def catchUp : Unit = {
    if (!busy) try {
      busy = true
      file.foreach(_.processEdit)
    } finally {
      busy = false
    }
  }
  private var hyper = false
  
  object reconciler extends PresentationReconciler with IPresentationDamager with IPresentationRepairer {
    setDamager (this, IDocument.DEFAULT_CONTENT_TYPE);
    setRepairer(this, IDocument.DEFAULT_CONTENT_TYPE);
    override def createPresentation(presentation : TextPresentation, damage : ITypedRegion) : Unit = {
      if (!file.isEmpty && doCreatePresentation) {
        val offset = damage.getOffset
        val length = damage.getLength
        val file = SourceViewer.this.file.get
        //catchUp
        //Console.out.println("REFRESH: " + offset + " " + length)
        file.refresh(offset, length, presentation)
      } else {
        assert(true)
        assert(true)
      }
    }
    private def NO_REGION = new Region(0, 0);
    override def getDamageRegion(partition : ITypedRegion, event : DocumentEvent, documentPartitioningChanged : Boolean) : Region = {
      val text = if (event.getText == null) "" else event.getText
      new Region(event.getOffset, text.length)
    }
    def setDocument(doc : IDocument) = {}
  }
  def doCreatePresentation = true
  
  import org.eclipse.jface.text.information.IInformationProviderExtension2;

  object textHover extends ITextHover with ITextHoverExtension with IInformationProviderExtension2 {
    override def getHoverInfo(viewer : ITextViewer, region : IRegion) = try {
      val project = SourceViewer.this.file.get.project
      val file = SourceViewer.this.file.get.asInstanceOf[project.File]
      project.hover(file, region.getOffset) match {
      case None => null
      case Some(seq) => seq.mkString + " <p></p>"
      }
    } catch {
      case ex => 
        plugin.logError(ex)
        ""
    }

    def getHoverRegion(viewer : ITextViewer, offset : Int) = new Region(offset, 0); 
    def getHoverControlCreator = new IInformationControlCreator {
      def createInformationControl(parent : Shell) = try {
        import org.eclipse.jface.internal.text.html._;
        import org.eclipse.jdt.ui._;
        var clazz = classOf[BrowserInformationControl]
        var c = clazz.getConstructor(classOf[Shell], classOf[String], java.lang.Boolean.TYPE)
        val ret0 = c.newInstance(parent, PreferenceConstants.APPEARANCE_JAVADOC_FONT, new java.lang.Boolean(false)).asInstanceOf[BrowserInformationControl];
        ret0
      } catch {
      case t : Throwable => try {
        import org.eclipse.jface.internal.text.html.HTMLTextPresenter;
        //val ret0 = new BrowserInformationControl(parent, PreferenceConstants.APPEARANCE_JAVADOC_FONT, false)
        val ret = new DefaultInformationControl(parent, new HTMLTextPresenter)
        ret.setBackgroundColor(parent.getDisplay.getSystemColor(SWT.COLOR_WHITE));
        ret.setForegroundColor(parent.getDisplay.getSystemColor(SWT.COLOR_BLACK));
        ret
      } catch {
      case t : Throwable => new DefaultInformationControl(parent)
      }}
      }
    def getInformationPresenterControlCreator = getHoverControlCreator
  }
  object hyperlinkDetector extends IHyperlinkDetector {
    override def detectHyperlinks(tv : ITextViewer, region : IRegion, canShowMultiple : Boolean) : Array[IHyperlink] = {
      //Console.println("HYPER: " + region.getOffset + " " + region.getLength)
      val project = SourceViewer.this.file.get.project
      val file = SourceViewer.this.file.get.asInstanceOf[project.File]
      val result = project.hyperlink(file, region.getOffset)
      hyper = true
      if (result == None) null
      else (result.get :: Nil).toArray
    }
  }
  def getAnnotationAccess : IAnnotationAccess
  def getSharedColors : ISharedTextColors
  
  private[eclipse] def projection : ProjectionAnnotationModel = {
    if (!isProjectionMode()) {
      val projectionSupport = new ProjectionSupport(this,getAnnotationAccess,getSharedColors);
      projectionSupport.install();
      //doOperation(ProjectionViewer.TOGGLE);
      //assert(isProjectionMode());
      val ee = getProjectionAnnotationModel
      if (ee != null) ee.addAnnotationModelListener(this)
    }
    return getProjectionAnnotationModel;
  }
  override def canDoOperation(operation : Int) : Boolean = {
    if (operation == ProjectionViewer.TOGGLE)
      false
    else
      super.canDoOperation(operation)
	}
  override def modelChanged(model : IAnnotationModel) = {
    assert(model == projection)
  }
  override def focusGained(e : FocusEvent) : Unit = {
    if (!file.isEmpty && file.get.editing) file.get.doPresentation
  }
  override def focusLost  (e : FocusEvent) : Unit = {
    if (!file.isEmpty && file.get.editing) file.get.doPresentation
  }
  override def inputDocumentAboutToBeChanged(oldInput : IDocument, newInput : IDocument) = {
    if (oldInput != null && oldInput != newInput) {
      unload
    }
  }
  override def inputDocumentChanged(oldInput : IDocument, newInput : IDocument) = {
    if (newInput != null && oldInput != newInput) {
      load 
    }
  }
  def editor : Option[Editor] = None
  private var thread : Thread = _
  def load : Unit = {
    thread = Thread.currentThread
    if (this.file.isEmpty) {
      plugin.logError("file could not be found, please refresh and make sure its in the project! Make sure your kind in the .project file is set to ch.epfl.lamp.sdt.core and not scala.plugin.", null)
      return
    }
    if (this.file.get.isLoaded)
      this.file.get.doUnload
    assert(!this.file.get.isLoaded)
    val project = this.file.get.project
    project.initialize(this)
    val file = this.file.get.asInstanceOf[project.File]
    plugin.viewers(file) = this
    assert(file.isLoaded)
    file.clear
    val timer = new lampion.util.BenchTimer
    file.repair(0, getDocument.getLength, 0)
    Console.println("LOAD_REPAIR: " + timer.elapsedString)
    file.loaded
    catchUp
  }
  def unload : Unit = {
    if (this.file.isEmpty || !this.file.get.isLoaded) return 
    assert(this.file.get.isLoaded)
    val project = this.file.get.project
    val file = this.file.get.asInstanceOf[project.File]
    //plugin.viewers.removeKey(file)
    file.doUnload
    file.unloaded
    //catchUp
  }
  getTextWidget.addFocusListener(this)
  this.addTextInputListener(this)
}

object SourceViewer extends ReflectionUtils {
  val jsvClass = Class.forName("org.eclipse.jdt.internal.ui.javaeditor.JavaSourceViewer")
  val fIsSetVisibleDocumentDelayedField = getField(jsvClass, "fIsSetVisibleDocumentDelayed")

  class Configuration(store : IPreferenceStore, editor : ITextEditor)
    //extends TextSourceViewerConfiguration(store) {
    extends JavaSourceViewerConfiguration(JavaPlugin.getDefault.getJavaTextTools.getColorManager, store, editor, null) {
    implicit def coerce(sv : ISourceViewer) = sv.asInstanceOf[SourceViewer]
    override def getPresentationReconciler(sv : ISourceViewer) = sv.reconciler
    override def getTextHover(sv : ISourceViewer, contentType : String, stateMask : Int) = sv.textHover
    override def getHyperlinkDetectors(sv : ISourceViewer) = Array(sv.hyperlinkDetector : IHyperlinkDetector)

    override def getReconciler(sourceViewer : ISourceViewer) : IReconciler = {
      if (editor != null && editor.isEditable) {
        val strategy = new JavaCompositeReconcilingStrategy(sourceViewer, editor, getConfiguredDocumentPartitioning(sourceViewer))
        val reconciler = new JavaReconciler(editor, strategy, false)
        reconciler.setIsIncrementalReconciler(false)
        reconciler.setIsAllowedToModifyDocument(false)
        reconciler.setProgressMonitor(new NullProgressMonitor)
        reconciler.setDelay(500)
        reconciler
      }
      else
        null
    }
  }
    
  def setIsSetVisibleDocumentDelayed(sv : SourceViewer, value : Boolean) = fIsSetVisibleDocumentDelayedField.set(sv, value)
}

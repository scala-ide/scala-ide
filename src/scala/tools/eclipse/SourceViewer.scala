/*
 * Copyright 2005-2009 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.eclipse;

import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.jdt.internal.ui.JavaPlugin
import org.eclipse.jdt.internal.ui.javaeditor.JavaSourceViewer
import org.eclipse.jdt.internal.ui.text.{ JavaCompositeReconcilingStrategy, JavaReconciler }
import org.eclipse.jdt.internal.ui.text.java.hover.{ JavadocBrowserInformationControlInput, JavadocHover }
import org.eclipse.jdt.ui.text.JavaSourceViewerConfiguration
import org.eclipse.jface.internal.text.html.HTMLPrinter
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text.{ TextPresentation, ITextInputListener, ITypedRegion, DocumentEvent, IDocument, ITextViewer, IRegion, Region}
import org.eclipse.jface.text.hyperlink.{ IHyperlink, IHyperlinkDetector }
import org.eclipse.jface.text.presentation.{ IPresentationDamager, IPresentationRepairer, PresentationReconciler }
import org.eclipse.jface.text.reconciler.IReconciler
import org.eclipse.jface.text.source.{ IAnnotationAccess, IAnnotationModel, IAnnotationModelListener, IOverviewRuler, ISharedTextColors, ISourceViewer, IVerticalRuler, SourceViewerConfiguration }
import org.eclipse.jface.text.source.projection.{ ProjectionAnnotationModel, ProjectionSupport, ProjectionViewer }
import org.eclipse.swt.events.{ FocusListener, FocusEvent, VerifyEvent }
import org.eclipse.swt.widgets.{ Composite, Shell }
import org.eclipse.ui.texteditor.ITextEditor

import lampion.util.ReflectionUtils

abstract class SourceViewer(parent : Composite, vertical : IVerticalRuler, overview : IOverviewRuler, showAnnotationsOverview : Boolean, styles : Int, store: IPreferenceStore) extends 
  JavaSourceViewer(parent,vertical,overview,showAnnotationsOverview,styles, store) with IAnnotationModelListener with FocusListener with ITextInputListener {
  val plugin : ScalaPlugin
  type File = plugin.Project#File
  def file : Option[File]
  /* private[eclipse] */ var busy = false
  
  override def configure(configuration : SourceViewerConfiguration) {
    super.configure(configuration)
    SourceViewer.setIsSetVisibleDocumentDelayed(this, false)
  }
  
  override protected def handleVerifyEvent(e : VerifyEvent) = try {
    super.handleVerifyEvent(e)
  } catch {
  case ex : IllegalArgumentException =>
    plugin.logError(ex)
  case ex : IllegalStateException =>
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
        try {
          file.refresh(offset, length, presentation)
        } catch {
          case t : Throwable => t.printStackTrace 
        }
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
  
  object textHover extends JavadocHover with ReflectionUtils {
    val getStyleSheetMethod = getDeclaredMethod(classOf[JavadocHover], "getStyleSheet")
    
    editor.map(setEditor)
    
  	override def getHoverInfo2(textViewer : ITextViewer, hoverRegion :  IRegion) = {
  		val i = super.getHoverInfo2(textViewer, hoverRegion)
      if (i != null)
        i
      else {
        val s =
          try {
            val project = SourceViewer.this.file.get.project
            val file = SourceViewer.this.file.get.asInstanceOf[project.File]
            project.hover(file, hoverRegion.getOffset) match {
              case Some(seq) => seq.mkString
              case None => ""
            }
          } catch {
            case ex => {
              plugin.logError(ex)
              ""
            }
          }
        
        val buffer = new StringBuffer(s)
  			HTMLPrinter.insertPageProlog(buffer, 0, getStyleSheet0)
  			HTMLPrinter.addPageEpilog(buffer)
        
        new JavadocBrowserInformationControlInput(null, null, buffer.toString, 0)
      }
    }
    
    def getStyleSheet0 = getStyleSheetMethod.invoke(null).asInstanceOf[String]
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
  
  /* private[eclipse] */ def projection : ProjectionAnnotationModel = {
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
  val fIsSetVisibleDocumentDelayedField = getDeclaredField(jsvClass, "fIsSetVisibleDocumentDelayed")

  class Configuration(store : IPreferenceStore, editor : ITextEditor)
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

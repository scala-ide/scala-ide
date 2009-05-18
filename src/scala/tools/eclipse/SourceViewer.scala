/*
 * Copyright 2005-2009 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.eclipse;

import org.eclipse.core.resources.{ IMarker, IResource, IWorkspaceRunnable }
import org.eclipse.core.runtime.{ IProgressMonitor }
import org.eclipse.jdt.internal.ui.JavaPlugin
import org.eclipse.jdt.internal.ui.javaeditor.JavaSourceViewer
import org.eclipse.jdt.internal.ui.text.java.hover.{ JavadocBrowserInformationControlInput, JavadocHover }
import org.eclipse.jdt.ui.text.{ JavaSourceViewerConfiguration, IJavaPartitions }
import org.eclipse.jface.internal.text.html.HTMLPrinter
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text.{ TextPresentation, ITextInputListener, ITypedRegion, IDocument, ITextViewer, IRegion }
import org.eclipse.jface.text.contentassist.{ ContentAssistant, IContentAssistProcessor }
import org.eclipse.jface.text.hyperlink.{ IHyperlink, IHyperlinkDetector }
import org.eclipse.jface.text.presentation.PresentationReconciler
import org.eclipse.jface.text.rules.{ DefaultDamagerRepairer, ITokenScanner }
import org.eclipse.jface.text.source.{ IOverviewRuler, ISourceViewer, IVerticalRuler }
import org.eclipse.swt.events.{ FocusListener, FocusEvent }
import org.eclipse.swt.widgets.Composite
import org.eclipse.ui.texteditor.ITextEditor

import scala.tools.eclipse.util.ReflectionUtils

class ScalaSourceViewer(val plugin : ScalaPlugin, val editor : Editor, parent : Composite, vertical : IVerticalRuler, overview : IOverviewRuler, showAnnotationsOverview : Boolean, styles : Int, store: IPreferenceStore) extends 
  JavaSourceViewer(parent,vertical,overview,showAnnotationsOverview,styles, store) with FocusListener with ITextInputListener {

  getTextWidget.addFocusListener(this)
  this.addTextInputListener(this)

  type File = plugin.Project#File

  class ScalaDamagerRepairer(scanner : ITokenScanner) extends DefaultDamagerRepairer(scanner) {
    override def createPresentation(presentation : TextPresentation, damage : ITypedRegion) : Unit =
      super.createPresentation(presentation, damage);
  }
  
  object textHover extends JavadocHover with ReflectionUtils {
    val getStyleSheetMethod = getDeclaredMethod(classOf[JavadocHover], "getStyleSheet")
    
    setEditor(editor)
    
  	override def getHoverInfo2(textViewer : ITextViewer, hoverRegion :  IRegion) = {
  		val i = super.getHoverInfo2(textViewer, hoverRegion)
      if (i != null)
        i
      else {
        val s =
          try {
            val project = ScalaSourceViewer.this.file.get.project
            val file = ScalaSourceViewer.this.file.get.asInstanceOf[project.File]
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
      val project = ScalaSourceViewer.this.file.get.project
      val file = ScalaSourceViewer.this.file.get.asInstanceOf[project.File]
      val result = project.hyperlink(file, region.getOffset)
      if (result == None) null
      else (result.get :: Nil).toArray
    }
  }
  
  override def focusGained(e : FocusEvent) : Unit = {
    if (!file.isEmpty) file.get.doPresentation
  }
  
  override def focusLost  (e : FocusEvent) : Unit = {
    if (!file.isEmpty) file.get.doPresentation
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
        
  def load : Unit = {
    getDocument.addDocumentListener(editor.documentListener)

    editor.file = editor.plugin.fileFor(editor.getEditorInput) 
    if (file.isEmpty) {
      plugin.logError("file could not be found, please refresh and make sure its in the project! Make sure your kind in the .project file is set to ch.epfl.lamp.sdt.core and not scala.plugin.", null)
      return
    }

    file.get.underlying match {
      case plugin.NormalFile(file0) => {
        val workspace = file0.getWorkspace 
        if (!workspace.isTreeLocked)
          workspace.run(new IWorkspaceRunnable {
            def run(monitor : IProgressMonitor) = 
              file0.deleteMarkers(IMarker.PROBLEM, false, IResource.DEPTH_ZERO)
          }, null)
      }  
      case _ =>
    }
    
    if (file.get.isLoaded)
      file.get.doUnload
    
    val project = file.get.project
    project.initialize(this)
    
    val file0 = file.get.asInstanceOf[project.File]
    plugin.viewers(file0) = this
  }
  
  def unload : Unit = {
    getDocument.removeDocumentListener(editor.documentListener)
    if (file.isDefined && file.get.isLoaded) { 
      val project = file.get.project
      val file0 = file.get.asInstanceOf[project.File]
      file0.doUnload
    }
    editor.file = None
  }

  def file = editor.file.asInstanceOf[Option[File]]
}

class ScalaSourceViewerConfiguration(store : IPreferenceStore, editor : ITextEditor, contentAssistProcessor : IContentAssistProcessor)
  extends JavaSourceViewerConfiguration(JavaPlugin.getDefault.getJavaTextTools.getColorManager, store, editor, IJavaPartitions.JAVA_PARTITIONING) {
    
  private val codeScanner = new ScalaCodeScanner(getColorManager, store);
  
  override def getPresentationReconciler(sv : ISourceViewer) = {
    val reconciler = super.getPresentationReconciler(sv).asInstanceOf[PresentationReconciler]
    val ssv = sv.asInstanceOf[ScalaSourceViewer]
    val dr = new ssv.ScalaDamagerRepairer(codeScanner)
    reconciler.setDamager(dr, IDocument.DEFAULT_CONTENT_TYPE);
    reconciler.setRepairer(dr, IDocument.DEFAULT_CONTENT_TYPE);
    reconciler
  }
    
  override def getTextHover(sv : ISourceViewer, contentType : String, stateMask : Int) = sv.asInstanceOf[ScalaSourceViewer].textHover
  
  override def getHyperlinkDetectors(sv : ISourceViewer) = Array(sv.asInstanceOf[ScalaSourceViewer].hyperlinkDetector : IHyperlinkDetector)

  override def getContentAssistant(sv : ISourceViewer) = {
    val assistant = super.getContentAssistant(sv).asInstanceOf[ContentAssistant]
    assistant.setContentAssistProcessor(contentAssistProcessor, IDocument.DEFAULT_CONTENT_TYPE)
    assistant
  }
}

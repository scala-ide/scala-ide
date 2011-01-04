/**
 * 
 */
package scala.tools.eclipse
package ui.semantic.highlighting

import org.eclipse.ui.part.FileEditorInput
import org.eclipse.jdt.core.WorkingCopyOwner
import scala.tools.eclipse.internal.logging.Tracer
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitEditor;
import org.eclipse.jdt.internal.ui.JavaPlugin;

import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.preference.PreferenceConverter
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.text.TextPresentation;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.{ IAnnotationAccess, AnnotationPainter, IAnnotationModelExtension };
import org.eclipse.jface.util.IPropertyChangeListener
import org.eclipse.jface.util.PropertyChangeEvent
import org.eclipse.jface.text.IPainter

import org.eclipse.swt.SWT

import scala.tools.eclipse.javaelements.ScalaCompilationUnit;
import scala.tools.nsc.util.RangePosition
import scala.tools.eclipse.util.ColorManager
import scala.tools.eclipse.ui.preferences.PropertyChangeListenerProxy

import scala.collection._

/**
 * @author Jin Mingjian
 * @author David Bernard 
 *
 */
class SemanticHighlightingPresenter(editor : FileEditorInput, sourceViewer: ISourceViewer) {
  import scala.tools.eclipse.ui.preferences.ScalaEditorColoringPreferencePage._

  val annotationAccess = new IAnnotationAccess() {
    def getType(annotation: Annotation) = annotation.getType();
    def isMultiLine(annotation: Annotation) = true
    def isTemporary(annotation: Annotation) = true
  }

  
  private def fFontStyle_BLOD = pluginStore.getBoolean(P_BLOD) match {
    case true => SWT.BOLD
    case _ => SWT.NORMAL
  }

  private def fFontStyle_ITALIC = pluginStore.getBoolean(P_ITALIC) match {
    case true => SWT.ITALIC
    case _ => SWT.NORMAL
  }

  private val P_COLOR = {
    val lookup = new org.eclipse.ui.texteditor.AnnotationPreferenceLookup()
    val pref = lookup.getAnnotationPreference(ImplicitConversionsOrArgsAnnotation.KIND)
    pref.getColorPreferenceKey()
  }
  
  def fColorValue = ColorManager.getDefault.getColor(PreferenceConverter.getColor(editorsStore, P_COLOR))

  val impTextStyleStrategy = new ImplicitConversionsOrArgsTextStyleStrategy(fFontStyle_BLOD | fFontStyle_ITALIC)

  val painter: AnnotationPainter = {
    val b = new AnnotationPainter(sourceViewer, annotationAccess)
    b.addAnnotationType(ImplicitConversionsOrArgsAnnotation.KIND, ImplicitConversionsOrArgsAnnotation.KIND)
    b.addTextStyleStrategy(ImplicitConversionsOrArgsAnnotation.KIND, impTextStyleStrategy)
    //FIXME settings color of the underline is required to active TextStyle (bug ??, better way ??)
    b.setAnnotationTypeColor(ImplicitConversionsOrArgsAnnotation.KIND, fColorValue)
    sourceViewer.asInstanceOf[org.eclipse.jface.text.TextViewer].addPainter(b)
    sourceViewer.asInstanceOf[org.eclipse.jface.text.TextViewer].addTextPresentationListener(b)
    b
  }

  private val _listener = new IPropertyChangeListener {
    def propertyChange(event: PropertyChangeEvent) {
      val changed = event.getProperty() match {
        case P_BLOD => true
        case P_ITALIC => true
        case P_COLOR => true
        case _ => false
      }
      if (changed) {
        impTextStyleStrategy.fFontStyle = fFontStyle_BLOD | fFontStyle_ITALIC
        painter.setAnnotationTypeColor(ImplicitConversionsOrArgsAnnotation.KIND, fColorValue)
        painter.paint(IPainter.CONFIGURATION)
      }
    }
  }
  
  protected def pluginStore : IPreferenceStore = ScalaPlugin.plugin.getPreferenceStore
  protected def editorsStore :IPreferenceStore = org.eclipse.ui.editors.text.EditorsUI.getPreferenceStore

  new PropertyChangeListenerProxy(_listener, pluginStore, editorsStore).autoRegister()

  //TODO call it only on reconcile, build,... when tree is rebuild
  val update = (scu: ScalaCompilationUnit, monitor: IProgressMonitor, workingCopyOwner: WorkingCopyOwner) => {
    //val cu = JavaPlugin.getDefault().getWorkingCopyManager().getWorkingCopy(fEditor.getEditorInput());
    //val scu = cu.asInstanceOf[ScalaCompilationUnit]
    //FIXME : avoid having several SemanticHighlighter notified on single file update (=> move listeners at document level, or move SemanticHighliter at plugin level ?)
    if (scu.getResource.getLocation == editor.getPath.makeAbsolute) {
      scu.withSourceFile { (sourceFile, compiler) =>
        val toAdds = new java.util.HashMap[Annotation, org.eclipse.jface.text.Position]
        object viewsCollector extends compiler.Traverser {
          override def traverse(t: compiler.Tree): Unit = t match {
            case v: compiler.ApplyImplicitView =>
              val txt = new String(sourceFile.content, v.pos.startOrPoint, math.max(0, v.pos.endOrPoint - v.pos.startOrPoint)).trim()
              val ia = new ImplicitConversionsOrArgsAnnotation(
                scu.getCompilationUnit,
                "Implicit conversions found: " + txt + " => " + v.fun.symbol.name + "(" + txt + ")")
              val pos = new org.eclipse.jface.text.Position(v.pos.startOrPoint, txt.length)
              toAdds.put(ia, pos)
              super.traverse(t)
            case v: compiler.ApplyToImplicitArgs =>
              val txt = new String(sourceFile.content, v.pos.startOrPoint, math.max(0, v.pos.endOrPoint - v.pos.startOrPoint)).trim()
              val argsStr = v.args.map(_.symbol.name).mkString("( ", ", ", " )")
              val ia = new ImplicitConversionsOrArgsAnnotation(
                scu.getCompilationUnit,
                "Implicit arguments found: " + txt + " => " + txt + argsStr)
              val pos = new org.eclipse.jface.text.Position(v.pos.startOrPoint, txt.length)
              toAdds.put(ia, pos)
              super.traverse(t)
            case _ =>
              super.traverse(t)
          }
        }
        viewsCollector.traverse(compiler.body(sourceFile))
  
        val model = sourceViewer.getAnnotationModel()
        Tracer.println("update implicit annotations : " + toAdds.size)
        if (model ne null) {
          var toRemove: List[Annotation] = Nil
          val it = model.getAnnotationIterator()
          while (it.hasNext) {
            val annot = it.next.asInstanceOf[Annotation]
            if (ImplicitConversionsOrArgsAnnotation.KIND == annot.getType) {
              toRemove = annot :: toRemove
            }
          }
          val am = model.asInstanceOf[IAnnotationModelExtension]
          am.replaceAnnotations(toRemove.toArray, toAdds)
        }
      }
    }
  }
  
}

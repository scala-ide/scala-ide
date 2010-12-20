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

import org.eclipse.swt.SWT

import scala.tools.eclipse.javaelements.ScalaCompilationUnit;
import scala.tools.nsc.util.RangePosition
import scala.tools.eclipse.util.ColorManager

import scala.collection._

/**
 * @author Jin Mingjian
 * @author David Bernard 
 *
 */
class SemanticHighlightingPresenter(editor : FileEditorInput, sourceViewer: ISourceViewer) extends IPropertyChangeListener {
  import scala.tools.eclipse.ui.preferences.ScalaEditorColoringPreferencePage._

  val annotationAccess = new IAnnotationAccess() {
    def getType(annotation: Annotation) = annotation.getType();
    def isMultiLine(annotation: Annotation) = true
    def isTemporary(annotation: Annotation) = true
  }

  var fUnderlineStyle = getPreferenceStore().getInt(P_UNDERLINE) match {
    case 1 => SWT.UNDERLINE_SQUIGGLE
    case 2 => SWT.UNDERLINE_DOUBLE
    case 3 => SWT.UNDERLINE_SINGLE
    case 4 => 8 // for no underline case
  }

  var fFontStyle_BLOD = getPreferenceStore().getBoolean(P_BLOD) match {
    case true => SWT.BOLD
    case _ => SWT.NORMAL
  }

  var fFontStyle_ITALIC = getPreferenceStore().getBoolean(P_ITALIC) match {
    case true => SWT.ITALIC
    case _ => SWT.NORMAL
  }

  var fColorValue = PreferenceConverter.getColor(getPreferenceStore(), P_COLOR)

  val impTextStyleStrategy = new ImplicitConversionsOrArgsTextStyleStrategy(fUnderlineStyle, fFontStyle_BLOD | fFontStyle_ITALIC)

  val painter: AnnotationPainter = {
    val b = new AnnotationPainter(sourceViewer, annotationAccess)
    b.addTextStyleStrategy(ImplicitConversionsOrArgsAnnotation.KIND, impTextStyleStrategy)
    b.addAnnotationType(ImplicitConversionsOrArgsAnnotation.KIND, ImplicitConversionsOrArgsAnnotation.KIND)
    b.setAnnotationTypeColor(ImplicitConversionsOrArgsAnnotation.KIND, ColorManager.getDefault.getColor(fColorValue))
    sourceViewer.asInstanceOf[org.eclipse.jface.text.TextViewer].addPainter(b)
    sourceViewer.asInstanceOf[org.eclipse.jface.text.TextViewer].addTextPresentationListener(b)
    b
  }

  override def propertyChange(event: PropertyChangeEvent) {
    event.getProperty() match {
      case P_BLOD =>
        fFontStyle_BLOD = event.getNewValue().asInstanceOf[Boolean]
          match { case true => SWT.BOLD; case _ => SWT.NORMAL }
      case P_ITALIC =>
        fFontStyle_ITALIC = event.getNewValue().asInstanceOf[Boolean]
          match { case true => SWT.ITALIC; case _ => SWT.NORMAL }
      case P_COLOR => fColorValue = event.getNewValue().asInstanceOf[org.eclipse.swt.graphics.RGB]
      case P_UNDERLINE =>
        fUnderlineStyle = event.getNewValue().asInstanceOf[String]
          match {
            case "1" => SWT.UNDERLINE_SQUIGGLE
            case "2" => SWT.UNDERLINE_DOUBLE
            case "3" => SWT.UNDERLINE_SINGLE
            case "4" => 8
          }
    }
    impTextStyleStrategy.setUnderlineStyle(fUnderlineStyle)
    impTextStyleStrategy.setFontStyle(fFontStyle_BLOD | fFontStyle_ITALIC)
    painter.setAnnotationTypeColor(ImplicitConversionsOrArgsAnnotation.KIND, ColorManager.getDefault.getColor(fColorValue))
    //    update()
  }

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
              val txt = new String(sourceFile.content, v.pos.start, math.max(0, v.pos.end - v.pos.start)).trim()
              val ia = new ImplicitConversionsOrArgsAnnotation(
                scu.getCompilationUnit,
                ImplicitConversionsOrArgsAnnotation.KIND,
                false,
                "Implicit conversions found: " + txt + " => " + v.fun.symbol.name + "(" + txt + ")")
              val pos = new org.eclipse.jface.text.Position(v.pos.start, txt.length)
              toAdds.put(ia, pos)
              super.traverse(t)
            case v: compiler.ApplyToImplicitArgs =>
              val txt = new String(sourceFile.content, v.pos.start, math.max(0, v.pos.end - v.pos.start)).trim()
              val argsStr = v.args.map(_.symbol.name).mkString("( ", ", ", " )")
              val ia = new ImplicitConversionsOrArgsAnnotation(
                scu.getCompilationUnit,
                ImplicitConversionsOrArgsAnnotation.KIND,
                false,
                "Implicit arguments found: " + txt + " => " + txt + argsStr)
              val pos = new org.eclipse.jface.text.Position(v.pos.start, txt.length)
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

  def getPreferenceStore(): IPreferenceStore = ScalaPlugin.plugin.getPreferenceStore()

}

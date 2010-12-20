/**
 * 
 */
package scala.tools.eclipse.ui.semantic.highlighting

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
 *
 */
class SemanticHighlightingPresenter(fEditor: CompilationUnitEditor, fSourceViewer: ISourceViewer)
  extends IDocumentListener with IPropertyChangeListener {

  import SemanticHighlightingPresenter._

  val annotationAccess = new IAnnotationAccess() {
    def getType(annotation: Annotation) = annotation.getType();
    def isMultiLine(annotation: Annotation) = true
    def isTemporary(annotation: Annotation) = true
  };
  val painter = new AnnotationPainter(fSourceViewer, annotationAccess);
  import scala.tools.eclipse.ui.preferences.ScalaEditorColoringPreferencePage._
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
  
  val impTextStyleStrategy = new ImplicitConversionsOrArgsTextStyleStrategy(fUnderlineStyle, fFontStyle_BLOD | fFontStyle_ITALIC);
  
  
  painter.addTextStyleStrategy(ImplicitConversionsOrArgsAnnotation.KIND,
    impTextStyleStrategy);
  painter.addAnnotationType(ImplicitConversionsOrArgsAnnotation.KIND, ImplicitConversionsOrArgsAnnotation.KIND);
  painter.setAnnotationTypeColor(ImplicitConversionsOrArgsAnnotation.KIND,
    ColorManager.getDefault.getColor(fColorValue))
  fSourceViewer.asInstanceOf[org.eclipse.jface.text.TextViewer].addPainter(painter);
  fSourceViewer.asInstanceOf[org.eclipse.jface.text.TextViewer].addTextPresentationListener(painter);

  
  override def propertyChange(event: PropertyChangeEvent){
	  event.getProperty()  match {
	 	  case P_BLOD => fFontStyle_BLOD =  event.getNewValue().asInstanceOf[Boolean] 
	 	                                      match { case true => SWT.BOLD;case _ => SWT.NORMAL }
	 	  case P_ITALIC => fFontStyle_ITALIC = event.getNewValue().asInstanceOf[Boolean] 
	 	                                      match { case true => SWT.ITALIC;case _ => SWT.NORMAL}
	 	  case P_COLOR => fColorValue = event.getNewValue().asInstanceOf[org.eclipse.swt.graphics.RGB]
	 	  case P_UNDERLINE => fUnderlineStyle = event.getNewValue().asInstanceOf[String] 
	 	                                      match { case "1" => SWT.UNDERLINE_SQUIGGLE
	 	 	                                          case "2" => SWT.UNDERLINE_DOUBLE
	 	 	                                          case "3" => SWT.UNDERLINE_SINGLE
	 	 	                                          case "4" => 8}
	  }
	  impTextStyleStrategy.setUnderlineStyle(fUnderlineStyle)
	  impTextStyleStrategy.setFontStyle(fFontStyle_BLOD | fFontStyle_ITALIC)
	  painter.setAnnotationTypeColor(ImplicitConversionsOrArgsAnnotation.KIND, 
	 	ColorManager.getDefault.getColor(fColorValue))
	  update()
  }
  
  
  /**
   * The manipulation described by the document event will be performed.
   *
   * @param event the document event describing the document change
   */
  override def documentAboutToBeChanged(event: DocumentEvent) {}

  /**
   * The manipulation described by the document event has been performed.
   *
   * @param event the document event describing the document change
   */
  override def documentChanged(event: DocumentEvent) {
    update()
  }

  def update() {
    val cu = JavaPlugin.getDefault().getWorkingCopyManager().getWorkingCopy(fEditor.getEditorInput());
    val scu = cu.asInstanceOf[ScalaCompilationUnit]
    scu.withSourceFile{ (sourceFile, compiler) =>

      val toAdds = new java.util.HashMap[Annotation, org.eclipse.jface.text.Position]
      var toAddAnnotations = List[Annotation]()
      object viewsCollector extends compiler.Traverser {
        override def traverse(t: compiler.Tree): Unit = t match {
          case v: compiler.ApplyImplicitView =>
            val txt = fSourceViewer.getDocument().get(v.pos.start, v.pos.end - v.pos.start).trim()
            val ia = new ImplicitConversionsOrArgsAnnotation(cu,ImplicitConversionsOrArgsAnnotation.KIND,
              false,
              "Implicit conversions found: "+txt+" => "+v.fun.symbol.name+"("+txt+")")
            val pos = new org.eclipse.jface.text.Position(v.pos.start, v.pos.end - v.pos.start);
            toAdds.put(ia, pos)
            toAddAnnotations = ia :: toAddAnnotations
            super.traverse(t)
          case v: compiler.ApplyToImplicitArgs =>
            val txt = fSourceViewer.getDocument().get(v.pos.start, v.pos.end - v.pos.start).trim()
            val sb = new StringBuilder()
            for (arg <- v.args) sb.append(arg.symbol.name).append(",")
            val ia = new ImplicitConversionsOrArgsAnnotation(cu,ImplicitConversionsOrArgsAnnotation.KIND,
              false,
              "Implicit arguments found: "+txt+" => "+txt+"("+sb.deleteCharAt(sb.length()-1).toString()+")")
            val pos = new org.eclipse.jface.text.Position(v.pos.start, v.pos.end - v.pos.start);
            toAdds.put(ia, pos)
            toAddAnnotations = ia :: toAddAnnotations
            super.traverse(t)
          case _ =>
            super.traverse(t)
        }
      }
      viewsCollector.traverse(compiler.body(sourceFile))

      val model = fSourceViewer.getAnnotationModel()
      if (model != null) model.asInstanceOf[IAnnotationModelExtension].replaceAnnotations(currentAnnotations.toArray, toAdds);
      currentAnnotations = toAddAnnotations
    }
  }

  def getPreferenceStore(): IPreferenceStore = {
    scala.tools.eclipse.ScalaPlugin.plugin.getPreferenceStore()
  }

}

object SemanticHighlightingPresenter {
	//XXX: workaround for current cache related bugs, like bug#1000103 
	var currentAnnotations: List[Annotation] = List()
}
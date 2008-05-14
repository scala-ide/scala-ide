/*
 * Copyright 2005-2008 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package lampion.eclipse
import org.eclipse.ui.texteditor._
import org.eclipse.jface.text.source._
import scala.collection.jcl._
import org.eclipse.swt.widgets._

abstract class SelectRulerAction extends AbstractRulerActionDelegate {
  def plugin : UIPlugin

  protected def appendix(a : Annotation) = special(a) match {
  case Some(a) => a.actionId
  case _ => "GotoAnnotation."
  }
  protected def dispatch(editor : Editor, a : Annotation) : Boolean = special(a) match {
    case Some(a) => a.dispatch(editor); true
    case _ => false
  }
  def special(a : Annotation) : Option[Special] = None
  trait Special extends Annotation {
    def actionId : String
    def dispatch(editor : Editor) : Unit  
  }
  
  class SelectAnnotationRulerAction(editor : Editor, ruler : IVerticalRulerInfo) extends 
    SelectMarkerRulerAction(plugin.bundle, "Ruler.", editor, ruler) {
    var annotation : Option[Annotation] = None
    override def update = {
      findAnnotation
      setEnabled(true)
      if (annotation.isDefined)
        initialize(plugin.bundle, "SelectAnnotationRulerAction." + appendix(annotation.get))
      super.update
    }
    override def run = runWithEvent(null)
    override def runWithEvent(event : Event) = {
      if (!annotation.isDefined || !dispatch(editor, annotation.get))
        super.run
    }
    def findAnnotation : Unit = {
      val model = getAnnotationModel
      val annotationAccess= getAnnotationAccessExtension
      val doc = getDocument
      if (model == null) return
      val i : java.util.Iterator[Annotation] = model.getAnnotationIterator.asInstanceOf[java.util.Iterator[Annotation]]
      annotation = None
      while (i.hasNext) {
        val a = i.next.asInstanceOf[Annotation]
        if (!a.isMarkedDeleted && includesRulerLine(model.getPosition(a), doc)) {
          annotation = Some(a)
        }
      }
    }
  }
  override protected def createAction(editor : ITextEditor, rulerInfo : IVerticalRulerInfo) = editor match {
    case editor : Editor if editor.plugin == plugin => new SelectAnnotationRulerAction(editor, rulerInfo)
    case _ => null
  }
}

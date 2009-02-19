/*
 * Copyright 2005-2009 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.eclipse

import org.eclipse.ui.texteditor.{ AbstractRulerActionDelegate, ITextEditor, SelectMarkerRulerAction }
import org.eclipse.jface.text.source.{ Annotation, IVerticalRulerInfo }
import org.eclipse.swt.widgets.Event
  
class SelectRulerAction extends AbstractRulerActionDelegate {
  
  def plugin = ScalaPlugin.plugin // i don't understand....
  
  protected def appendix(a : Annotation) = special(a) match {
  case Some(a) => a.actionId
  case _ => "GotoAnnotation."
  }
  protected def dispatch(editor : Editor, a : Annotation) : Boolean = special(a) match {
    case Some(a) => a.dispatch(editor); true
    case _ => false
  }

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
  
  def special(a : Annotation) : Option[Special] = {
    if (a.getType == plugin.OverrideIndicator) Some(new Special {
      override def actionId = "OpenSuperImplementation."
      override def dispatch(editor : Editor) = {
        val plugin = ScalaPlugin.plugin
        val file0 = editor.file
        if (!file0.isEmpty) {
          val file1 : plugin.File = file0.get.asInstanceOf[plugin.File]
          val external : plugin.ExternalFile = file1.external
          val project : plugin.Project = external.project
          val file = external.file
          import project.compiler._
          /* XXX: redo...
            val sym = generateIdeMaps.url2sym(a.getText)(null)
            if (sym != null && sym != NoSymbol)
              file.open(generateIdeMaps.External(sym)).foreach(_.apply)
*/
          () 
        }
      }
    }) else None
  }
}

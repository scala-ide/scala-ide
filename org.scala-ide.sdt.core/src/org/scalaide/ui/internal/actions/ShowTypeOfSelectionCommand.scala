package org.scalaide.ui.internal.actions

import org.scalaide.ui.internal.editor.ScalaSourceFileEditor
import org.eclipse.core.commands.AbstractHandler
import org.eclipse.core.commands.ExecutionEvent
import org.eclipse.jface.text.ITextSelection
import org.eclipse.ui.handlers.HandlerUtil
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility
import org.scalaide.core.internal.jdt.model.ScalaCompilationUnit
import org.eclipse.jface.text.information.IInformationProvider
import org.eclipse.jface.text.ITextViewer
import org.eclipse.jface.text.Region
import org.eclipse.jface.text.IRegion
import org.scalaide.util.internal.eclipse.EclipseUtils._

class ShowTypeOfSelectionCommand extends AbstractHandler {

  override def execute(event: ExecutionEvent): Object = {
    HandlerUtil.getActiveEditor(event) match {
      case scalaEditor: ScalaSourceFileEditor =>
        scalaEditor.getSelectionProvider.getSelection match {
          case sel: ITextSelection =>
            scalaEditor.typeOfExpressionPresenter.showInformation()
          case _ => ()
        }
      case _ => ()
    }
    null
  }

}

object TypeOfExpressionProvider extends IInformationProvider {
  def getSubject(textViewer: ITextViewer, offset: Int): IRegion = {
    val r = textViewer.getSelectedRange
    new Region(r.x, r.y)
  }

  def getInformation(textViewer: ITextViewer, region: IRegion): String = {

    EditorUtility.getActiveEditorJavaInput match {
      case scu: ScalaCompilationUnit =>
        scu.withSourceFile { (src, compiler) =>
          import compiler._

          def typeInfo(tpe: Type): String =
            Option(tpe).map(_.toString).getOrElse(null)

          val response = new Response[Tree]
          askTypeAt(region.toRangePos(src), response)
          (for {
            t <- response.get.left.toOption
          } yield t match {
            case ValDef(_, _, _, rhs) =>
              typeInfo(rhs.tpe)
            case DefDef(_, _, _, _, _, rhs) =>
              typeInfo(rhs.tpe)
            case _ =>
              typeInfo(t.tpe)
          }).getOrElse(null)
        }.orNull

      case _ => null
    }
  }
}

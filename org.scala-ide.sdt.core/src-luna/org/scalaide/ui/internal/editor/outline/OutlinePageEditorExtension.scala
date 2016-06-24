package org.scalaide.ui.internal.editor.outline
import org.scalaide.ui.internal.editor.ScalaCompilationUnitEditor
import org.eclipse.jface.viewers.ISelection
import org.eclipse.jface.viewers.IStructuredSelection

trait OutlinePageEditorExtension extends ScalaCompilationUnitEditor {

  private var outlinePage: ScalaOutlinePage = null
  def getOutlinePage = outlinePage
  override def getAdapter(required: Class[_]): AnyRef = {
    required match {
      case OutlinePageEditorExtension.IContentOutlinePage ⇒ {
        if (outlinePage == null)
          outlinePage = createScalaOutlinePage
        outlinePage
      }
      case _ => super.getAdapter(required)
    }

  }

  private def createScalaOutlinePage: ScalaOutlinePage = {
    val sop = new ScalaOutlinePage(this)
    sop
  }

  override def doSelectionChanged(selection: ISelection) = {
    selection match {
      case ss: IStructuredSelection =>
        if (!ss.isEmpty()) {
          ss.getFirstElement match {
            case n: Node => selectAndReveal(n.start, n.end - n.start)
            case _ => super.doSelectionChanged(selection)
          }
        }
    }
  }

}

object OutlinePageEditorExtension {
  val IContentOutlinePage = classOf[org.eclipse.ui.views.contentoutline.IContentOutlinePage]
}

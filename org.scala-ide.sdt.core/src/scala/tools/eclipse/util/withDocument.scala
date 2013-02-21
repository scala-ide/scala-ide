package scala.tools.eclipse.util

import org.eclipse.jface.text.source.ISourceViewer
import org.eclipse.jface.text.IDocument

/** Given a `sourceViewer` it applies `f` on the underlying document's model.*/
object withDocument {
  def apply[T](sourceViewer: ISourceViewer)(f: IDocument => T): Option[T] = {
    for {
      view <- Option(sourceViewer)
      document <- Option(view.getDocument)
      res <- Option(f(document)) // if `f` returns `null`, it gets converted into None
    } yield res
  }
}
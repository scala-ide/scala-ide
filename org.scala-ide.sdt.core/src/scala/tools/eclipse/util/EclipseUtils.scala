package scala.tools.eclipse.util

import org.eclipse.text.edits.{ TextEdit => EclipseTextEdit, _ }
import scalariform.utils.TextEdit
import org.eclipse.jface.text.IDocumentExtension4
import org.eclipse.jface.text.IDocument
import org.eclipse.core.runtime.IAdaptable

import PartialFunction._

object EclipseUtils {

  implicit def adaptableToPimpedAdaptable(adaptable: IAdaptable): PimpedAdaptable = new PimpedAdaptable(adaptable)

  class PimpedAdaptable(adaptable: IAdaptable) {

    def adaptTo[T](implicit m: Manifest[T]): T = adaptable.getAdapter(m.erasure).asInstanceOf[T]

    def adaptToSafe[T](implicit m: Manifest[T]): Option[T] = Option(adaptable.getAdapter(m.erasure).asInstanceOf[T])

  }

  implicit def documentToPimpedDocument(document: IDocument): PimpedDocument = new PimpedDocument(document)

  class PimpedDocument(document: IDocument) {

    def apply(offset: Int): Character = document.getChar(offset)

  }

  implicit def asEclipseTextEdit(edit: TextEdit): EclipseTextEdit = new ReplaceEdit(edit.position, edit.length, edit.replacement)

}
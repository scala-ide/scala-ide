package scala.tools.eclipse.util

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

    def defaultLineDelimiter: Option[String] = condOpt(document) {
      case d4: IDocumentExtension4 => d4.getDefaultLineDelimiter
    }

    def apply(offset: Int): Character = document.getChar(offset)
    
  }

}
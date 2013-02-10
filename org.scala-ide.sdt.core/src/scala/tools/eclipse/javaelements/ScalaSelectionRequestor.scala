package scala.tools.eclipse.javaelements

import org.eclipse.jdt.core.IJavaElement
import org.eclipse.jdt.internal.core.{ NameLookup, Openable, SelectionRequestor }

class ScalaSelectionRequestor(nameLookup : NameLookup, openable : Openable) extends SelectionRequestor(nameLookup, openable) {
  override def addElement(elem : IJavaElement) =
    if (elem != null) super.addElement(elem)

  override def findLocalElement(pos : Int) =
    super.findLocalElement(pos)

  def hasSelection() = elementIndex >= 0
}

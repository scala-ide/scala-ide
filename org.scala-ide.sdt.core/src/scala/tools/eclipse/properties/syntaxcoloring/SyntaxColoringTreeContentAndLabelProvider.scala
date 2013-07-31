package scala.tools.eclipse.properties.syntaxcoloring

import scala.tools.eclipse.properties.syntaxcoloring.ScalaSyntaxClasses.Category

import org.eclipse.jface.viewers._

object SyntaxColoringTreeContentAndLabelProvider extends LabelProvider with ITreeContentProvider {

  def getElements(inputElement: AnyRef) = ScalaSyntaxClasses.categories.toArray

  def getChildren(parentElement: AnyRef) = parentElement match {
    case Category(_, children) => children.toArray
    case _ => Array()
  }

  def getParent(element: AnyRef): Category = ScalaSyntaxClasses.categories.find(_.children contains element).orNull

  def hasChildren(element: AnyRef) = getChildren(element).nonEmpty

  def inputChanged(viewer: Viewer, oldInput: AnyRef, newInput: AnyRef) {}

  override def getText(element: AnyRef) = element match {
    case Category(name, _) => name
    case ScalaSyntaxClass(displayName, _, _) => displayName
  }
}
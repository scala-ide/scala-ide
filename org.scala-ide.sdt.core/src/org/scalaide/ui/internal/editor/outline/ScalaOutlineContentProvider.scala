package org.scalaide.ui.internal.editor.outline

import org.eclipse.jface.viewers.ITreeContentProvider
import org.eclipse.jface.viewers.Viewer
import org.scalaide.core.internal.ScalaPlugin

class ScalaOutlineContentProvider extends ITreeContentProvider {
  var publicOnly = ScalaPlugin().getPreferenceStore().getBoolean("PublicOnlyAction.isChecked")
  val NONODES = new Array[Object](0)

  private def filter(nodes: Iterable[Node]) = {
    var result = nodes
    if (publicOnly)
      result = result.filter { n => !(n.isPrivate || n.isProtected) }
    result
  }
  override def getChildren(o: Object): Array[Object] = {
    o match {
      case p: ContainerNode => filter(p.children.values).toArray
      case _ => NONODES
    }
  }

  override def getParent(o: Object): Object = {
    o match {
      case c: Node => c.parent
      case _ => null
    }
  }

  override def hasChildren(n: Object): Boolean = {
    n match {
      case c: Node => !c.isLeaf
      case _ => throw new IllegalArgumentException("Node is expected")
    }
  }

  override def getElements(root: Object): Array[Object] = {

    root match {
      case r: RootNode => r.children.values.toArray
      case _ => throw new IllegalArgumentException("Root is expected")
    }
  }

  override def dispose() = {
  }

  override def inputChanged(arg0: Viewer, arg1: Object, arg2: Object) = {
  }
}
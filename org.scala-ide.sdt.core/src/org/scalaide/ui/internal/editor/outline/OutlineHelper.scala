package org.scalaide.ui.internal.editor.outline

import org.eclipse.jface.viewers.TreeViewer

object OutlineHelper {
  def foldImportNodes(viewer: TreeViewer, input: Any): Unit = {
    input match {
      case n: ImportsNode => viewer.collapseToLevel(n, 1)
      case n: ContainerNode => n.children.values.foreach { x => foldImportNodes(viewer, x) }
      case _ =>
    }
  }
}
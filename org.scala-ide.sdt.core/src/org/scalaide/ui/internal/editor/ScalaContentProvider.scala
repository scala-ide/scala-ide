package org.scalaide.ui.internal.editor
import org.eclipse.jface.viewers.ITreeContentProvider
import org.eclipse.jface.viewers.Viewer




/**
 * @author ailinykh
 */
class ScalaContentProvider extends ITreeContentProvider {

  val NONODES = new Array[Object](0)
  /**
   * Gets the children of the specified object
   *
   * @param arg0
   *            the parent object
   * @return Object[]
   */
  override def  getChildren( o:Object):Array[Object]= {
    o match {
      case p:ContainerNode => p.getChildren.values.toArray
      case _ => NONODES
    }
  }

  /**
   * Gets the parent of the specified object
   *
   * @param arg0
   *            the object
   * @return Object
   */
  override def  getParent( o:Object):Object = {
    o match {
      case c:Node => c.parent
      case _ => null
    }
  }

  /**
   * Returns whether the passed object has children
   *
   * @param arg0
   *            the parent object
   * @return boolean
   */
  override def hasChildren(n: Object ):Boolean= {
    n match {
      case c:Node => !c.isLeaf
      case _ => throw new IllegalArgumentException("Node is expected")
    }
  }

  /**
   * Gets the root element(s) of the tree
   *
   * @param arg0
   *            the input data
   * @return Object[]
   */
  override def getElements( root:Object):Array[Object] ={

    root match {
      case r:RootNode => r.getChildren.values.toArray
      case _ => throw new IllegalArgumentException("Root is expected")
    }
  }

  override def dispose() ={
  }

  /**
   * Called when the input changes
   *
   * @param arg0
   *            the viewer
   * @param arg1
   *            the old input
   * @param arg2
   *            the new input
   */
  override def inputChanged( arg0:Viewer,  arg1:Object,  arg2:Object)= {
  }
}
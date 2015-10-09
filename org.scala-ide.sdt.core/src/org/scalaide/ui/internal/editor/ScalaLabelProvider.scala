package org.scalaide.ui.internal.editor

/**
 * @author ailinykh


 */
import org.eclipse.jface.viewers.ILabelProvider
import java.io.FileInputStream
import org.eclipse.swt.graphics.Image
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.scalaide.core.internal.ScalaPlugin

class ScalaLabelProvider extends ILabelProvider{


    /**
   * Gets the image to display for a node in the tree
   *
   * @param arg0
   *            the node
   * @return Image
   */
  override def  getImage( o:Object):Image= {
    import org.scalaide.ui.ScalaImages._

    val reg = ScalaPlugin().imageDescriptorRegistry
    o match {
      case n:ClassNode => if(n.isTrait) reg.get( SCALA_TRAIT) else reg.get( SCALA_CLASS)
      case n:ValNode => if(n.isPrivate) reg.get( PRIVATE_VAL) else if(n.isProtected) reg.get(PROTECTED_VAL) else reg.get(PUBLIC_VAL)
      case n:VarNode => if(n.isPrivate) reg.get( PRIVATE_VAR) else if(n.isProtected) reg.get(PROTECTED_VAR) else reg.get(PUBLIC_VAR)
      case n:MethodNode => if(n.isPrivate) reg.get( PRIVATE_DEF) else if(n.isProtected) reg.get(PROTECTED_DEF) else reg.get(PUBLIC_DEF)
      case n:ObjectNode => reg.get(SCALA_OBJECT)
      case n:PackageNode => reg.get(PACKAGE)
      case _ => null
    }
  }

  /**
   * Gets the text to display for a node in the tree
   *
   * @param arg0
   *            the node
   * @return String
   */
  override def  getText( o:Object):String ={
    def renderArgList(sb:StringBuilder, args:List[List[String]])={
      args.foreach(list =>{
        sb.append("(")
        list.foreach { s => sb.append(s); sb.append(",") }
        if(!list.isEmpty)
          sb.setLength(sb.length-1)
        sb.append(")")
        })
    }
    val u = ""//System.currentTimeMillis()
    o match {
      case c:PackageNode => c.displayName+ " "+u
      case c:ClassNode => c.displayName+ " "+u
      case c:ValNode => c.displayName + " "+u
      case c:VarNode => c.displayName + " "+u
      case c:ObjectNode => c.displayName+ " "+u
      case c:MethodNode => {
        val sb = new StringBuilder
        if(!c.argTypes.isEmpty)
          renderArgList(sb, c.argTypes)
        c.displayName+sb.toString() + c.returnType.map(":"+_).getOrElse("")+" "+u
        }
      case _ => ""
    }

  }

  /**
   * Adds a listener to this label provider
   *
   * @param arg0
   *            the listener
   */
  override def addListener( arg0:ILabelProviderListener) ={

  }

  /**
   * Called when this LabelProvider is being disposed
   */
  override def dispose()= {

  }

  /**
   * Returns whether changes to the specified property on the specified
   * element would affect the label for the element
   *
   * @param arg0
   *            the element
   * @param arg1
   *            the property
   * @return boolean
   */
  override def  isLabelProperty( arg0:Object,  arg1:String):Boolean = {
     false
  }

  /**
   * Removes the listener
   *
   * @param arg0
   *            the listener to remove
   */
  override def removeListener( arg0:ILabelProviderListener) ={

  }
}
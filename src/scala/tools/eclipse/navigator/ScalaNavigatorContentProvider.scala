/*
 * Copyright 2005-2009 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.eclipse.navigator
import org.eclipse.jdt.internal.ui.navigator._
import org.eclipse.jdt.core._

class ScalaNavigatorContentProvider extends JavaNavigatorContentProvider {
  override def getChildren(parentElement : AnyRef) : Array[AnyRef] = {
    super.getChildren(parentElement)
  }
  override protected def getPackageContent(fragment : IPackageFragment) : Array[AnyRef] = {
    val ret = super.getPackageContent(fragment)
    if (ret.length == 1 && ret(0) == null) {
       
      new Array[AnyRef](0) 
    } else ret
  }

}

/*
 * Copyright 2005-2009 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.eclipse.debug;
import org.eclipse.jdt.internal.debug.ui._
import org.eclipse.jdt.internal.debug.core.breakpoints._
import org.eclipse.jdt.internal.core._
import org.eclipse.jdt.core._
import org.eclipse.jdt.internal.compiler.env._
import org.eclipse.ui._

class ScalaModelPresentation extends JDIModelPresentation {
  // HACK!!!!!!!!!
  override def getEditorInput(that : AnyRef) = {
    if (ScalaUIPlugin.plugin != null) 
      ScalaUIPlugin.plugin.inputFor(that) getOrElse (that match {
      case that : JavaLineBreakpoint => 
        val tpe = that.getMarker().getAttribute(JDIDebugUIPlugin.getUniqueIdentifier + ".JAVA_ELEMENT_HANDLE_ID", null)
        if (tpe == null) super.getEditorInput(that)
        else ScalaUIPlugin.plugin.inputFor(JavaCore.create(tpe)) getOrElse super.getEditorInput(that)
      case that => super.getEditorInput(that)
      })
    else super.getEditorInput(that)
  } 
  override def  getEditorId(input : IEditorInput, that : AnyRef) : String = {
    val plugin = ScalaUIPlugin.plugin
    if (plugin != null) input match {
    case that : plugin.ClassFileInput => return plugin.editorId
    case _ => 
    }
    super.getEditorId(input, that)
  }
}

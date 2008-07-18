/*
 * Copyright 2005-2008 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.eclipse

class Editor extends { val plugin = Driver.driver } with lampion.eclipse.Editor {
  
  import org.eclipse.ui._
  import org.eclipse.ui.editors.text._
  override def doSetInput(input : IEditorInput) = {
    input match {
      case null =>
      case input : plugin.ClassFileInput =>
        setDocumentProvider(new plugin.ScalaClassFileDocumentProvider)
      case _ =>
        setDocumentProvider(new plugin.DocumentProvider)
    }
    super.doSetInput(input)
  }
}

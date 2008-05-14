/*
 * Copyright 2005-2008 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.eclipse;
import lampion.eclipse._;
import org.eclipse.core.runtime._;
import org.eclipse.core.resources._;

class Editor extends lampion.eclipse.Editor {
  override val plugin = {
    assert(Driver.driver != null)
    Driver.driver 
  }
  intializeAfterPlugin
  
  protected def BUNDLE = java.util.ResourceBundle.getBundle("scala.tools.eclipse.messages")
}

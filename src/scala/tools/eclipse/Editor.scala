/*
 * Copyright 2005-2008 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.eclipse;

class Editor extends { override val plugin = Driver.driver } with lampion.eclipse.Editor {
  intializeAfterPlugin
}

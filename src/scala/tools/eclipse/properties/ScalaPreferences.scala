/*
 * Copyright 2005-2009 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.eclipse.properties
import org.eclipse.jface._
import org.eclipse.jface.util._
import org.eclipse.jface.preference._
import org.eclipse.ui._
import org.eclipse.swt.widgets._
import org.eclipse.swt.layout._
import org.eclipse.swt.events._
import org.eclipse.swt.SWT
import scala.collection.jcl._
import org.eclipse.swt.graphics._

class ScalaPreferences extends PreferencePage with IWorkbenchPreferencePage {
  def init(wb : IWorkbench) : Unit = {}
  def createContents(parent : Composite) = new Label(parent, 0)
}

/*
 * Copyright 2005-2009 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.eclipse.net;
import org.eclipse.jface._
import org.eclipse.jface.util._
import org.eclipse.jface.preference._
import org.eclipse.ui._
import org.eclipse.swt.widgets._
import org.eclipse.swt.custom._
import org.eclipse.swt.layout._
import org.eclipse.swt.events._
import org.eclipse.swt.SWT
import scala.collection.jcl._
import org.eclipse.swt.graphics._
import scala.xml._

// TODO: allow for configuration of the global assembly cache. 
abstract class EditorPreferences extends org.eclipse.jface.preference.PreferencePage with IWorkbenchPreferencePage {
  
}

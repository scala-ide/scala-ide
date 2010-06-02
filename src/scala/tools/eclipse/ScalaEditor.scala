/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import scala.tools.eclipse.contribution.weaving.jdt.ui.javaeditor.IScalaEditor
import scala.tools.eclipse.lexical.ScalaPartitionScanner
import org.eclipse.jface.text.rules.IPartitionTokenScanner

trait ScalaEditor extends IScalaEditor {
  override def getPartitionScanner() : IPartitionTokenScanner = {
    new ScalaPartitionScanner
  }
}

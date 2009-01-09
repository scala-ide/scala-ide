/*
 * Copyright 2005-2009 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.eclipse.net;
import org.eclipse.ui.wizards.newresource._
import org.eclipse.ui._
import org.eclipse.ui.ide._
import org.eclipse.jface.wizard._
import org.eclipse.jface.viewers._
import org.eclipse.jdt.core._
import scala.collection.jcl._
import org.eclipse.swt.widgets._
import org.eclipse.swt.layout._
import org.eclipse.swt.SWT
import org.eclipse.core.resources._
import org.eclipse.core.runtime._
import org.eclipse.jdt.internal.core._
class NewProjectWizard extends BasicNewProjectResourceWizard {
  override def addPages = {
    super.addPages
    getStartingPage.setDescription("Create a new Scala.NET project")
    getStartingPage.setTitle("New Scala.NET Project")
    setWindowTitle("New Scala.NET Project")
  }
  override def performFinish : Boolean = super.performFinish && ScalaPlugin.plugin.check{
    val desc = getNewProject.getDescription
    val natures = JavaCore.NATURE_ID :: ScalaPlugin.plugin.natureId :: ScalaPlugin.plugin.msilNatureId :: desc.getNatureIds.toList
    desc.setNatureIds(natures.reverse.toArray)
    getNewProject.setDescription(desc, null)
    true
  }.getOrElse(false)
}

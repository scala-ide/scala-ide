/*
 * Copyright 2005-2009 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.eclipse.wizards

import org.eclipse.core.resources._
import org.eclipse.jdt.core._
import org.eclipse.jdt.launching._
import org.eclipse.debug.core._
import org.eclipse.debug.ui._

import scala.collection.JavaConversions._

class NewApplicationWizard extends NewObjectWizard {
  override def adjective = "Application"
  override def body = """  def main(args : Array[String]) : Unit = {}"""
  override protected def postFinish(project : ScalaPlugin#Project, file : IFile) = {
    super.postFinish(project, file)
    val toRun = this.pkg.getElementName + "." +this.name
    val launchName = DebugPlugin.getDefault.getLaunchManager().generateUniqueLaunchConfigurationNameFrom(toRun)
    val launchType = DebugPlugin.getDefault().getLaunchManager().getLaunchConfigurationType(ScalaPlugin.plugin.launchTypeId)
    val wc = launchType.newInstance(null, name)
    wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, project.underlying.getName)
    wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME, toRun)

    val groups = new scala.collection.mutable.ArrayBuffer[AnyRef]
    groups += IDebugUIConstants.ID_RUN_LAUNCH_GROUP
    wc.setAttribute(IDebugUIConstants.ATTR_FAVORITE_GROUPS, groups)
	      
    val config = wc.doSave
    assert(config != null)
    //config.launch(ILaunchManager.RUN_MODE, null)
  }
}

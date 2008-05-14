/*
 * Copyright 2005-2008 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package lampion.eclipse

import org.eclipse.core.resources.{IProjectNature,IProject,IResource}


abstract class Nature extends IProjectNature {
  protected def plugin : Plugin
  private var project : IProject = _
  override def getProject = project
  override def setProject(project : IProject) = this.project = project
  protected def requiredBuilders : Seq[String] = Nil
  protected def unrequiredBuilders : Seq[String] = Nil
  override def configure : Unit = {
    if (project == null || !project.isOpen) {
      plugin.logError("", null); return
    }
    plugin.check {
      val desc = project.getDescription
      val spec = desc.getBuildSpec
      val spec0 = new scala.collection.mutable.ListBuffer ++ spec
      requiredBuilders.foreach{bld => 
        if (!spec0.elements.exists(_.getBuilderName == bld)) {
          val cmd = desc.newCommand
          cmd.setBuilderName(bld)
          spec0 += cmd
        }
      }
      unrequiredBuilders.foreach{bld => 
        val x = spec0.findIndexOf(_.getBuilderName == bld)
        if (x != -1) spec0.remove(x)
      }
      desc.setBuildSpec(spec0.toArray)
      project.setDescription(desc, IResource.FORCE, null)
    }
  }
  override def deconfigure : Unit = {
    if (project == null || !project.isOpen) {
      plugin.logError("", null); return
    }
    plugin.check {
      val desc = project.getDescription
      val spec = desc.getBuildSpec.filter(b => requiredBuilders contains b.getBuilderName)
      desc.setBuildSpec(spec)
      project.setDescription(desc, IResource.FORCE, null)
    }
  }
  
}

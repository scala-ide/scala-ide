/*
 * Copyright 2005-2008 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package lampion.eclipse

import org.eclipse.core.resources.{ ICommand, IProject, IProjectNature, IResource}

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
      val spec =
        requiredBuilders.map(b => { val cmd = desc.newCommand ; cmd.setBuilderName(b) ; cmd }) ++
        desc.getBuildSpec.filter(b => !(requiredBuilders contains b.getBuilderName) && !(unrequiredBuilders contains b.getBuilderName))
      desc.setBuildSpec(spec.toArray)
      project.setDescription(desc, IResource.FORCE, null)
    }
  }
  
  override def deconfigure : Unit = {
    //TODO - Do we need to release resources?
    
    if (project == null || !project.isOpen) {
      plugin.logError("", null); return
    }
    plugin.check {
      val desc = project.getDescription
      val spec = desc.getBuildSpec.filter(b => !(requiredBuilders contains b.getBuilderName) )
      desc.setBuildSpec(spec)
      project.setDescription(desc, IResource.FORCE, null)
    }
  }
}

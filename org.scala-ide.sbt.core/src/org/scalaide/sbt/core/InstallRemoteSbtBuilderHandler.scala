package org.scalaide.sbt.core

import org.scalaide.util.internal.Utils

import org.eclipse.core.commands.AbstractHandler
import org.eclipse.core.commands.ExecutionEvent
import org.eclipse.core.resources.IProject
import org.eclipse.core.resources.IResource
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jface.viewers.TreeSelection
import org.eclipse.ui.handlers.HandlerUtil

class InstallRemoteSbtBuilderHandler extends AbstractHandler{

  override def execute(event: ExecutionEvent): AnyRef = {

    HandlerUtil.getCurrentSelection(event) match {
      case selection: TreeSelection =>
        val project = selection.getFirstElement() match {
          case project: IProject =>
            project
          case javaProject: IJavaProject =>
            javaProject.getProject()
          case e =>
            throw new RuntimeException(s"InstallRemoteSbtBuilder not called on a valid project: $e")
        }

        if (project.isOpen) {
          switchBuilder(project)
        }
    }
    null // from javadoc spec
  }
  
  private def switchBuilder(project: IProject) {
     Utils tryExecute {
      
      val builderToRemove = "org.scala-ide.sdt.core.scalabuilder"
      val builderToAdd = "org.scala-ide.sbt.core.remoteBuilder"
       
      val description = project.getDescription
      val previousCommands = description.getBuildSpec
      val filteredCommands = previousCommands.filterNot(builderToRemove == _.getBuilderName)
      val newCommands = if (filteredCommands.exists(_.getBuilderName == builderToAdd))
        filteredCommands
      else
        filteredCommands :+ {
          val newBuilderCommand = description.newCommand;
          newBuilderCommand.setBuilderName(builderToAdd);
          newBuilderCommand
        }
      description.setBuildSpec(newCommands)
      project.setDescription(description, IResource.FORCE, null)
    }
  }

}
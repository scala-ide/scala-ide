package scala.tools.eclipse.launching

import scala.tools.eclipse.ScalaProject
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.Status
import scala.tools.eclipse.ScalaPlugin
import org.eclipse.core.resources.IResource
import org.eclipse.core.runtime.IPath

object MainClassVerifier {
  private final val ModuleClassSuffix = "$"

  trait ErrorReporter {
    def report(msg: String): Unit
  }
}

class MainClassVerifier(reporter: MainClassVerifier.type#ErrorReporter) {
  /**
   * Verify that the classfile of the fully-qualified {{{mainTypeName}}} can be found.
   * @project The scala project containing the main type.
   * @typeName The fully-qualified main type name.
   */
  def execute(project: ScalaProject, mainTypeName: String): IStatus = {
    val status = canRunMain(project, mainTypeName)
    if (!status.isOK) reporter.report(status.getMessage())
    status
  }

  private def canRunMain(project: ScalaProject, mainTypeName: String): IStatus = {
    val mainClass = findClassFile(project, mainTypeName)
    def mainModuleClass = findClassFile(project, mainTypeName + MainClassVerifier.ModuleClassSuffix)

    if(mainClass.isEmpty) mainTypeCannotBeLocated(project, mainTypeName)
    else if (mainModuleClass.isEmpty) new Status(IStatus.ERROR, ScalaPlugin.plugin.pluginId, s"${mainTypeName} needs to be an `object` (it is currently a `class`).")
    else new Status(IStatus.OK, ScalaPlugin.plugin.pluginId, "")
  }

  private def findClassFile(project: ScalaProject, mainTypeName: String): Option[IResource] = {
    val outputLocations = project.outputFolders
    val classFileName = mainTypeName.replace('.', '/')
    (for {
      outputLocation <- outputLocations
      classFileLocation = outputLocation.append(s"${classFileName}.class")
      classFile <- Option(ScalaPlugin.plugin.workspaceRoot.findMember(classFileLocation))
    } yield classFile).headOption
  }

  private def mainTypeCannotBeLocated(project: ScalaProject, mainTypeName: String): IStatus = {
    val projectName = project.underlying.getName
    val sb = new StringBuilder
    sb append ((s"Cannot locate main type '${mainTypeName}' in project '${projectName}'."))
    for (mainClassName <- mainTypeName.split('.').lastOption.map(_.mkString)) {
      sb append {
        s""" Check your Run Configuration and make sure that the value of \"Main class\" is in sync with the package and type name declared in class '${mainClassName}'."""
      }
    }

    val errMsg = sb.toString
    new Status(IStatus.ERROR, ScalaPlugin.plugin.pluginId, errMsg)
  }
}
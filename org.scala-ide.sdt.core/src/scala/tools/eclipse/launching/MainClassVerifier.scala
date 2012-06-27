package scala.tools.eclipse.launching

import scala.tools.eclipse.ScalaProject

object MainClassVerifier {
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
  def execute(project: ScalaProject, mainTypeName: String): Boolean = {
    // 1. No binaries are produced if the project contains compilation errors.
    if (project.buildManager.hasErrors) {
      projectHasBuildErrors(project.underlying.getName)
      return false
    }

    // Try to locate the type
    val element = project.javaProject.findType(mainTypeName)

    // 2. The main type won't be found if the provided ``mainTypeName`` (fully-qualified name) doesn't 
    //    reference an existing type. (this is a workaround for #1000541).
    if (element == null) {
      mainTypeCannotBeLocated(project.underlying.getName, mainTypeName)
      return false
    }

    true
  }

  private def projectHasBuildErrors(projectName: String): Unit = {
    val errMsg = "Project '%s' contains compilation errors (therefore, no binaries have been produced).".format(projectName)
    reporter.report(errMsg)
  }

  private def mainTypeCannotBeLocated(projectName: String, mainTypeName: String): Unit = {
    val errMsg = ("Cannot locate main type '%s' in project '%s'. For this to work, the package name in " +
      "the source needs to match the source's physical location.\n" +
      "Hint: Move the source file containing the main type '%s' in folder '%s'."
      ).format(mainTypeName, projectName, mainTypeName, mainTypeName.split('.').init.mkString("/"))
    reporter.report(errMsg)
  }
}
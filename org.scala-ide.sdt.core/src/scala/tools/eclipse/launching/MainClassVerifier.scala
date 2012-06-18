package scala.tools.eclipse.launching

import scala.Array.canBuildFrom
import scala.tools.eclipse.ScalaProject

import org.eclipse.core.runtime.IPath
import org.eclipse.core.runtime.Path

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
    //    reference an existing type.
    //    This is basically an error in the Run Configuration. Indeed, the same issue can happen in 
    //    Java, but we try to provide the user with hints to quickly resolve the issue.
    if (element == null) {
      mainTypeCannotBeLocated(project.underlying.getName, mainTypeName)
      return false
    }

    // 3. Check that the package declaration matches the physical location of the source containing 
    //    the main type. (this is a workaround for #1000541). 
    val expectedPackage = element.getPackageFragment.getElementName
    val declaredPackage = element.getCompilationUnit.getPackageDeclarations.map(_.getElementName).headOption.getOrElse("")
    if (expectedPackage != declaredPackage) {
      val projectName = project.underlying.getName
      val mainSourceLocation = element.getCompilationUnit.getPath.makeRelativeTo(new Path(projectName))
      packageDeclarationOfMainDoesntMatch(projectName, mainSourceLocation, expectedPackage, declaredPackage)
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

  private def packageDeclarationOfMainDoesntMatch(projectName: String, mainSourceLocation: IPath, expectedPackage: String, declaredPackage: String): Unit = {
    val errMsg = new StringBuilder
    errMsg.append("Cannot locate class file for '%s' in project '%s'.".format(mainSourceLocation.toOSString, projectName))
    errMsg.append("\n")

    if (expectedPackage.isEmpty)
      errMsg.append("Hint: Remove the package declaration in '%s'.".format(mainSourceLocation.lastSegment))
    else {
      errMsg.append("Hint: Change the package name in '%s' ".format(mainSourceLocation.lastSegment))

      if (!declaredPackage.isEmpty)
        errMsg.append("from 'package %s' ".format(declaredPackage))

      errMsg.append("to 'package %s'.".format(expectedPackage))
    }
    reporter.report(errMsg.toString)
  }
}
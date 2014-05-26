package org.scalaide.ui.wizards

import org.eclipse.core.runtime.IPath
import org.eclipse.jdt.core.IJavaProject
import org.scalaide.util.internal.Commons

class ClassCreator extends ScalaFileCreator

class TraitCreator extends ScalaFileCreator

class ObjectCreator extends ScalaFileCreator

class PackageObjectCreator extends ScalaFileCreator {

  import ScalaFileCreator._

  /**
   * The initial path of a package object can be valid, therefore we want to
   * show users immediately when the initial path is not valid.
   */
  override def showErrorMessageAtStartup: Boolean =
    true

  private[wizards] override def generateInitialPath(path: Seq[String], srcDirs: Seq[String], isDirectory: Boolean): String = {
    val p = super.generateInitialPath(path, srcDirs, isDirectory)
    if (p.isEmpty()) "" else p.init
  }

  private[wizards] override def validateFullyQualifiedType(fullyQualifiedType: String, name: String): Either[Invalid, FileExistenceCheck] = {
    val parts = Commons.split(fullyQualifiedType, '.')

    def packageIdentCheck =
      parts.find(!isValidScalaPackageIdent(_)) map (e => s"'$e' is not a valid package name")

    packageIdentCheck match {
      case Some(e) => Left(Invalid(e))
      case _       => Right(checkFileExists(_, name.replace('.', '/') + "/package.scala"))
    }
  }

  private[wizards] override def createCompilationUnit(project: IJavaProject, path: String): IPath = {
    val Seq(srcFolder, packagePath) = Commons.split(path, '/')
    val folder = project.getProject().getFolder(s"/$srcFolder")
    val root = project.getPackageFragmentRoot(folder)
    val pkg = root.createPackageFragment(packagePath, false, null)
    val cu = pkg.createCompilationUnit(s"package.scala", "", false, null)

    cu.getPath()
  }
}

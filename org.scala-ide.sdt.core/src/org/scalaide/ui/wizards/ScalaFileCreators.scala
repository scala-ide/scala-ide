package org.scalaide.ui.wizards

import org.eclipse.core.resources.IContainer
import org.eclipse.core.resources.IFile
import org.eclipse.core.runtime.IPath
import org.eclipse.core.runtime.Path
import org.scalaide.util.internal.Commons

class ClassCreator extends ScalaFileCreator

class TraitCreator extends ScalaFileCreator

class ObjectCreator extends ScalaFileCreator

class PackageObjectCreator extends ScalaFileCreator {

  /**
   * The initial path of a package object can be valid, therefore we want to
   * show users immediately when the initial path is not valid.
   */
  override def showErrorMessageAtStartup: Boolean =
    true

  private[wizards] override def generateInitialPath(path: IPath, srcDirs: Seq[IPath], isDirectory: Boolean): String = {
    val p = super.generateInitialPath(path, srcDirs, isDirectory)
    if (p.isEmpty()) "" else p.init
  }

  private[wizards] override def validateFullyQualifiedType(fullyQualifiedType: String): Either[Invalid, FileExistenceCheck] = {
    val parts = Commons.split(fullyQualifiedType, '.')

    def packageIdentCheck =
      parts.find(!isValidScalaPackageIdent(_)) map (e => s"'$e' is not a valid package name")

    packageIdentCheck match {
      case Some(e) => Left(Invalid(e))
      case _       => Right { f =>
        val fileName = fullyQualifiedType.replace('.', '/') + "/package.scala"
        if (f.getFile(new Path(fileName)).exists())
          Invalid("File already exists")
        else
          Valid
      }
    }
  }

  override def create(folder: IContainer, name: String): IFile = {
    val filePath = name.replace('.', '/')
    folder.getFile(new Path(s"$filePath/package.scala"))
  }
}

class AppCreator extends ScalaFileCreator

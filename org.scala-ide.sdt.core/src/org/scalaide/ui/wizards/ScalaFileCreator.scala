package org.scalaide.ui.wizards

import org.eclipse.core.resources.IProject
import org.eclipse.core.resources.IResource
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.runtime.IPath
import org.scalaide.core.ScalaPlugin
import org.scalaide.util.internal.Commons
import org.scalaide.util.internal.eclipse.ProjectUtils

import scalariform.lexer._

object ScalaFileCreator {
  val VariableTypeName = "type_name"
  val VariablePackageName = "package_name"
}

trait ScalaFileCreator extends FileCreator {
  import ScalaFileCreator._
  import ProjectUtils._

  private[wizards] type FileExistenceCheck = IProject => Validation

  override def templateVariables(project: IProject, name: String): Map[String, String] = {
    val srcDirs = sourceDirs(project.getProject()).map(_.lastSegment())
    generateTemplateVariables(srcDirs, name)
  }

  override def initialPath(project: IResource): String = {
    val srcDirs = sourceDirs(project.getProject()).map(_.lastSegment())
    generateInitialPath(
        path = project.getFullPath().segments(),
        srcDirs = srcDirs,
        isDirectory = project.getType() == IResource.FOLDER)
  }

  override def validateName(project: IProject, name: String): Validation = {
    if (!ScalaPlugin.plugin.isScalaProject(project))
      Invalid("Not a Scala project")
    else {
      val srcDirs = sourceDirs(project)
      doValidation(srcDirs.map(_.lastSegment()), name) match {
        case Left(v) => v
        case Right(f) => f(project)
      }
    }
  }

  override def createFileFromName(project: IProject, name: String): IPath = {
    ???
  }

  /**
   * `path` contains the path starting from the project to a given element.
   * `srcFolders` contains the names of all source folders of a given project.
   * `isDirectory` describes if the last element of `fullPath` references a
   * directory.
   */
  private[wizards] def generateInitialPath(path: Seq[String], srcDirs: Seq[String], isDirectory: Boolean): String = {
    if (path.size < 3)
      ""
    else {
      val Seq(project, topFolder, rawSubPath @ _*) = path
      val subPath = if (isDirectory) rawSubPath else rawSubPath.init

      def generatePath(delimiter: String) = {
        val p = subPath.mkString(delimiter)

        if (p.isEmpty())
          s"$topFolder/"
        else
          s"$topFolder/$p$delimiter"
      }

      generatePath(if (srcDirs contains topFolder) "." else "/")
    }
  }


  private[wizards] def doValidation(srcDirs: Seq[String], name: String): Either[Invalid, FileExistenceCheck] = {
    def isValidScalaIdent(str: String) = {
      val validIdent =
        str.nonEmpty &&
        Character.isJavaIdentifierStart(str.head) &&
        str.tail.forall(Character.isJavaIdentifierPart)

      def noKeyword =
        !Tokens.KEYWORDS(ScalaLexer.tokenise(str, forgiveErrors = true).head.tokenType)

      validIdent && noKeyword
    }

    val rawPath = Commons.split(name, '/')
    val noFolder = rawPath.size == 1
    val noPackageNotation = rawPath.size > 2

    if (name.isEmpty())
      Left(Invalid("No file path specified"))
    else if (noFolder)
      Right(checkFileExists(_, name))
    else if (!srcDirs.contains(rawPath.head))
      if (name.endsWith("/"))
        Left(Invalid("No file name specified"))
      else
        Right(checkFileExists(_, name))
    else if (noPackageNotation)
      Left(Invalid(s"Incorrect syntax for file path. Has to be <folder>/<package>.<filename>"))
    else {
      val fullyQualifiedType = rawPath(1)
      val packages = Commons.split(fullyQualifiedType, '.')

      if (packages.last.isEmpty)
        Left(Invalid("No type name specified"))
      else
        packages.find(!isValidScalaIdent(_)) match {
          case Some(e) => Left(Invalid(s"'$e' is not a valid package name"))
          case _       => Right(checkTypeExists(_, fullyQualifiedType, name.replace('.', '/')))
        }
    }
  }

  private[wizards] def checkFileExists(project: IProject, path: String): Validation = {
    val root = ResourcesPlugin.getWorkspace().getRoot()
    val fullPath = root.getRawLocation().append(project.getFullPath()).append(path)
    val fileExists = fullPath.toFile().exists()
    if (fileExists)
      Invalid("File already exists")
    else
      Valid
  }

  private[wizards] def checkTypeExists(project: IProject, fullyQualifiedType: String, path: String): Validation = {
    val v = checkFileExists(project, path + ".scala")
    if (v.isInvalid)
      v
    else {
      val scalaProject = ScalaPlugin.plugin.asScalaProject(project)
      val typeExists = scalaProject flatMap { scalaProject =>
        scalaProject.presentationCompiler { compiler =>
          compiler.askOption { () =>
            compiler.rootMirror.getClassIfDefined(fullyQualifiedType) != compiler.NoSymbol
          }
        }.flatten
      } getOrElse false

      if (typeExists)
        Invalid("Type already exists")
      else
        Valid
    }
  }

  private[wizards] def generateTemplateVariables(srcDirs: Seq[String], name: String): Map[String, String] = {
    val isFolderNotation = name.count(_ == '/') != 1

    if (isFolderNotation)
      Map()
    else {
      val Seq(folder, pkg) = Commons.split(name, '/')

      if (!srcDirs.contains(folder))
        Map()
      else {
        val splitPos = pkg.lastIndexOf('.')
        if (splitPos < 0)
          Map(VariableTypeName -> pkg)
        else
          Map(
            VariablePackageName -> pkg.substring(0, splitPos),
            VariableTypeName -> pkg.substring(splitPos+1))
      }
    }
  }
}

package org.scalaide.ui.wizards

import org.eclipse.core.resources.IFolder
import org.eclipse.core.resources.IResource
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.runtime.IPath
import org.scalaide.core.ScalaPlugin
import org.scalaide.util.internal.Commons
import org.scalaide.util.internal.eclipse.FileUtils
import org.scalaide.util.internal.eclipse.ProjectUtils

import scalariform.lexer._

object ScalaFileCreator {
  val VariableTypeName = "type_name"
  val VariablePackageName = "package_name"

  import scala.reflect.runtime._
  private[this] val st = universe.asInstanceOf[JavaUniverse]

  val ScalaKeywords = st.nme.keywords map (_.toString())
  val JavaKeywords = st.javanme.keywords map (_.toString())
}

trait ScalaFileCreator extends FileCreator {
  import ScalaFileCreator._
  import ProjectUtils._

  private[wizards] type FileExistenceCheck = IFolder => Validation

  override def templateVariables(folder: IFolder, name: String): Map[String, String] =
    generateTemplateVariables(name)

  override def initialPath(res: IResource): String = {
    val srcDirs = sourceDirs(res.getProject())
    generateInitialPath(
        path = res.getFullPath(),
        srcDirs = srcDirs,
        isDirectory = res.getType() == IResource.FOLDER)
  }

  override def validateName(folder: IFolder, name: String): Validation = {
    if (!ScalaPlugin.plugin.isScalaProject(folder.getProject()))
      Invalid("Not a Scala project")
    else
      doValidation(name) match {
        case Left(v) => v
        case Right(f) => f(folder)
      }
  }

  override def createFilePath(folder: IFolder, name: String): IPath = {
    val filePath = name.replace('.', '/')+".scala"
    val root = ResourcesPlugin.getWorkspace().getRoot()
    root.getRawLocation().append(folder.getFullPath()).append(filePath)
  }

  override def completionEntries(folder: IFolder, name: String): Seq[String] = {
    val ret = projectAsJavaProject(folder.getProject()) map { jp =>
      val root = jp.findPackageFragmentRoot(folder.getFullPath())
      val pkgs = root.getChildren().map(_.getElementName())
      val ignoreCaseMatcher = s"(?i)\\Q$name\\E.*"

      pkgs.filter(_.matches(ignoreCaseMatcher))
    }

    ret.fold(Seq[String]())(identity)
  }

  /**
   * `path` is the path of the element which is selected when the wizard is
   * created. `srcDirs` contains all source folders of the project where `path`
   * is part of. `isDirectory` describes if the last element of `path` references
   * a directory.
   */
  private[wizards] def generateInitialPath(path: IPath, srcDirs: Seq[IPath], isDirectory: Boolean): String = {
    srcDirs.find(_.isPrefixOf(path))
      .map(srcDir => path.removeFirstSegments(srcDir.segmentCount()))
      .map(pkgOrFilePath => if (isDirectory) pkgOrFilePath else pkgOrFilePath.removeLastSegments(1))
      .map(_.segments().mkString("."))
      .map(pkg => if (pkg.isEmpty()) "" else s"$pkg.")
      .getOrElse("")
  }

  private[wizards] def doValidation(name: String): Either[Invalid, FileExistenceCheck] = {
    if (name.isEmpty())
      Left(Invalid("No file path specified"))
    else
      validateFullyQualifiedType(name)
  }

  private[wizards] def validateFullyQualifiedType(fullyQualifiedType: String): Either[Invalid, FileExistenceCheck] = {
    def isValidScalaTypeIdent(str: String) = {
      val conformsToIdentToken = ScalaLexer.tokenise(str, forgiveErrors = true).size == 2

      conformsToIdentToken && !ScalaKeywords.contains(str)
    }

    val parts = Commons.split(fullyQualifiedType, '.')

    if (parts.last.isEmpty)
      Left(Invalid("No type name specified"))
    else {
      def packageIdentCheck =
        parts.init.find(!isValidScalaPackageIdent(_)) map (e => s"'$e' is not a valid package name")

      def typeIdentCheck =
        Seq(parts.last).find(!isValidScalaTypeIdent(_)) map (e => s"'$e' is not a valid type name")

      packageIdentCheck orElse typeIdentCheck match {
        case Some(e) => Left(Invalid(e))
        case _       => Right(checkTypeExists(_, fullyQualifiedType))
      }
    }
  }

  private[wizards] def isValidScalaPackageIdent(str: String): Boolean = {
    val validIdent =
      str.nonEmpty &&
      Character.isJavaIdentifierStart(str.head) &&
      str.tail.forall(Character.isJavaIdentifierPart)

    validIdent && !ScalaKeywords.contains(str) && !JavaKeywords.contains(str)
  }

  private[wizards] def checkTypeExists(folder: IFolder, fullyQualifiedType: String): Validation = {
    val path = fullyQualifiedType.replace('.', '/')
    if (FileUtils.existsWorkspaceFile(folder.getFullPath().append(path + ".scala")))
      Invalid("File already exists")
    else {
      val scalaProject = ScalaPlugin.plugin.asScalaProject(folder.getProject())
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

  private[wizards] def generateTemplateVariables(pkg: String): Map[String, String] = {
    val splitPos = pkg.lastIndexOf('.')
    if (splitPos < 0)
      Map(VariableTypeName -> pkg)
    else
      Map(
        VariablePackageName -> pkg.substring(0, splitPos),
        VariableTypeName -> pkg.substring(splitPos+1))
  }
}

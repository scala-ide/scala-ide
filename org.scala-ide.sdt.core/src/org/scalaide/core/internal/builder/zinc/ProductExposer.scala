package org.scalaide.core.internal.builder.zinc

import java.io.File
import java.io.FilenameFilter
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption

import org.eclipse.core.resources.IProject
import org.eclipse.core.resources.IResource
import org.eclipse.core.runtime.IPath
import org.eclipse.jdt.core.IJavaModelMarker
import org.scalaide.core.IScalaPlugin
import org.scalaide.core.SdtConstants

private[zinc] object ProductExposer {
  private val ProductExtension = ".failed"
  private val RootProject = 1

  private def packageAndClassPrefix(project: IProject, file: IPath) = {
    val ClassFile = 1
    val scalaProject = IScalaPlugin().getScalaProject(project)
    scalaProject.sourceFolders.map { srcFolder =>
      val relativeSrcPath = project.getFullPath.append(srcFolder.makeRelativeTo(project.getLocation))
      if (file.matchingFirstSegments(relativeSrcPath) == relativeSrcPath.segmentCount()) {
        val packageClassFile = file.removeFirstSegments(relativeSrcPath.segmentCount())
        Option((packageClassFile.removeLastSegments(ClassFile), packageClassFile.segments.last.takeWhile { _ != '.' }))
      } else None
    }.collectFirst {
      case Some(src) => src
    }
  }

  def hideJavaCompilationProductsIfCompilationFailed(project: IProject) = {
    val Dot = 1
    val shouldHide = project.findMarkers(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER, false, IResource.DEPTH_INFINITE).nonEmpty
    if (shouldHide) {
      val scalaProject = IScalaPlugin().getScalaProject(project)
      scalaProject.allSourceFiles.filter {
        _.getFileExtension == SdtConstants.JavaFileExtn.drop(Dot)
      }.map { f =>
        packageAndClassPrefix(project, f.getFullPath)
      }.collect {
        case Some(p) => p
      }.foreach {
        case (packagePath, productPrefix) =>
          scalaProject.outputFolders.map { output =>
            val absOutput = project.getLocation.append(output.segments.drop(RootProject).mkString(File.separator)).makeAbsolute
            absOutput.append(packagePath).makeAbsolute.toFile
          }.collect {
            case packagePath if packagePath.exists =>
              packagePath.listFiles(product(productPrefix))
          }.flatten.foreach { p =>
            val path = p.toPath
            Files.move(path, path.resolveSibling(p.getName + ProductExtension), StandardCopyOption.REPLACE_EXISTING)
          }
      }
    }
  }

  private def product(mainTypeName: String) = new FilenameFilter {
    def accept(file: File, name: String): Boolean =
      name.startsWith(mainTypeName + ".") || name.startsWith(mainTypeName + "$")
  }

  def showJavaCompilationProducts(project: IProject) = {
    val scalaProject = IScalaPlugin().getScalaProject(project)
    scalaProject.outputFolderLocations.flatMap { output =>
      val finder = new Finder
      Files.walkFileTree(output.makeAbsolute.toFile.toPath, finder)
      finder.collected
    }.foreach { path =>
      val fname = path.toFile.getName
      Files.move(path, path.resolveSibling(fname.substring(0, fname.lastIndexOf(ProductExtension))), StandardCopyOption.REPLACE_EXISTING)
    }
  }

  class Finder extends SimpleFileVisitor[Path] {
    import java.nio.file._
    import java.nio.file.FileVisitResult._
    import java.nio.file.attribute._
    import scala.collection.mutable.ListBuffer
    def collected = coll.toSeq
    private val coll: scala.collection.mutable.ListBuffer[Path] = ListBuffer.empty
    private val matcher = FileSystems.getDefault().getPathMatcher("glob:" + s"*$ProductExtension")

    private def find(file: Path) = {
      val name = file.getFileName()
      if (name != null && matcher.matches(name))
        coll += file
    }

    override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
      find(file)
      CONTINUE
    }

    override def visitFileFailed(file: Path, exc: IOException) = CONTINUE
  }
}

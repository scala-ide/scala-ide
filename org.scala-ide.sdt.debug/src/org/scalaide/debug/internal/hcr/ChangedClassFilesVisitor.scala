/*
 * Copyright (c) 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.hcr

import org.eclipse.core.resources.IFile
import org.eclipse.core.resources.IMarker
import org.eclipse.core.resources.IResource
import org.eclipse.core.resources.IResourceDelta
import org.eclipse.core.resources.IResourceDeltaVisitor
import org.eclipse.core.runtime.IPath
import org.eclipse.core.runtime.Path
import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.jdt.core.IJavaModelMarker
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.JavaCore
import org.eclipse.jdt.core.ToolFactory
import org.eclipse.jdt.core.util.IClassFileReader
import org.eclipse.jdt.internal.debug.core.JavaDebugUtils
import org.scalaide.logging.HasLogger
import org.scalaide.debug.internal.preferences.HotCodeReplacePreferences

private[internal] case class ClassFileResource(fullyQualifiedName: String, classFile: IFile)

/**
 * Collects classes affected by changes in code and optionally skips these ones which contain
 * error markers in associated source files.
 * The implementation of this class is inspired by
 * org.eclipse.jdt.internal.debug.core.hcr.JavaHotCodeReplaceManager.ChangedClassFilesVisitor
 * used in Java HCR implementation.
 */
private[internal] class ChangedClassFilesVisitor extends IResourceDeltaVisitor with HasLogger {
  import ChangedClassFilesVisitor._

  // the value stored as a field to finish applying the same strategy to all classes even when someone changed
  // the configuration in the meantime
  private var replaceDespiteCompilationErrors = HotCodeReplacePreferences.performHcrForFilesContainingErrors

  /**
   * Found classes containing changes.
   */
  private val changedClasses = scala.collection.mutable.Set[ClassFileResource]()

  def getChangedClasses: List[ClassFileResource] = changedClasses.toList

  /**
   * It should be called always before the visitor is reused.
   */
  def reset(): Unit = {
    replaceDespiteCompilationErrors = HotCodeReplacePreferences.performHcrForFilesContainingErrors
    changedClasses.clear()
  }

  /**
   * Looks for modified classes, adds them to changedClasses and decides whether children should be visited.
   * @return whether children should be visited or not
   */
  override def visit(delta: IResourceDelta): Boolean = delta match {
    case MayContainChangedClassesIn(resource) => resource.getType match {
      case IResource.FILE =>
        resource match {
          case file: IFile if isContentChanged(delta) && isClassFile(file) =>
            visitClassFile(file)
          case _ => // no changes or not a class file - nothing to do
        }
        false
      case _ => true
    }
    case _ => false
  }

  private def visitClassFile(classFile: IFile): Unit =
    for {
      localPath <- Option(classFile.getLocation())
      reader <- classFileReader(localPath)
    } {
      val slashDelimitedQualifiedName = new String(reader.getClassName())

      if (replaceDespiteCompilationErrors || !hasCompilationErrors(classFile, reader, slashDelimitedQualifiedName)) {
        val className = slashDelimitedQualifiedName.replace('/', '.')
        changedClasses.add(ClassFileResource(className, classFile))
      }
    }

  private def classFileReader(path: IPath) = {
    val osSpecificPath = path.toOSString()
    Option(ToolFactory.createDefaultClassFileReader(osSpecificPath, IClassFileReader.CLASSFILE_ATTRIBUTES))
  }

  /**
   * Checks whether there are errors markers located in src file associated with given class file.
   *
   * @param classFile class file for given type
   * @param reader class file reader for given type
   * @param fullyQualifiedName slash delimited name of type
   */
  private def hasCompilationErrors(classFile: IFile, reader: IClassFileReader, fullyQualifiedName: String): Boolean =
    try {
      def isErrorMarker(marker: IMarker): Boolean =
        marker.getAttribute(IMarker.SEVERITY, /* defaultValue = */ IMarker.SEVERITY_INFO) == IMarker.SEVERITY_ERROR

      def hasErrorMarkers(sourceFile: IResource): Boolean = {
        val problemMarkers = sourceFile.findMarkers(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER, true, IResource.DEPTH_INFINITE)
        problemMarkers exists isErrorMarker
      }

      val srcFile = getSourceFile(classFile, reader, fullyQualifiedName)
      srcFile exists hasErrorMarkers
    } catch {
      case e: Exception =>
        logger.error(s"Something went wrong when looking for compilation error markers for type $fullyQualifiedName", e)
        true
    }

  /**
   * Tries to find the source file for given type in the associated project.
   */
  private def getSourceFile(classFile: IFile, reader: IClassFileReader, fullyQualifiedName: String): Option[IResource] = {
    try {
      val project: IJavaProject = JavaCore.create(classFile.getProject)

      val sourceAttribute = Option(reader.getSourceFileAttribute)
      val sourceFileName = sourceAttribute map { srcAttr =>
        new String(srcAttr.getSourceFileName())
      }

      sourceFileName.flatMap { srcName =>
        val i = fullyQualifiedName.lastIndexOf('/')
        val sourceFilePath = if (i > 0) fullyQualifiedName.substring(0, i + 1) + srcName else srcName
        Option(project.findElement(new Path(sourceFilePath)))
      }.getOrElse {
        JavaDebugUtils.findElement(fullyQualifiedName, project)
      } match {
        case cu: ICompilationUnit => Some(cu.getCorrespondingResource())
        case _ => None
      }
    } catch {
      case e: Exception =>
        logger.error(s"Something went wrong when looking for src file for type $fullyQualifiedName", e)
        None
    }
  }

  private def isClassFile(file: IFile) = "class" == file.getFullPath().getFileExtension()

  private def isContentChanged(delta: IResourceDelta) = 0 != (delta.getFlags() & IResourceDelta.CONTENT)
}

private[hcr] object ChangedClassFilesVisitor {

  private object MayContainChangedClassesIn {
    def unapply(delta: IResourceDelta): Option[IResource] =
      if (delta == null || 0 == (delta.getKind() & IResourceDelta.CHANGED)) None
      else Option(delta.getResource())
  }
}

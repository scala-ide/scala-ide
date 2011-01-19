/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import scala.collection.immutable.Set
import scala.collection.mutable.{ LinkedHashSet, HashMap, HashSet }

import java.io.File.pathSeparator

import org.eclipse.core.resources.{ IContainer, IFile, IFolder, IMarker, IProject, IResource, IResourceProxy, IResourceProxyVisitor, IWorkspaceRunnable }
import org.eclipse.core.runtime.{ FileLocator, IPath, IProgressMonitor, Path }
import org.eclipse.jdt.core.{ IClasspathEntry, IJavaProject, JavaCore }
import org.eclipse.jdt.core.compiler.IProblem
import org.eclipse.jdt.internal.core.JavaProject
import org.eclipse.jdt.internal.core.builder.{ ClasspathDirectory, ClasspathLocation, NameEnvironment }
import org.eclipse.jdt.internal.core.util.Util
import org.eclipse.swt.widgets.{ Display, Shell }

import scala.tools.nsc.{ Settings, MissingRequirementError }
import scala.tools.nsc.util.SourceFile

import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.tools.eclipse.properties.PropertyStore
import scala.tools.eclipse.util.{ Cached, EclipseResource, IDESettings, OSGiUtils, ReflectionUtils }

class ScalaProject(val underlying: IProject) {
  import ScalaPlugin.plugin

  private var classpathUpdate: Long = IResource.NULL_STAMP
  private var buildManager0: EclipseBuildManager = null
  private var hasBeenBuilt = false
  private val depFile = underlying.getFile(underlying.getName() + ".scala_dependencies")
  private val resetPendingLock = new Object
  private var resetPending = false

  case class InvalidCompilerSettings() extends RuntimeException("Scala compiler cannot initialize. Please check that your classpath contains the standard Scala library")

  private val presentationCompiler = new Cached[Option[ScalaPresentationCompiler]] {
    override def create() = {
      checkClasspathTimeStamp()
      println("trying to instantiate compiler for " + underlying.getName)
      try {
        val settings = new Settings
        settings.printtypes.tryToSet(Nil)
        settings.verbose.tryToSetFromPropertyValue("true")
        settings.XlogImplicits.tryToSetFromPropertyValue("true")
        initialize(settings, _.name.startsWith("-Ypresentation"))
        Some(new ScalaPresentationCompiler(ScalaProject.this, settings))
      } catch {
        case ex@MissingRequirementError(required) =>
          failedCompilerInitialization("Could not initialize Scala compiler because it could not find a required class: " + required)
          plugin.logError(ex)
          None
        case ex =>
          plugin.logError(ex)
          None
      }
    }

    override def destroy(compiler: Option[ScalaPresentationCompiler]) {
      compiler.map(_.destroy())
    }
  }

  private var messageShowed = false
  
  private def failedCompilerInitialization(msg: String) {
    import org.eclipse.jface.dialogs.MessageDialog
    synchronized {
      if (!messageShowed) {
        messageShowed = true
        Display.getDefault asyncExec new Runnable { 
          def run() {
//            ToggleScalaNatureAction.toggleScalaNature(underlying)
            MessageDialog.openWarning(null, "Error initializing the Scala compiler in project %s".format(underlying.getName),
            msg +
            ". The editor will not try to re-initialize the compiler until you change the classpath and " +
            " reopen project %s .".format(underlying.getName))
          }
        }
      }
    }
  }

  override def toString = underlying.getName

  def buildError(severity: Int, msg: String, monitor: IProgressMonitor) =
    underlying.getWorkspace.run(new IWorkspaceRunnable {
      def run(monitor: IProgressMonitor) = {
        val mrk = underlying.createMarker(plugin.problemMarkerId)
        mrk.setAttribute(IMarker.SEVERITY, severity)
        val string = msg.map {
          case '\n' => ' '
          case '\r' => ' '
          case c => c
        }.mkString("", "", "")
        mrk.setAttribute(IMarker.MESSAGE, msg)
      }
    }, monitor)

  def clearBuildErrors(monitor: IProgressMonitor) =
    underlying.getWorkspace.run(new IWorkspaceRunnable {
      def run(monitor: IProgressMonitor) = {
        underlying.deleteMarkers(plugin.problemMarkerId, true, IResource.DEPTH_ZERO)
      }
    }, monitor)

  def externalDepends = underlying.getReferencedProjects

  lazy val javaProject = {
    if (!underlying.exists())
      underlying.create(null)
    JavaCore.create(underlying)
  }

  def sourceFolders: Seq[IPath] = {
    val all = for (cpe <- javaProject.getResolvedClasspath(true) if cpe.getEntryKind == IClasspathEntry.CPE_SOURCE) yield {
      val resource = plugin.workspaceRoot.findMember(cpe.getPath)
      if (resource == null) null else resource.getLocation
    }
    all.filter { _ ne null }
  }

  def outputFolders: Seq[IPath] = {
    val outputs = new LinkedHashSet[IPath]
    for (cpe <- javaProject.getResolvedClasspath(true) if cpe.getEntryKind == IClasspathEntry.CPE_SOURCE) {
      val cpeOutput = cpe.getOutputLocation
      val output = if (cpeOutput == null) javaProject.getOutputLocation else cpeOutput
      outputs += output
    }
    outputs.toSeq
  }

  def classpath: Seq[IPath] = {
    val path = new LinkedHashSet[IPath]
    def classpath(javaProject: IJavaProject, exportedOnly: Boolean): Unit = {
      val cpes = javaProject.getResolvedClasspath(true)

      for (cpe <- cpes if (!exportedOnly || cpe.isExported)) cpe.getEntryKind match {
        case IClasspathEntry.CPE_PROJECT =>
          val depProject = plugin.workspaceRoot.getProject(cpe.getPath.lastSegment)
          if (JavaProject.hasJavaNature(depProject)) {
            val depJava = JavaCore.create(depProject)
            for (cpe <- depJava.getResolvedClasspath(true) if cpe.getEntryKind == IClasspathEntry.CPE_SOURCE) {
              val specificOutputLocation = cpe.getOutputLocation
              val outputLocation = if (specificOutputLocation != null) specificOutputLocation else depJava.getOutputLocation
              if (outputLocation != null) {
                val absPath = plugin.workspaceRoot.findMember(outputLocation)
                if (absPath != null) path += absPath.getLocation
              }
            }
            classpath(depJava, true)
          }
        case IClasspathEntry.CPE_LIBRARY =>
          if (cpe.getPath != null) {
            val absPath = plugin.workspaceRoot.findMember(cpe.getPath)
            if (absPath != null)
              path += absPath.getLocation
            else
              path += cpe.getPath
          }
        case _ =>
      }
    }
    classpath(javaProject, false)
    path.toList
  }

  def sourceOutputFolders(env: NameEnvironment): Seq[(IContainer, IContainer)] = {
    val sourceLocations = NameEnvironmentUtils.sourceLocations(env)
    sourceLocations.map(cl => (ClasspathLocationUtils.sourceFolder(cl), ClasspathLocationUtils.binaryFolder(cl)))
  }

  def isExcludedFromProject(env: NameEnvironment, childPath: IPath): Boolean = {
    // answer whether the folder should be ignored when walking the project as a source folder
    if (childPath.segmentCount() > 2) return false // is a subfolder of a package

    val sourceLocations = NameEnvironmentUtils.sourceLocations(env)
    for (sl <- sourceLocations) {
      val binaryFolder = ClasspathLocationUtils.binaryFolder(sl)
      if (childPath == binaryFolder.getFullPath) return true
      val sourceFolder = ClasspathLocationUtils.sourceFolder(sl)
      if (childPath == sourceFolder.getFullPath) return true
    }

    // skip default output folder which may not be used by any source folder
    return childPath == javaProject.getOutputLocation
  }

  def allSourceFiles(): Set[IFile] = allSourceFiles(new NameEnvironment(javaProject))

  def allSourceFiles(env: NameEnvironment): Set[IFile] = {
    val sourceFiles = new HashSet[IFile]
    val sourceLocations = NameEnvironmentUtils.sourceLocations(env)

    for (sourceLocation <- sourceLocations) {
      val sourceFolder = ClasspathLocationUtils.sourceFolder(sourceLocation)
      val exclusionPatterns = ClasspathLocationUtils.exclusionPatterns(sourceLocation)
      val inclusionPatterns = ClasspathLocationUtils.inclusionPatterns(sourceLocation)
      val isAlsoProject = sourceFolder == javaProject
      val segmentCount = sourceFolder.getFullPath.segmentCount
      val outputFolder = ClasspathLocationUtils.binaryFolder(sourceLocation)
      val isOutputFolder = sourceFolder == outputFolder
      sourceFolder.accept(
        new IResourceProxyVisitor {
          def visit(proxy: IResourceProxy): Boolean = {
            proxy.getType match {
              case IResource.FILE =>
                val resource = proxy.requestResource
                if (plugin.isBuildable(resource.asInstanceOf[IFile])) {
                  if (exclusionPatterns != null || inclusionPatterns != null)
                    if (Util.isExcluded(resource.getFullPath, inclusionPatterns, exclusionPatterns, false))
                      return false
                  sourceFiles += resource.asInstanceOf[IFile]
                }
                return false

              case IResource.FOLDER =>
                var folderPath: IPath = null
                if (isAlsoProject) {
                  folderPath = proxy.requestFullPath
                  if (isExcludedFromProject(env, folderPath))
                    return false
                }
                if (exclusionPatterns != null) {
                  if (folderPath == null)
                    folderPath = proxy.requestFullPath
                  if (Util.isExcluded(folderPath, inclusionPatterns, exclusionPatterns, true)) {
                    // must walk children if inclusionPatterns != null, can skip them if == null
                    // but folder is excluded so do not create it in the output folder
                    return inclusionPatterns != null
                  }
                }

              case _ =>
            }
            return true
          }
        },
        IResource.NONE)
    }

    Set.empty ++ sourceFiles
  }

  def createOutputFolders = {
    for (outputPath <- outputFolders) plugin.workspaceRoot.findMember(outputPath) match {
      case fldr: IFolder =>
        def createParentFolder(parent: IContainer) {
          if (!parent.exists()) {
            createParentFolder(parent.getParent)
            parent.asInstanceOf[IFolder].create(true, true, null)
            parent.setDerived(true)
          }
        }

        fldr.refreshLocal(IResource.DEPTH_ZERO, null)
        if (!fldr.exists()) {
          createParentFolder(fldr.getParent)
          fldr.create(IResource.FORCE | IResource.DERIVED, true, null)
        }
      case _ =>
    }
  }

  def cleanOutputFolders(monitor: IProgressMonitor) = {
    def delete(container: IContainer, deleteDirs: Boolean)(f: String => Boolean): Unit =
      if (container.exists()) {
        container.members.foreach {
          case cntnr: IContainer =>
            if (deleteDirs) {
              try {
                cntnr.delete(true, monitor) // might not work.
              } catch {
                case _ =>
                  delete(cntnr, deleteDirs)(f)
                  if (deleteDirs)
                    try {
                      cntnr.delete(true, monitor) // try again
                    } catch {
                      case t => plugin.logError(t)
                    }
              }
            } else
              delete(cntnr, deleteDirs)(f)
          case file: IFile if f(file.getName) =>
            try {
              file.delete(true, monitor)
            } catch {
              case t => plugin.logError(t)
            }
          case _ =>
        }
      }

    val outputLocation = javaProject.getOutputLocation
    val resource = plugin.workspaceRoot.findMember(outputLocation)
    resource match {
      case container: IContainer => delete(container, container != javaProject.getProject)(_.endsWith(".class"))
      case _ =>
    }
  }

  /** Check if the .classpath file has been changed since the last check.
   *  If the saved timestamp does not match the file timestamp, reset the
   *  two compilers.
   */
  def checkClasspathTimeStamp(): Unit = plugin.check {
    val cp = underlying.getFile(".classpath")
    if (cp.exists)
      classpathUpdate match {
        case IResource.NULL_STAMP => classpathUpdate = cp.getModificationStamp()
        case stamp if stamp == cp.getModificationStamp() =>
        case _ =>
          classpathUpdate = cp.getModificationStamp()
          resetCompilers
      }
  }

  def refreshOutput: Unit = {
    val res = plugin.workspaceRoot.findMember(javaProject.getOutputLocation)
    if (res ne null)
      res.refreshLocal(IResource.DEPTH_INFINITE, null)
  }

  def initialize(settings: Settings, filter: Settings#Setting => Boolean) = {
    val env = new NameEnvironment(javaProject)

    for ((src, dst) <- sourceOutputFolders(env))
      settings.outputDirs.add(EclipseResource(src), EclipseResource(dst))

    // TODO Per-file encodings
    val sfs = sourceFolders
    if (!sfs.isEmpty) {
      val path = sfs.iterator.next
      plugin.workspaceRoot.findContainersForLocation(path) match {
        case Array(container) => settings.encoding.value = container.getDefaultCharset
        case _ =>
      }
    }

    settings.classpath.value = classpath.map(_.toOSString).mkString(pathSeparator)
    settings.sourcepath.value = sfs.map(_.toOSString).mkString(pathSeparator)

    val workspaceStore = ScalaPlugin.plugin.getPreferenceStore
    val projectStore = new PropertyStore(underlying, workspaceStore, plugin.pluginId)
    val useProjectSettings = projectStore.getBoolean(SettingConverterUtil.USE_PROJECT_SETTINGS_PREFERENCE)

    val store = if (useProjectSettings) projectStore else workspaceStore
    for (
      box <- IDESettings.shownSettings(settings);
      setting <- box.userSettings; if filter(setting)
    ) {
      val value0 = store.getString(SettingConverterUtil.convertNameToProperty(setting.name))
      println("initializing %s to %s".format(setting.name, value0.toString))
      try {
        val value = if (setting ne settings.pluginsDir) value0 else {
          ScalaPlugin.plugin.continuationsClasses map {
            _.removeLastSegments(1).toOSString + (if (value0 == null || value0.length == 0) "" else ":" + value0)
          } getOrElse value0
        }
        if (value != null && value.length != 0) {
          setting.tryToSetFromPropertyValue(value)
        }
      } catch {
        case t: Throwable => plugin.logError("Unable to set setting '" + setting.name + "' to '" + value0 + "'", t)
      }
    }
  }

  def isStandardSource(file: IFile, qualifiedName: String): Boolean = {
    val pathString = file.getLocation.toString
    val suffix = qualifiedName.replace(".", "/") + ".scala"
    pathString.endsWith(suffix) && {
      val suffixPath = new Path(suffix)
      val sourceFolderPath = file.getLocation.removeLastSegments(suffixPath.segmentCount)
      sourceFolders.exists(_ == sourceFolderPath)
    }
  }

  def withPresentationCompiler[T](op: ScalaPresentationCompiler => T): T = {
    presentationCompiler {
      case Some(c) => op(c)
      case None => 
        if (underlying.isOpen)
          failedCompilerInitialization("Compiler failed to initialize properly.");
        //throw InvalidCompilerSettings()
        null.asInstanceOf[T] // we're already in deep trouble here, so one more NPE won't kill us
    }
  }

  def withSourceFile[T](scu: ScalaCompilationUnit)(op: (SourceFile, ScalaPresentationCompiler) => T): T =
    withPresentationCompiler { compiler =>
      compiler.withSourceFile(scu)(op)
    }

  def resetPresentationCompiler {
    presentationCompiler.invalidate
  }

  def buildManager = {
    checkClasspathTimeStamp()
    if (buildManager0 == null) {
      val settings = new Settings
      initialize(settings, _ => true)
      buildManager0 = new EclipseBuildManager(this, settings)
    }
    buildManager0
  }

  def prepareBuild(): Boolean = {
    if (!hasBeenBuilt) {
      if (!depFile.exists())
        true
      else {
        try {
          !buildManager.loadFrom(EclipseResource(depFile), EclipseResource.fromString(_).getOrElse(null))
        } catch { case _ => true }
      }
    } else
      false
  }

  def build(addedOrUpdated: Set[IFile], removed: Set[IFile], monitor: IProgressMonitor) {
    if (addedOrUpdated.isEmpty && removed.isEmpty)
      return

    hasBeenBuilt = true

    clearBuildErrors(monitor)
    buildManager.build(addedOrUpdated, removed, monitor)
    refreshOutput

    buildManager.saveTo(EclipseResource(depFile), _.toString)
    depFile.setDerived(true)
    depFile.refreshLocal(IResource.DEPTH_INFINITE, null)
  }

  def clean(monitor: IProgressMonitor) = {
    underlying.deleteMarkers(plugin.problemMarkerId, true, IResource.DEPTH_INFINITE)
    resetCompilers
    depFile.delete(true, false, monitor)
    cleanOutputFolders(monitor)
  }

  def resetBuildCompiler {
    buildManager0 = null
    hasBeenBuilt = false
  }

  def resetCompilers = {
    resetBuildCompiler
    resetPresentationCompiler
  }
}

object NameEnvironmentUtils extends ReflectionUtils {
  val neClazz = classOf[NameEnvironment]
  val sourceLocationsField = getDeclaredField(neClazz, "sourceLocations")

  def sourceLocations(env: NameEnvironment) = sourceLocationsField.get(env).asInstanceOf[Array[ClasspathLocation]]
}

object ClasspathLocationUtils extends ReflectionUtils {
  val cdClazz = classOf[ClasspathDirectory]
  val binaryFolderField = getDeclaredField(cdClazz, "binaryFolder")

  val cpmlClazz = Class.forName("org.eclipse.jdt.internal.core.builder.ClasspathMultiDirectory")
  val sourceFolderField = getDeclaredField(cpmlClazz, "sourceFolder")
  val inclusionPatternsField = getDeclaredField(cpmlClazz, "inclusionPatterns")
  val exclusionPatternsField = getDeclaredField(cpmlClazz, "exclusionPatterns")

  def binaryFolder(cl: ClasspathLocation) = binaryFolderField.get(cl).asInstanceOf[IContainer]
  def sourceFolder(cl: ClasspathLocation) = sourceFolderField.get(cl).asInstanceOf[IContainer]
  def inclusionPatterns(cl: ClasspathLocation) = inclusionPatternsField.get(cl).asInstanceOf[Array[Array[Char]]]
  def exclusionPatterns(cl: ClasspathLocation) = exclusionPatternsField.get(cl).asInstanceOf[Array[Array[Char]]]
}

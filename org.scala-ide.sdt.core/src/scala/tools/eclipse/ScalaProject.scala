/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import scala.collection.immutable.Set
import scala.collection.mutable.{ LinkedHashSet, HashMap, HashSet }
import java.io.File.pathSeparator
import org.eclipse.core.resources.{ IContainer, IFile, IFolder, IMarker, IProject, IResource, IResourceProxy, IResourceProxyVisitor }
import org.eclipse.core.runtime.{ FileLocator, IPath, IProgressMonitor, Path, SubMonitor }
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
import scala.tools.eclipse.util.{ Cached, EclipseResource, OSGiUtils, ReflectionUtils, EclipseUtils }
import scala.tools.eclipse.properties.IDESettings
import util.SWTUtils.asyncExec
import EclipseUtils.workspaceRunnableIn
import scala.tools.eclipse.properties.CompilerSettings
import scala.tools.eclipse.util.HasLogger


trait BuildSuccessListener {   
  def buildSuccessful(): Unit
}

class ScalaProject(val underlying: IProject) extends HasLogger {
  import ScalaPlugin.plugin

  private var classpathUpdate: Long = IResource.NULL_STAMP
  private var buildManager0: EclipseBuildManager = null
  private var hasBeenBuilt = false
  private val resetPendingLock = new Object
  private var resetPending = false
  
  private val buildListeners = new HashSet[BuildSuccessListener]

  case class InvalidCompilerSettings() extends RuntimeException(
        "Scala compiler cannot initialize for project: " + underlying.getName +
    		". Please check that your classpath contains the standard Scala library.")

  private val presentationCompiler = new Cached[Option[ScalaPresentationCompiler]] {
    override def create() = {
      checkClasspathTimeStamp(shouldReset = false)
      try {
        val settings = new Settings
        settings.printtypes.tryToSet(Nil)
        initialize(settings, isPCSetting(settings))
        Some(new ScalaPresentationCompiler(ScalaProject.this, settings))
      } catch {
        case ex @ MissingRequirementError(required) =>
          failedCompilerInitialization("could not find a required class: " + required)
          logger.error(ex)
          None
        case ex =>
          logger.info("Throwable when intializing presentation compiler!!! " + ex.getMessage)
          ex.printStackTrace()
          if (underlying.isOpen)
            failedCompilerInitialization("error initializing Scala compiler")
          logger.error(ex)          
          None
      }
    }

    override def destroy(compiler: Option[ScalaPresentationCompiler]) {
      compiler.map(_.destroy())
    }
  }

  /** Compiler settings that are honored by the presentation compiler. */
  private def isPCSetting(settings: Settings): Set[Settings#Setting] = {
    import settings.{ plugin => pluginSetting, _ }
    Set(deprecation, 
        unchecked, 
        pluginOptions, 
        verbose,
        Xexperimental, 
        future, 
        Xmigration28, 
        pluginSetting,
        pluginsDir,
        YpresentationDebug, 
        YpresentationVerbose, 
        YpresentationLog, 
        YpresentationReplay, 
        YpresentationDelay)
  }
  
  private var messageShowed = false

  private def failedCompilerInitialization(msg: String) {
    import org.eclipse.jface.dialogs.MessageDialog
    synchronized {
      if (!messageShowed) {
        messageShowed = true
        asyncExec {
          val doAdd = MessageDialog.openQuestion(ScalaPlugin.getShell, "Add Scala library to project classpath?", 
              ("There was an error initializing the Scala compiler: %s. \n\n"+
               "The editor compiler will be restarted when the project is cleaned or the classpath is changed.\n\n" + 
               "Add the Scala library to the classpath of project %s?") 
              .format(msg, underlying.getName))
          if (doAdd) {
            plugin check {
              Nature.addScalaLibAndSave(underlying)
            }
          }
        }
      }
    }
  }

  override def toString = underlying.getName

  /** Generic build error, without a source position. It creates a marker in the
   *  Problem views.
   */
  def buildError(severity: Int, msg: String, monitor: IProgressMonitor) =
    workspaceRunnableIn(underlying.getWorkspace, monitor) { m =>
      val mrk = underlying.createMarker(plugin.problemMarkerId)
      mrk.setAttribute(IMarker.SEVERITY, severity)
      val string = msg.map {
        case '\n' => ' '
        case '\r' => ' '
        case c    => c
      }.mkString("", "", "")
      mrk.setAttribute(IMarker.MESSAGE, string)
    }

  def clearBuildErrors =
    workspaceRunnableIn(underlying.getWorkspace) { m =>
      underlying.deleteMarkers(plugin.problemMarkerId, true, IResource.DEPTH_ZERO)
    }

  def externalDepends = underlying.getReferencedProjects

  lazy val javaProject = {
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

    def computeClasspath(project: IJavaProject, exportedOnly: Boolean): Unit = {
      val cpes = project.getResolvedClasspath(true)

      for (cpe <- cpes if !exportedOnly || cpe.isExported ||
    		            cpe.getEntryKind == IClasspathEntry.CPE_SOURCE) cpe.getEntryKind match {
        case IClasspathEntry.CPE_PROJECT =>
          val depProject = plugin.workspaceRoot.getProject(cpe.getPath.lastSegment)
          if (JavaProject.hasJavaNature(depProject)) {
            computeClasspath(JavaCore.create(depProject), true)
          }
        case IClasspathEntry.CPE_LIBRARY =>
          if (cpe.getPath != null) {
            val absPath = plugin.workspaceRoot.findMember(cpe.getPath)
            if (absPath != null)
              path += absPath.getLocation
            else {
              path += cpe.getPath
            }
          } else
            logger.error("Classpath computation encountered a null path for " + cpe, null)
        case IClasspathEntry.CPE_SOURCE =>
          val cpeOutput = cpe.getOutputLocation
          val outputLocation = if (cpeOutput != null) cpeOutput else project.getOutputLocation
              
          if (outputLocation != null) {
            val absPath = plugin.workspaceRoot.findMember(outputLocation)
            if (absPath != null) 
              path += absPath.getLocation
          }  

        case _ =>
          logger.warning("Classpath computation encountered unknown entry: " + cpe)
      }
    }
    computeClasspath(javaProject, false)
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

  def cleanOutputFolders(implicit monitor: IProgressMonitor) = {
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
                      case t => logger.error(t)
                    }
              }
            } else
              delete(cntnr, deleteDirs)(f)
          case file: IFile if f(file.getName) =>
            try {
              file.delete(true, monitor)
            } catch {
              case t => logger.error(t)
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
  def checkClasspathTimeStamp(shouldReset: Boolean): Unit = plugin.check {
    val cp = underlying.getFile(".classpath")
    if (cp.exists)
      classpathUpdate match {
        case IResource.NULL_STAMP => classpathUpdate = cp.getModificationStamp()
        case stamp if stamp == cp.getModificationStamp() =>
        case _ =>
          classpathUpdate = cp.getModificationStamp()
          if (shouldReset) resetCompilers()
      }
  }

  private def refreshOutput: Unit = {
    val res = plugin.workspaceRoot.findMember(javaProject.getOutputLocation)
    if (res ne null)
      res.refreshLocal(IResource.DEPTH_INFINITE, null)
  }

  def initialize(settings: Settings, filter: Settings#Setting => Boolean) = {
    
    // if the workspace project doesn't exist, it is a virtual project used by Eclipse.
    // As such the source folders don't exist.
    if (underlying.exists()) {
      val env = new NameEnvironment(javaProject)
  
      for ((src, dst) <- sourceOutputFolders(env))
        settings.outputDirs.add(EclipseResource(src), EclipseResource(dst))
    }

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

    val store = storage
    for (
      box <- IDESettings.shownSettings(settings);
      setting <- box.userSettings; if filter(setting)
    ) {
      val value0 = store.getString(SettingConverterUtil.convertNameToProperty(setting.name))
//      logger.info("[%s] initializing %s to %s".format(underlying.getName(), setting.name, value0.toString))
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
        case t: Throwable => logger.error("Unable to set setting '" + setting.name + "' to '" + value0 + "'", t)
      }
    }
    
    // handle additional parameters
    val additional = store.getString(CompilerSettings.ADDITIONAL_PARAMS)
    logger.info("setting additional paramters: " + additional)
    settings.processArgumentString(additional)
  }
  
  private def buildManagerInitialize: String =
    storage.getString(SettingConverterUtil.convertNameToProperty(properties.ScalaPluginSettings.buildManager.name))
  
  def storage = {
    val workspaceStore = ScalaPlugin.plugin.getPreferenceStore
    val projectStore = new PropertyStore(underlying, workspaceStore, plugin.pluginId)
    val useProjectSettings = projectStore.getBoolean(SettingConverterUtil.USE_PROJECT_SETTINGS_PREFERENCE)

    if (useProjectSettings) projectStore else workspaceStore
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
  
  /**
   * Performs `op` on the presentation compiler, if the compiler has been initialized. 
   * Otherwise, do nothing (no exception thrown).
   */
  def doWithPresentationCompiler(op: ScalaPresentationCompiler => Unit): Unit = {
    presentationCompiler {
      case Some(c) => op(c)
      case None =>
    }
  }
  
  def defaultOrElse[T]: T = {  
    if (underlying.isOpen)
      failedCompilerInitialization("")
    
    throw InvalidCompilerSettings() 
  }

  /** 
   * If the presentation compiler has failed to initialize and no `orElse` is specified, 
   * the default handler throws an `InvalidCompilerSettings` exception
   * If T = Unit, then doWithPresentationCompiler can be used, which does not throw.
   */
  def withPresentationCompiler[T](op: ScalaPresentationCompiler => T)(orElse: => T = defaultOrElse): T = {
    presentationCompiler {
      case Some(c) => op(c)
      case None => orElse
    }
  }

  def withSourceFile[T](scu: ScalaCompilationUnit)(op: (SourceFile, ScalaPresentationCompiler) => T)(orElse: => T = defaultOrElse): T = {
    withPresentationCompiler { compiler =>
      compiler.withSourceFile(scu)(op)
    } {orElse}
  }

  /** Shutdown the presentation compiler, and force a reinitialization but asking to reconcile all 
   *  compilation units that were serviced by the previous instance of the PC.
   */
  def resetPresentationCompiler() {
    val units: List[ScalaCompilationUnit] = withPresentationCompiler(_.compilationUnits)(Nil)
    
    presentationCompiler.invalidate
    
    logger.info("Scheduling for reconcile: " + units.map(_.file))
    units.foreach(_.scheduleReconcile())
  }

  def buildManager = {
    checkClasspathTimeStamp(shouldReset = true)
    if (buildManager0 == null) {
      val settings = new Settings
      initialize(settings, _ => true)
      // source path should be emtpy. The build manager decides what files get recompiled when.
      // if scalac finds a source file newer than its corresponding classfile, it will 'compileLate'
      // that file, using an AbstractFile/PlainFile instead of the EclipseResource instance. This later
      // causes problems if errors are reported against that file. Anyway, it's wrong to have a sourcepath
      // when using the build manager.
      settings.sourcepath.value = ""
      	
      // Which build manager?
      // We assume that build manager setting has only single box
      val choice = buildManagerInitialize
      choice match {
      	case "refined" =>
      	  logger.info("BM: Refined Build Manager")
      	  buildManager0 = new buildmanager.refined.EclipseRefinedBuildManager(this, settings)
      	case "sbt"  =>
      	  logger.info("BM: SBT enhanced Build Manager for " + ScalaPlugin.plugin.scalaVer + " Scala library")
      	  buildManager0 = new buildmanager.sbtintegration.EclipseSbtBuildManager(this, settings)
      	case _         =>
      	  logger.info("Invalid build manager choice '" + choice  + "'. Setting to (default) refined build manager")
      	  buildManager0 = new buildmanager.refined.EclipseRefinedBuildManager(this, settings)
      }

      //buildManager0 = new EclipseBuildManager(this, settings)
    }
    buildManager0
  }

  /* If true, then it means that all source files have to be reloaded */
  def prepareBuild(): Boolean = if (!hasBeenBuilt) buildManager.invalidateAfterLoad else false
  
  def build(addedOrUpdated: Set[IFile], removed: Set[IFile], monitor: SubMonitor) {
    if (addedOrUpdated.isEmpty && removed.isEmpty)
      return

    hasBeenBuilt = true

    clearBuildErrors
    buildManager.build(addedOrUpdated, removed, monitor)
    refreshOutput

    // Already performs saving the dependencies
    
    if (!buildManager.hasErrors) 
      buildListeners foreach { _.buildSuccessful }
  }
  
  def addBuildSuccessListener(listener: BuildSuccessListener) {
    buildListeners add listener
  }
  
  def removeBuildSuccessListener(listener: BuildSuccessListener) {
    buildListeners remove listener
  }

  def clean(implicit monitor: IProgressMonitor) = {
    underlying.deleteMarkers(plugin.problemMarkerId, true, IResource.DEPTH_INFINITE)
    resetCompilers
    if (buildManager0 != null)
      buildManager0.clean(monitor)
    cleanOutputFolders
  }

  def resetBuildCompiler(implicit monitor: IProgressMonitor) {
    buildManager0 = null
    hasBeenBuilt = false
  }

  def resetCompilers(implicit monitor: IProgressMonitor = null) = {
    logger.info("resetting compilers!  project: " + this.toString)
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

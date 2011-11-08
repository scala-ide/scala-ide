/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import scala.collection.immutable
import scala.collection.mutable
import java.io.File.pathSeparator
import org.eclipse.core.resources.{ IContainer, IFile, IFolder, IMarker, IProject, IResource, IResourceProxy, IResourceProxyVisitor }
import org.eclipse.core.runtime.{ FileLocator, IPath, IProgressMonitor, Path, SubMonitor }
import org.eclipse.jdt.core.{ IClasspathEntry, IJavaProject, JavaCore }
import org.eclipse.jdt.core.compiler.IProblem
import org.eclipse.jdt.internal.core.JavaProject
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
import scala.collection.mutable.ListBuffer
import scala.actors.Actor
import org.eclipse.jdt.core.IJarEntryResource
import java.util.Properties
import org.eclipse.jdt.core.IPackageFragmentRoot
import org.eclipse.core.runtime.jobs.Job
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.Status

trait BuildSuccessListener {
  def buildSuccessful(): Unit
}

object ScalaProject {
  def apply(underlying: IProject) = new ScalaProject(underlying)
}

class ScalaProject private (val underlying: IProject) extends HasLogger {
  import ScalaPlugin.plugin

  private var classpathUpdate: Long = IResource.NULL_STAMP
  private var buildManager0: EclipseBuildManager = null
  private var hasBeenBuilt = false
  
  private var classpathCheckLock= new Object
  private var classpathHasBeenChecked= false
  private var classpathValid= false;
  
  private val buildListeners = new mutable.HashSet[BuildSuccessListener]

  case class InvalidCompilerSettings() extends RuntimeException(
        "Scala compiler cannot initialize for project: " + underlying.getName +
    		". Please check that your classpath contains the standard Scala library.")

  private val presentationCompiler = new Cached[Option[ScalaPresentationCompiler]] {
    override def create() = {
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
  
  /** Does this project have the Scala nature? */
  def hasScalaNature = 
    ScalaPlugin.plugin.isScalaProject(underlying)

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

  /** Return the output folders of this project. Paths are relative to the workspace root, 
   *  and they are handles only (may not exist).
   */
  def outputFolders: Seq[IPath] =
    sourceOutputFolders map (_._2.getFullPath())

  def classpath: Seq[IPath] = {
    val path = new mutable.LinkedHashSet[IPath]

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

  /** Return the source folders and their corresponding output locations
   *  without relying on NameEnvironment. Does not create folders if they
   *  don't exist already. 
   *  
   *  @return A sequence of pairs of source folders and their corresponding
   *          output folder.
   */
  def sourceOutputFolders: Seq[(IContainer, IContainer)] = {
    val cpes = javaProject.getResolvedClasspath(true)

    for (cpe <- cpes if cpe.getEntryKind == IClasspathEntry.CPE_SOURCE) yield {
      val cpeOutput = cpe.getOutputLocation
      val outputLocation = if (cpeOutput != null) cpeOutput else javaProject.getOutputLocation

      val wsroot = ScalaPlugin.plugin.workspaceRoot
      val binPath = wsroot.getFolder(outputLocation)  // may not exist
      val srcContainer = Option(wsroot.findMember(cpe.getPath()).asInstanceOf[IContainer]) getOrElse {
        // may be null if source folder does not exist
        logger.debug("Could not retrieve source folder %s for project %s".format(cpe.getPath(), underlying))
        wsroot.getFolder(cpe.getPath())
      }
      
      (srcContainer, binPath)
    }
  }
  
  /** Return all source files in the source path. It only returns buildable files (meaning
   *  Java or Scala sources).
   */
  def allSourceFiles(): Set[IFile] = {
    allFilesInSourceDirs() filter (f => plugin.isBuildable(f.getName))
  }
  
  /** Return all the files in the current project. It walks all source entries in the classpath
   *  and respects inclusion and exclusion filters. It returns both buildable files (java or scala)
   *  and all other files in the source path.
   */
  def allFilesInSourceDirs(): Set[IFile] = {
    /** Cache it for the duration of this call */
    lazy val currentSourceOutputFolders = sourceOutputFolders
    
    /** Return the inclusion patterns of `entry` as an Array[Array[Char]], ready for consumption
     *  by the JDT.
     *  
     *  @see org.eclipse.jdt.internal.core.ClassPathEntry.fullInclusionPatternChars()
     */
    def fullPatternChars(entry: IClasspathEntry, patterns: Array[IPath]): Array[Array[Char]] = {
      if (patterns.isEmpty) 
        null
      else {
        val prefixPath = entry.getPath().removeTrailingSeparator();
        for (pattern <- patterns) 
          yield prefixPath.append(pattern).toString().toCharArray();
      }
    }

    /** Logic is copied from existing code ('isExcludedFromProject'). Code is trying to 
     *  see if the given path is a source or output folder for any source entry in the
     *  classpath of this project.
     */
    def sourceOrBinaryFolder(path: IPath): Boolean = {
      if (path.segmentCount() > 2) return false // is a subfolder of a package

      currentSourceOutputFolders exists {
        case (srcFolder, binFolder) =>
          (srcFolder.getFullPath() == path || binFolder.getFullPath() == path)
      }
    }

    var sourceFiles = new immutable.HashSet[IFile]
    
    for {
      srcEntry <- javaProject.getResolvedClasspath(true)
      if srcEntry.getEntryKind() == IClasspathEntry.CPE_SOURCE
      val srcFolder = ScalaPlugin.plugin.workspaceRoot.findMember(srcEntry.getPath()) 
      if srcFolder ne null
    } {
      val inclusionPatterns = fullPatternChars(srcEntry, srcEntry.getInclusionPatterns())
      val exclusionPatterns = fullPatternChars(srcEntry, srcEntry.getExclusionPatterns())
      val isAlsoProject = srcFolder == underlying  // source folder is the project itself

      srcFolder.accept(
        new IResourceProxyVisitor {
          def visit(proxy: IResourceProxy): Boolean = {
            proxy.getType match {
              case IResource.FILE =>
                if (!Util.isExcluded(proxy.requestFullPath(), inclusionPatterns, exclusionPatterns, false))
                  sourceFiles += proxy.requestResource().asInstanceOf[IFile] // must be an IFile, otherwise we wouldn't be here
                
                false // don't recurse, it's a file anyway

              case IResource.FOLDER =>
                if (isAlsoProject) {
                  !sourceOrBinaryFolder(proxy.requestFullPath)  // recurse if not on a source or binary folder path
                } else if (exclusionPatterns != null) {
                  if (Util.isExcluded(proxy.requestFullPath, inclusionPatterns, exclusionPatterns, true)) {
                    // must walk children if inclusionPatterns != null, can skip them if == null
                    // but folder is excluded so do not create it in the output folder
                    inclusionPatterns != null
                  } else true
                } else true  // recurse into subfolders
                
              case _ =>
                true
            }
         }
        }, IResource.NONE)
    }
    
    sourceFiles
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

  /**
   * Manage the possible classpath error/warning reported on the project.
   */
  private def setClasspathError(severity: Int, message: String) {
    // set the state
    classpathValid= severity != IMarker.SEVERITY_ERROR
    classpathHasBeenChecked= true
    
    // the marker manipulation need to be done in a Job, because it requires
    // a change on the IProject, which is locked for modification during
    // the classpath change notification
    val markerJob= new Job("Update classpath error marker") {
      override def run(monitor: IProgressMonitor): IStatus = {
        if (underlying.isOpen()) { // cannot change markers on closed project
          // clean the markers
          underlying.deleteMarkers(plugin.problemMarkerId, false, IResource.DEPTH_ZERO)
          
          // add a new marker if needed
          severity match {
            case IMarker.SEVERITY_ERROR | IMarker.SEVERITY_WARNING =>
              val marker= underlying.createMarker(plugin.problemMarkerId)
              marker.setAttribute(IMarker.MESSAGE, message)
              marker.setAttribute(IMarker.SEVERITY, severity)
            case _ =>
          }
        }
        Status.OK_STATUS
      }
    }
    markerJob.setRule(underlying)
    markerJob.schedule()
  }
  
  /**
   * Return <code>true</code> if the classpath is deemed valid.
   * Check the classpath if it has not been checked yet.
   */
  def isClasspathValid(): Boolean = {
    classpathCheckLock.synchronized {
      if (!classpathHasBeenChecked)
        checkClasspath()
      classpathValid
    }
  }
  
  /**
   * Check if the classpath is valid for scala.
   * It is said valid if it contains one and only scala library jar, with a version compatible
   * with the one from the scala-ide plug-in
   */
  def classpathHasChanged() {
    classpathCheckLock.synchronized {
      try {
        // mark as in progress
        classpathHasBeenChecked= false
        checkClasspath()
        if (classpathValid) {
          // no point to reset the compilers on an invalid classpath,
          // it would not work anyway
          resetCompilers()
        }
      }
    }
  }

  private def checkClasspath() {
    // look for all package fragment roots containing instances of scala.Predef
    val fragmentRoots = new ListBuffer[IPackageFragmentRoot]
    for (fragmentRoot <- javaProject.getAllPackageFragmentRoots()) {
      val fragment = fragmentRoot.getPackageFragment("scala")
      fragmentRoot.getKind() match {
        case IPackageFragmentRoot.K_BINARY =>
          if (fragment.getClassFile("Predef.class").exists())
            fragmentRoots += fragmentRoot
        case _ => // look only in jars. SBT doesn't start without one, and refined is not really happy either
      }
    }

    // check the found package fragment roots
    fragmentRoots.length match {
      case 0 => // unable to find any trace of scala library
        setClasspathError(IMarker.SEVERITY_ERROR, "Unable to find a scala library. Please add the scala container or a scala library jar to the build path.")
      case 1 => // one and only one, now check if the version number is contained in library.properties
        getVersionNumber(fragmentRoots(0)) match {
          case Some(v) if v == plugin.scalaVer =>
            // exactly the same version, should be from the container. Perfect
            setClasspathError(0, null)
          case v if plugin.isCompatibleVersion(v) =>
            // compatible version (major, minor are the same). Still, add warning message
            setClasspathError(IMarker.SEVERITY_WARNING, "The version of scala library found in the build path is different from the one provided by scala IDE: " + v.get + ". Expected: " + plugin.scalaVer + ". Make sure you know what you are doing.")
          case Some(v) =>
            // incompatible version
            setClasspathError(IMarker.SEVERITY_ERROR, "The version of scala library found in the build path is incompatible with the one provided by scala IDE: " + v + ". Expected: " + plugin.scalaVer + ". Please replace the scala library with the scala container or a compatible scala library jar.")
          case None =>
            // no version found
            setClasspathError(IMarker.SEVERITY_ERROR, "The scala library found in the build path doesn't expose its version. Please replace the scala library with the scala container or a valid scala library jar")
        }
      case _ => // 2 or more of them, not great
        if (fragmentRoots.exists(fragmentRoot => !plugin.isCompatibleVersion(getVersionNumber(fragmentRoot))))
          setClasspathError(IMarker.SEVERITY_ERROR, "More than one scala library found in the build path, including at least one with an incompatible version. Please update the project build path so it contains only compatible scala libraries")
        else
          setClasspathError(IMarker.SEVERITY_WARNING, "More than one scala library found in the build path, all with compatible versions. This is not an optimal configuration, try to limit to one scala library in the build path.")
    }
  }
  
  /**
   * Return the version number contained in library.properties if it exists.
   */
  private def getVersionNumber(fragmentRoot: IPackageFragmentRoot): Option[String] = {
    for (resource <- fragmentRoot.getNonJavaResources())
      resource match {
        case jarEntry: IJarEntryResource if jarEntry.isFile() && "library.properties".equals(jarEntry.getName) =>
          val properties = new Properties()
          properties.load(jarEntry.getContents())
          val version = properties.getProperty("version.number")
          if (version == null) {
            return None
          }
          return Option(version)
        case _ =>
    }
    None
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
      for ((src, dst) <- sourceOutputFolders) {
        logger.debug("Added output folder: " + src + ": " + dst)
        settings.outputDirs.add(EclipseResource(src), EclipseResource(dst))
      }
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

    logger.debug("CLASSPATH: " + classpath.mkString("\n"))
    logger.debug("SOURCEPATH: " + sfs.mkString("\n"))
    
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

  /** Shutdown the presentation compiler, and force a re-initialization but asking to reconcile all 
   *  compilation units that were serviced by the previous instance of the PC. Does nothing if
   *  the presentation compiler is not yet initialized.
   *  
   *  @return true if the presentation compiler was initialized at the time of this call.
   */
  def resetPresentationCompiler(): Boolean =
    if (presentationCompiler.initialized) {
      val units: Seq[ScalaCompilationUnit] = withPresentationCompiler(_.compilationUnits)(Nil)
      
      presentationCompiler.invalidate
      
      logger.info("Scheduling for reconcile: " + units.map(_.file))
      units.foreach(_.scheduleReconcile())
      true
    } else {
      logger.info("[%s] Presentation compiler was not yet initialized, ignoring reset.".format(underlying.getName()))
      false
    }

  def buildManager = {
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
    
    if (!buildManager.hasErrors) {
      // reset presentation compilers of projects that depend on this one
      // since the output directory now contains the up-to-date version of this project
      // note: ScalaBuilder resets the presentation compiler when a referred project
      // is built, but only when it has changes! this call makes sure that a rebuild,
      // even when there are no changes, propagates the classpath to dependent projects
      resetDependentProjects()
      buildListeners foreach { _.buildSuccessful }
    }
  }

  /** Reset the presentation compiler of projects that depend on this one.
   *  This should be done after a successful build, since the output directory
   *  now contains an up-to-date version of this project.
   */
  def resetDependentProjects() {
    for {
      prj <- underlying.getReferencingProjects()
      if prj.isOpen() && ScalaPlugin.plugin.isScalaProject(prj)
      dependentScalaProject <- ScalaPlugin.plugin.asScalaProject(prj)
    } {
      logger.debug("[%s] Reset PC of referring project %s".format(this, dependentScalaProject))
      dependentScalaProject.resetPresentationCompiler()
    }
  }
  
  def addBuildSuccessListener(listener: BuildSuccessListener) {
    buildListeners add listener
  }
  
  def removeBuildSuccessListener(listener: BuildSuccessListener) {
    buildListeners remove listener
  }

  def clean(implicit monitor: IProgressMonitor) = {
    underlying.deleteMarkers(plugin.problemMarkerId, true, IResource.DEPTH_INFINITE)
    // mark the classpath as not checked
    classpathCheckLock.synchronized {
      classpathHasBeenChecked= false
    }
    if (buildManager0 != null)
      buildManager0.clean(monitor)
    cleanOutputFolders
    resetCompilers // reset them only after the output directory is emptied
  }

  def resetBuildCompiler() {
    buildManager0 = null
    hasBeenBuilt = false
  }

  def resetCompilers(implicit monitor: IProgressMonitor = null) = {
    logger.info("resetting compilers!  project: " + this.toString)
    resetBuildCompiler()
    resetPresentationCompiler()
  }
  
  def shutDownCompilers() {
    resetBuildCompiler()
    shutDownPresentationCompiler()
  }
  
  /** Shut down presentation compiler without scheduling a reconcile for open files. */
  def shutDownPresentationCompiler() {
    presentationCompiler.invalidate()
  }
}

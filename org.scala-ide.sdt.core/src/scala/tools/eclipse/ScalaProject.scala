/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import java.io.File.pathSeparator

import scala.collection.immutable
import scala.collection.mutable
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.eclipse.logging.HasLogger
import scala.tools.eclipse.properties.{CompilerSettings, IDESettings, PropertyStore}
import scala.tools.eclipse.ui.PartAdapter
import scala.tools.eclipse.util.{Cached, EclipseResource, Trim, Utils}
import scala.tools.eclipse.util.SWTUtils.asyncExec
import scala.tools.nsc.{Settings, MissingRequirementError}
import scala.tools.nsc.util.BatchSourceFile
import scala.tools.nsc.util.SourceFile

import org.eclipse.core.resources.{IContainer, IFile, IMarker, IProject, IResource, IResourceProxy, IResourceProxyVisitor}
import org.eclipse.core.runtime.{IPath, IProgressMonitor, Path, SubMonitor}
import org.eclipse.jdt.core.{IClasspathEntry, IJavaProject, JavaCore, JavaModelException}
import org.eclipse.jdt.internal.core.util.Util
import org.eclipse.ui.IEditorPart
import org.eclipse.ui.IPartListener
import org.eclipse.ui.IWorkbenchPart
import org.eclipse.ui.part.FileEditorInput
import org.eclipse.jface.preference.IPreferenceStore

trait BuildSuccessListener {
  def buildSuccessful(): Unit
}

object ScalaProject {
  def apply(underlying: IProject): ScalaProject = {
    val project = new ScalaProject(underlying)
    project.init()
    project
  }

  /** Listen for [[IWorkbenchPart]] event and takes care of loading/discarding scala compilation units.*/
  private class ProjectPartListener(project: ScalaProject) extends PartAdapter with HasLogger {
    override def partOpened(part: IWorkbenchPart) {
      doWithCompilerAndFile(part) { (compiler, ssf) =>
        logger.debug("open " + part.getTitle)
        ssf.forceReload()
      }
    }

    override def partClosed(part: IWorkbenchPart) {
      doWithCompilerAndFile(part) { (compiler, ssf) =>
        logger.debug("close " + part.getTitle)
        ssf.discard()
      }
    }

    private def doWithCompilerAndFile(part: IWorkbenchPart)(op: (ScalaPresentationCompiler, ScalaSourceFile) => Unit) {
      part match {
        case editor: IEditorPart =>
          editor.getEditorInput match {
            case fei: FileEditorInput =>
              val f = fei.getFile
              if (f.getProject == project.underlying &&  f.getName.endsWith(ScalaPlugin.plugin.scalaFileExtn)) {
                for (ssf <- ScalaSourceFile.createFromPath(f.getFullPath.toString)) {
                  if (project.underlying.isOpen)
                    project.doWithPresentationCompiler(op(_, ssf)) // so that an exception is not thrown
                }
              }
            case _ =>
          }
        case _ =>
      }
    }
  }
}

class ScalaProject private (val underlying: IProject) extends ClasspathManagement with HasLogger {
  import ScalaPlugin.plugin

  private var classpathUpdate: Long = IResource.NULL_STAMP
  private var buildManager0: EclipseBuildManager = null
  private var hasBeenBuilt = false
  
  private val buildListeners = new mutable.HashSet[BuildSuccessListener]
  
  private val worbenchPartListener: IPartListener = new ScalaProject.ProjectPartListener(this)

  case class InvalidCompilerSettings() extends RuntimeException(
        "Scala compiler cannot initialize for project: " + underlying.getName +
              ". Please check that your classpath contains the standard Scala library.")

  private val presentationCompiler = new Cached[Option[ScalaPresentationCompiler]] {
    override def create() = {
      try {
        val settings = ScalaPlugin.defaultScalaSettings()
        initializeCompilerSettings(settings, isPCSetting(settings))
        val pc = new ScalaPresentationCompiler(ScalaProject.this, settings)
        logger.debug("Presentation compiler classpath: " + pc.classPath)
        pc.askOption(() => pc.initializeRequiredSymbols())
        Some(pc)
      } catch {
        case ex @ MissingRequirementError(required) =>
          failedCompilerInitialization("could not find a required class: " + required)
          eclipseLog.error(ex)
          None
        case ex: Throwable =>
          logger.info("Throwable when intializing presentation compiler!!! " + ex.getMessage)
          ex.printStackTrace()
          if (underlying.isOpen)
            failedCompilerInitialization("error initializing Scala compiler")
          eclipseLog.error(ex)          
          None
      }
    }

    override def destroy(compiler: Option[ScalaPresentationCompiler]) {
      compiler.map(_.destroy())
    }
  }

  /** To avoid letting 'this' reference escape during initialization, this method is called right after a 
   * [[ScalaPlugin]] instance has been fully initialized.
   */
  private def init(): Unit = {
    if(!ScalaPlugin.plugin.headlessMode)
      ScalaPlugin.getWorkbenchWindow map (_.getPartService().addPartListener(worbenchPartListener))
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
        Ylogcp,
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
    logger.debug("failedCompilerInitialization: " + msg)
    import org.eclipse.jface.dialogs.MessageDialog
    synchronized {
      // FIXME: What's the purpose of having an `asyncExex` in a synchronized block?
      if (!plugin.headlessMode && !messageShowed) {
        messageShowed = true
        asyncExec {
          val doAdd = MessageDialog.openQuestion(ScalaPlugin.getShell, "Add Scala library to project classpath?", 
              ("There was an error initializing the Scala compiler: %s. \n\n"+
               "The editor compiler will be restarted when the project is cleaned or the classpath is changed.\n\n" + 
               "Add the Scala library to the classpath of project %s?") 
              .format(msg, underlying.getName))
          if (doAdd) {
            Utils tryExecute {
              Nature.addScalaLibAndSave(underlying)
            }
          }
        }
      }
    }
  }
  
  /** Does this project have the Scala nature? */
  def hasScalaNature: Boolean = plugin.isScalaProject(underlying)

  private def settingsError(severity: Int, msg: String, monitor: IProgressMonitor): Unit = {
    val mrk = underlying.createMarker(plugin.settingProblemMarkerId)
    mrk.setAttribute(IMarker.SEVERITY, severity)
    mrk.setAttribute(IMarker.MESSAGE, msg)
  }

  /** Deletes the build problem marker associated to {{{this}}} Scala project. */
  private def clearBuildProblemMarker(): Unit =
    if (isUnderlyingValid) {
      underlying.deleteMarkers(plugin.problemMarkerId, true, IResource.DEPTH_ZERO)
    }

 
  /** Deletes all build problem markers for all resources in {{{this}}} Scala project. */
  private def clearAllBuildProblemMarkers(): Unit = {
    if (isUnderlyingValid) {
      underlying.deleteMarkers(plugin.problemMarkerId, true, IResource.DEPTH_INFINITE)
    }
  }

  private def clearSettingsErrors(): Unit =
    if (isUnderlyingValid) {
      underlying.deleteMarkers(plugin.settingProblemMarkerId, true, IResource.DEPTH_ZERO)
    }

  
  /** The direct dependencies of this project. It only returns opened projects. */
  def directDependencies: Seq[IProject] = 
    underlying.getReferencedProjects.filter(_.isOpen)

  /** All direct and indirect dependencies of this project.
   * 
   *  Indirect dependencies are considered only if that dependency is exported by the dependent project.
   *  Consider the following dependency graph:
   *     A -> B -> C
   *     
   *  transitiveDependencies(C) = {A, B} iff B *exports* the A project in its classpath
   */
  def transitiveDependencies: Seq[IProject] =
    directDependencies ++ (directDependencies flatMap (p => plugin.getScalaProject(p).exportedDependencies))
  
  /** Return the exported dependencies of this project. An exported dependency is
   *  another project this project depends on, and which is exported to downstream
   *  dependencies.
   */
  def exportedDependencies: Seq[IProject] = {
    for { entry <- resolvedClasspath
      if entry.getEntryKind == IClasspathEntry.CPE_PROJECT && entry.isExported
    } yield plugin.workspaceRoot.getProject(entry.getPath().toString)
  }
    
  lazy val javaProject: IJavaProject = JavaCore.create(underlying)

  def sourceFolders: Seq[IPath] = {
    for {
      cpe <- resolvedClasspath if cpe.getEntryKind == IClasspathEntry.CPE_SOURCE
      resource <- Option(plugin.workspaceRoot.findMember(cpe.getPath)) if resource.exists
    } yield resource.getLocation
  }

  /** Return the output folders of this project. Paths are relative to the workspace root, 
   *  and they are handles only (may not exist).
   */
  def outputFolders: Seq[IPath] =
    sourceOutputFolders map (_._2.getFullPath)

  /** The output folder file-system absolute paths. */
  def outputFolderLocations: Seq[IPath] = 
    sourceOutputFolders map (_._2.getLocation)

  /** Return the source folders and their corresponding output locations
   *  without relying on NameEnvironment. Does not create folders if they
   *  don't exist already. 
   *  
   *  @return A sequence of pairs of source folders and their corresponding
   *          output folder.
   */
  def sourceOutputFolders: Seq[(IContainer, IContainer)] = {
    val cpes = resolvedClasspath

    for {
      cpe <- cpes if cpe.getEntryKind == IClasspathEntry.CPE_SOURCE
      source <- Option(plugin.workspaceRoot.findMember(cpe.getPath)) if source.exists
    } yield {
      val cpeOutput = cpe.getOutputLocation
      val outputLocation = if (cpeOutput != null) cpeOutput else javaProject.getOutputLocation

      val wsroot = plugin.workspaceRoot
      val binPath = wsroot.getFolder(outputLocation)  // may not exist
      
      (source.asInstanceOf[IContainer], binPath)
    }
  }

  private def isUnderlyingValid = (underlying.exists() && underlying.isOpen)

  /**
   * This function checks that the underlying project is closed, if not, return the classpath, otherwise return Nil, 
   * so avoids throwing an exceptions.
   *  @return the classpath or Nil, if the underlying project is closed.
   */ 
  private def resolvedClasspath = 
     try {
       if (isUnderlyingValid) {
         javaProject.getResolvedClasspath(true).toList
       } else {
         Nil
       }
     } catch { 
       case e: JavaModelException => logger.error(e); Nil
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
      srcFolder = plugin.workspaceRoot.findMember(srcEntry.getPath()) 
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
  
  private def cleanOutputFolders(implicit monitor: IProgressMonitor) = {
    def delete(container: IContainer, deleteDirs: Boolean)(f: String => Boolean): Unit =
      if (container.exists()) {
        container.members.foreach {
          case cntnr: IContainer =>
            if (deleteDirs) {
              try {
                cntnr.delete(true, monitor) // might not work.
              } catch {
                case _: Exception =>
                  delete(cntnr, deleteDirs)(f)
                  if (deleteDirs)
                    try {
                      cntnr.delete(true, monitor) // try again
                    } catch {
                      case t: Exception => eclipseLog.error(t)
                    }
              }
            } else
              delete(cntnr, deleteDirs)(f)
          case file: IFile if f(file.getName) =>
            try {
              file.delete(true, monitor)
            } catch {
              case t: Exception => eclipseLog.error(t)
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

  private def refreshOutputFolders(): Unit = {
    sourceOutputFolders foreach {
      case (_, binFolder) =>
        binFolder.refreshLocal(IResource.DEPTH_INFINITE, null)
        // make sure the folder is marked as Derived, so we don't see classfiles in Open Resource
        // but don't set it unless necessary (this might be an expensive operation)
        if (!binFolder.isDerived && binFolder.exists)
          binFolder.setDerived(true, null)
    }
  }

  // TODO Per-file encodings
  private def encoding: Option[String] =
    sourceFolders.headOption flatMap { path =>
      plugin.workspaceRoot.findContainersForLocation(path) match {
        case Array(container) => Some(container.getDefaultCharset)
        case _ => None
      }
    }

  private def shownSettings(settings: Settings, filter: Settings#Setting => Boolean): Seq[(Settings#Setting, String)] = {
    // save the current preferences state, so we don't go through the logic of the workspace
    // or project-specific settings for each setting in turn.
    val currentStorage = storage
    for {
      box <- IDESettings.shownSettings(settings)
      setting <- box.userSettings if filter(setting)
      value <- Trim(currentStorage.getString(SettingConverterUtil.convertNameToProperty(setting.name)))
    } yield (setting, value)
  }

  def scalacArguments: Seq[String] = {
    import ScalaPlugin.defaultScalaSettings
    val encArgs = encoding.toSeq flatMap (Seq("-encoding", _))

    val shownArgs = {
      val defaultSettings = defaultScalaSettings()
      setupCompilerClasspath(defaultSettings)
      val userSettings = for ((setting, value) <- shownSettings(defaultSettings, _ => true)) yield {
        initializeSetting(setting, value)
        setting
      }

      /* Here we make sure that the default plugins directory location is part of the returned scalac arguments. 
       * This is needed to enable continuations, when the user didn't provide an explicit path in the -Xpluginsdir
       * compiler setting.    
       */
      val pluginsDirSetting = {
        // if the user provided an explicit path for -Xpluginsdir, then it's all good.
        if (userSettings.exists(setting => setting.name == defaultSettings.pluginsDir)) None
        // otherwise, inject the `pluginsDir` setting as defined in `ScalaPlugin.defaultScalaSettings`, i.e., it will
        // inject the default location where the scala-continuations-plugin.jar can be found. Mind that this location can change
        // every time the user updates the Scala IDE.
        else Some(defaultSettings.pluginsDir)
      }

      val classpathSettings = List(defaultSettings.javabootclasspath, defaultSettings.bootclasspath)

      (classpathSettings ++ pluginsDirSetting.toList ++ userSettings) map (_.unparse)
    }
    val extraArgs = defaultScalaSettings().splitParams(storage.getString(CompilerSettings.ADDITIONAL_PARAMS))
    shownArgs.flatten ++ encArgs ++ extraArgs
  }

  private def initializeSetting(setting: Settings#Setting, propValue: String) {
    try {
      setting.tryToSetFromPropertyValue(propValue)
      logger.debug("[%s] initializing %s to %s".format(underlying.getName(), setting.name, setting.value.toString))
    } catch {
      case t: Throwable => eclipseLog.error("Unable to set setting '" + setting.name + "' to '" + propValue + "'", t)
    }
  }
  
  def initializeCompilerSettings(settings: Settings, filter: Settings#Setting => Boolean): Unit = {
    // if the workspace project doesn't exist, it is a virtual project used by Eclipse.
    // As such the source folders don't exist.
    if (underlying.exists())
      for ((src, dst) <- sourceOutputFolders) {
        logger.debug("Added output folder: " + src + ": " + dst)
        settings.outputDirs.add(EclipseResource(src), EclipseResource(dst))
      }
    
    for(enc <- encoding)
      settings.encoding.value = enc

    setupCompilerClasspath(settings)
    settings.sourcepath.value = sourceFolders.map(_.toOSString).mkString(pathSeparator)

    for ((setting, value) <- shownSettings(settings, filter)) {
      initializeSetting(setting, value)
    }

    // handle additional parameters
    val additional = storage.getString(CompilerSettings.ADDITIONAL_PARAMS)
    logger.info("setting additional parameters: " + additional)
    settings.processArgumentString(additional)
  }

  private def setupCompilerClasspath(settings: Settings) {
    val scalaCp = scalaClasspath // don't need to recompute it each time we use it

    settings.javabootclasspath.value = scalaCp.jdkPaths.map(_.toOSString).mkString(pathSeparator)
    settings.classpath.value = (scalaCp.userCp ++ scalaCp.scalaLib.toSeq).map(_.toOSString).mkString(pathSeparator)
    scalaCp.scalaLib.foreach(scalaLib => settings.bootclasspath.value = scalaLib.toOSString)

    logger.debug("javabootclasspath: " + settings.javabootclasspath.value)
    logger.debug("scalabootclasspath: " + settings.bootclasspath.value)
    logger.debug("user classpath: " + settings.classpath.value)
  }

  private def buildManagerInitialize: String =
    storage.getString(SettingConverterUtil.convertNameToProperty(properties.ScalaPluginSettings.buildManager.name))

  /** Does this project use project-specific compiler settings? */
  def usesProjectSettings: Boolean =
    projectSpecificStorage.getBoolean(SettingConverterUtil.USE_PROJECT_SETTINGS_PREFERENCE)

  /** Return a the project-specific preference store. This does not take into account the
   *  user-preference whether to use project-specific compiler settings or not.
   *
   *  @note This can't be a `val` or a `def`, because of the way `PropertyStore` is implemented.
   *  @see  #1001241.
   *  @see `storage` for a method that decides based on user preference
   */
  def projectSpecificStorage: IPreferenceStore = {
    new PropertyStore(underlying, ScalaPlugin.prefStore, plugin.pluginId)
  }

  /** Return the current project preference store.
   *
   *  The returned store won't track changes happening in the background, so it represents a
   *  snapshot of this project's settings.
   *
   *  @note This can't be a `val` or a `def`, because of the way `PropertyStore` is implemented.
   *  @see the half-broken implementation of `PropertyStore`
   *  @see  #1001241.
   */
  def storage: IPreferenceStore = {
    val workspaceStore = ScalaPlugin.prefStore
    val projectStore = projectSpecificStorage
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

  def withSourceFile[T](scu: InteractiveCompilationUnit)(op: (SourceFile, ScalaPresentationCompiler) => T)(orElse: => T = defaultOrElse): T = {
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
      val units: Seq[InteractiveCompilationUnit] = withPresentationCompiler(_.compilationUnits)(Nil)
      
      shutDownPresentationCompiler()
      
      val existingUnits = units.filter(_.exists) 
      logger.info("Scheduling for reconcile: " + existingUnits.map(_.file))
      existingUnits.foreach(_.scheduleReconcile())
      true
    } else {
      logger.info("[%s] Presentation compiler was not yet initialized, ignoring reset.".format(underlying.getName()))
      false
    }

  def buildManager: EclipseBuildManager = {
    if (buildManager0 == null) {
      val settings = ScalaPlugin.defaultScalaSettings(msg => settingsError(IMarker.SEVERITY_ERROR, msg, null))
      clearSettingsErrors()
      initializeCompilerSettings(settings, _ => true)
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
        case "sbt"  =>
          logger.info("BM: SBT enhanced Build Manager for " + plugin.scalaVer + " Scala library")
          buildManager0 = new buildmanager.sbtintegration.EclipseSbtBuildManager(this, settings)
        case _ =>
          logger.info("Invalid build manager choice '" + choice  + "'. Setting to (default) sbt build manager")
          buildManager0 = new buildmanager.sbtintegration.EclipseSbtBuildManager(this, settings)
      }

      //buildManager0 = new EclipseBuildManager(this, settings)
    }
    buildManager0
  }

  /* If true, then it means that all source files have to be reloaded */
  def prepareBuild(): Boolean = if (!hasBeenBuilt) buildManager.invalidateAfterLoad else false
  
  def build(addedOrUpdated: Set[IFile], removed: Set[IFile], monitor: SubMonitor) {
    hasBeenBuilt = true

    clearBuildProblemMarker()
    buildManager.build(addedOrUpdated, removed, monitor)
    refreshOutputFolders()

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
      if prj.isOpen() && plugin.isScalaProject(prj)
      dependentScalaProject <- plugin.asScalaProject(prj)
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
    clearAllBuildProblemMarkers()
    resetClasspathCheck()
    
    if (buildManager0 != null)
      buildManager0.clean(monitor)
    cleanOutputFolders
    resetCompilers // reset them only after the output directory is emptied
  }

  private def resetBuildCompiler() {
    buildManager0 = null
    hasBeenBuilt = false
  }

  protected def resetCompilers(implicit monitor: IProgressMonitor = null) = {
    logger.info("resetting compilers!  project: " + this.toString)
    resetBuildCompiler()
    resetPresentationCompiler()
  }
  
  def shutDownCompilers() {
    logger.info("shutting down compilers for " + this)
    resetBuildCompiler()
    shutDownPresentationCompiler()
  }
  
  /** Shut down presentation compiler without scheduling a reconcile for open files. */
  private def shutDownPresentationCompiler() {
    presentationCompiler.invalidate()
  }

  /**
   * Tell the presentation compiler to refresh the given files,
   * if they are not managed by the presentation compiler already.
   */
  def refreshChangedFiles(files: List[IFile]) {
    // transform to batch source files
    val abstractfiles = files.collect {
      // When a compilation unit is moved (e.g. using the Move refactoring) between packages, 
      // an ElementChangedEvent is fired but with the old IFile name. Ignoring the file does
      // not seem to cause any bad effects later on, so we simply ignore these files -- Mirko
      // using an Util class from jdt.internal to read the file, Eclipse doesn't seem to
      // provide an API way to do it -- Luc
      case file if file.exists => new BatchSourceFile(EclipseResource(file), Util.getResourceContentsAsCharArray(file))
    }
      
    withPresentationCompiler {compiler =>
      import compiler._
      // only the files not already managed should be refreshed
      val notLoadedFiles= abstractfiles.filter(compiler.getUnitOf(_).isEmpty)
            
      notLoadedFiles.foreach(file => {
        // call askParsedEntered to force the refresh without loading the file
        val r = new Response[Tree]
        compiler.askParsedEntered(file, false, r)
        r.get.left
      })
      
      // reconcile the opened editors if some files have been refreshed
      if (notLoadedFiles.nonEmpty)
        compiler.compilationUnits.foreach(_.scheduleReconcile())
    }(Nil)
  }
  
  def dispose(): Unit = {
    if(!ScalaPlugin.plugin.headlessMode)
      ScalaPlugin.getWorkbenchWindow map (_.getPartService().removePartListener(worbenchPartListener))
    shutDownCompilers()
  }

  override def toString: String = underlying.getName
}

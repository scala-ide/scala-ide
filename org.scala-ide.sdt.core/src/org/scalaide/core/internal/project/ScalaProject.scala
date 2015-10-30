package org.scalaide.core.internal.project

import java.io.File.pathSeparator
import scala.Right
import scala.annotation.tailrec
import scala.collection.immutable
import scala.collection.mutable
import scala.collection.mutable.Publisher
import scala.reflect.internal.util.SourceFile
import scala.tools.nsc.Settings
import scala.tools.nsc.settings.ScalaVersion
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import org.eclipse.core.resources.IContainer
import org.eclipse.core.resources.IFile
import org.eclipse.core.resources.IMarker
import org.eclipse.core.resources.IProject
import org.eclipse.core.resources.IResource
import org.eclipse.core.resources.IResourceProxy
import org.eclipse.core.resources.IResourceProxyVisitor
import org.eclipse.core.resources.ProjectScope
import org.eclipse.core.runtime.IPath
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.Path
import org.eclipse.core.runtime.SubMonitor
import org.eclipse.jdt.core.IClasspathEntry
import org.eclipse.jdt.core.JavaCore
import org.eclipse.jdt.core.JavaModelException
import org.eclipse.jdt.internal.core.util.Util
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.util.IPropertyChangeListener
import org.eclipse.jface.util.PropertyChangeEvent
import org.eclipse.ui.IEditorPart
import org.eclipse.ui.IPartListener
import org.eclipse.ui.IWorkbenchPart
import org.eclipse.ui.part.FileEditorInput
import org.scalaide.core.IScalaProject
import org.scalaide.core.IScalaProjectEvent
import org.scalaide.core.ScalaInstallationChange
import org.scalaide.core.BuildSuccess
import org.scalaide.core.IScalaPlugin
import org.scalaide.core.internal.ScalaPlugin.plugin
import org.scalaide.core.internal.compiler.ScalaPresentationCompiler
import org.scalaide.core.internal.compiler.PresentationCompilerProxy
import org.scalaide.core.internal.builder
import org.scalaide.core.internal.builder.EclipseBuildManager
import org.scalaide.core.internal.jdt.model.ScalaSourceFile
import org.scalaide.core.resources.EclipseResource
import org.scalaide.core.resources.MarkerFactory
import org.scalaide.logging.HasLogger
import org.scalaide.ui.internal.actions.PartAdapter
import org.scalaide.ui.internal.preferences.CompilerSettings
import org.scalaide.ui.internal.preferences.IDESettings
import org.scalaide.ui.internal.preferences.PropertyStore
import org.scalaide.ui.internal.preferences.ScalaPluginSettings
import org.scalaide.util.internal.CompilerUtils
import org.scalaide.util.internal.SettingConverterUtil
import org.scalaide.util.Utils.WithAsInstanceOfOpt
import org.scalaide.util.eclipse.SWTUtils.fnToPropertyChangeListener
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jface.preference.IPersistentPreferenceStore
import org.eclipse.core.runtime.CoreException
import org.scalaide.core.SdtConstants
import org.scalaide.util.eclipse.SWTUtils
import org.scalaide.util.eclipse.EclipseUtils
import org.scalaide.util.eclipse.FileUtils
import org.scalaide.core.compiler.IScalaPresentationCompiler
import org.eclipse.jdt.core.WorkingCopyOwner
import org.eclipse.jdt.internal.core.DefaultWorkingCopyOwner
import org.eclipse.jdt.internal.core.SearchableEnvironment
import org.eclipse.jdt.internal.core.JavaProject
import org.scalaide.core.internal.compiler.PresentationCompilerActivityListener
import org.scalaide.ui.internal.editor.ScalaEditor
import java.io.IOException

object ScalaProject {
  def apply(underlying: IProject): ScalaProject = {
    val project = new ScalaProject(underlying)
    project.init()
    project
  }

  /** Listen for [[IWorkbenchPart]] event and takes care of loading/discarding scala compilation units.*/
  private class ProjectPartListener(project: ScalaProject) extends PartAdapter with HasLogger {
    override def partOpened(part: IWorkbenchPart): Unit = {
      doWithCompilerAndFile(part) { (compiler, ssf) =>
        logger.debug("open " + part.getTitle)
        ssf.forceReload()
      }
    }

    override def partClosed(part: IWorkbenchPart): Unit = {
      doWithCompilerAndFile(part) { (compiler, ssf) =>
        logger.debug("close " + part.getTitle)
        ssf.discard()
      }
    }

    private def doWithCompilerAndFile(part: IWorkbenchPart)(op: (IScalaPresentationCompiler, ScalaSourceFile) => Unit): Unit = {
      part match {
        case editor: IEditorPart =>
          editor.getEditorInput match {
            case fei: FileEditorInput =>
              val f = fei.getFile
              if (f.getProject == project.underlying && f.getName.endsWith(SdtConstants.ScalaFileExtn)) {
                for (ssf <- ScalaSourceFile.createFromPath(f.getFullPath.toString)) {
                  if (project.underlying.isOpen)
                    project.presentationCompiler(op(_, ssf))
                }
              }
            case _ =>
          }
        case _ =>
      }
    }
  }

  /**
   * Return true if the given Java project is also a Scala project, false otherwise.
   */
  def isScalaProject(project: IJavaProject): Boolean =
    (project ne null) && isScalaProject(project.getProject)

  /**
   * Return true if the given project is a Scala project, false othrerwise.
   */
  def isScalaProject(project: IProject): Boolean =
    try {
      project != null && project.isOpen && project.hasNature(SdtConstants.NatureId)
    } catch {
      case _:
      CoreException => false
    }

  private def dependenciesForProject(project: IProject): Set[IPath] = {
    def isExportedProject(e: IClasspathEntry) =
      e.isExported && e.getEntryKind == IClasspathEntry.CPE_PROJECT

    val classpath = JavaCore.create(project).getResolvedClasspath(true)
    val exportedProjects = classpath.filter(isExportedProject)
    val exportedPaths = exportedProjects.map(_.getPath).toSet

    exportedPaths
  }

  /**
   * Computes exported project dependencies for set of project.
   */
  @tailrec
  private[project] def exportedDependenciesForProjects(newProjects: Set[IProject], exportedProjects: Set[IProject] = Set.empty): Set[IProject] = {
    val projectsToTest = newProjects diff exportedProjects
    if (projectsToTest.isEmpty)
      exportedProjects
    else {
      exportedDependenciesForProjects(
        projectsToTest.flatMap(dependenciesForProject).map(EclipseUtils.projectFromPath),
        exportedProjects union projectsToTest)
    }
  }
}

class ScalaProject private(val underlying: IProject) extends ClasspathManagement with InstallationManagement with Publisher[IScalaProjectEvent] with HasLogger with IScalaProject {

  private var buildManager0: EclipseBuildManager = null
  private var hasBeenBuilt = false

  private val worbenchPartListener: IPartListener = new ScalaProject.ProjectPartListener(this)

  @deprecated("Don't use or depend on this because it will be removed soon.", since = "4.0.0")
  case class InvalidCompilerSettings() extends RuntimeException(
    "Scala compiler cannot initialize for project: " + underlying.getName +
      ". Please check that your classpath contains the standard Scala library.")

  override val presentationCompiler = new PresentationCompilerProxy(underlying.getName, prepareCompilerSettings _)
  private val watchdog = new PresentationCompilerActivityListener(underlying.getName, ScalaEditor.projectHasOpenEditors(this), presentationCompiler.shutdown _)

  /** To avoid letting 'this' reference escape during initialization, this method is called right after a
   *  [[ScalaPlugin]] instance has been fully initialized.
   */
  private def init(): Unit = {
    presentationCompiler.subscribe(watchdog)

    if (!IScalaPlugin().headlessMode)
      SWTUtils.getWorkbenchWindow map (_.getPartService().addPartListener(worbenchPartListener))
  }

  /** Does this project have the Scala nature? */
  def hasScalaNature: Boolean = ScalaProject.isScalaProject(underlying)

  private def settingsError(severity: Int, msg: String, monitor: IProgressMonitor): Unit = {
    val mrk = underlying.createMarker(SdtConstants.SettingProblemMarkerId)
    mrk.setAttribute(IMarker.SEVERITY, severity)
    mrk.setAttribute(IMarker.MESSAGE, msg)
  }

  /** Deletes the build problem marker associated to {{{this}}} Scala project. */
  private def clearBuildProblemMarker(): Unit =
    if (isUnderlyingValid) {
      underlying.deleteMarkers(SdtConstants.ProblemMarkerId, true, IResource.DEPTH_ZERO)
    }

  /** Deletes all build problem markers for all resources in {{{this}}} Scala project. */
  private def clearAllBuildProblemMarkers(): Unit = {
    if (isUnderlyingValid) {
      underlying.deleteMarkers(SdtConstants.ProblemMarkerId, true, IResource.DEPTH_INFINITE)
    }
  }

  private def clearSettingsErrors(): Unit =
    if (isUnderlyingValid) {
      underlying.deleteMarkers(SdtConstants.SettingProblemMarkerId, true, IResource.DEPTH_ZERO)
    }

  def directDependencies: Seq[IProject] =
    underlying.getReferencedProjects.filter(_.isOpen)

  def transitiveDependencies: Seq[IProject] =
    if (underlying.isOpen)
      ScalaProject.exportedDependenciesForProjects(directDependencies.toSet).toSeq
    else Nil

  def exportedDependencies: Seq[IProject] =
    if (underlying.isOpen)
      ScalaProject.dependenciesForProject(underlying)
        .map(EclipseUtils.projectFromPath).toSeq
    else Nil

  lazy val javaProject: IJavaProject = JavaCore.create(underlying)

  def sourceFolders: Seq[IPath] = {
    for {
      cpe <- resolvedClasspath if cpe.getEntryKind == IClasspathEntry.CPE_SOURCE
      resource <- Option(EclipseUtils.workspaceRoot.findMember(cpe.getPath)) if resource.exists
    } yield resource.getLocation
  }

  def outputFolders: Seq[IPath] =
    sourceOutputFolders.map(_._2.getFullPath()).toSeq

  def outputFolderLocations: Seq[IPath] =
    sourceOutputFolders.map(_._2.getLocation()).toSeq

  def sourceOutputFolders: Seq[(IContainer, IContainer)] = {
    val cpes = resolvedClasspath
    for {
      cpe <- cpes if cpe.getEntryKind == IClasspathEntry.CPE_SOURCE
      source <- Option(EclipseUtils.workspaceRoot.findMember(cpe.getPath)) if source.exists
    } yield {
      val cpeOutput = cpe.getOutputLocation
      val outputLocation = if (cpeOutput != null) cpeOutput else javaProject.getOutputLocation

      val wsroot = EclipseUtils.workspaceRoot
      if (source.getProject.getFullPath == outputLocation)
        (source.asInstanceOf[IContainer], source.asInstanceOf[IContainer])
      else {
        val binPath = wsroot.getFolder(outputLocation)
        (source.asInstanceOf[IContainer], binPath)
      }
    }
  }

  protected def isUnderlyingValid = (underlying.exists() && underlying.isOpen)

  /** This function checks that the underlying project is closed, if not, return the classpath, otherwise return Nil,
   *  so avoids throwing an exceptions.
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

  def allSourceFiles(): Set[IFile] = {
    allFilesInSourceDirs() filter (f => FileUtils.isBuildable(f.getName))
  }

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
      srcFolder = EclipseUtils.workspaceRoot.findMember(srcEntry.getPath())
      if srcFolder ne null
    } {
      val inclusionPatterns = fullPatternChars(srcEntry, srcEntry.getInclusionPatterns())
      val exclusionPatterns = fullPatternChars(srcEntry, srcEntry.getExclusionPatterns())
      val isAlsoProject = srcFolder == underlying // source folder is the project itself

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
                  !sourceOrBinaryFolder(proxy.requestFullPath) // recurse if not on a source or binary folder path
                } else if (exclusionPatterns != null) {
                  if (Util.isExcluded(proxy.requestFullPath, inclusionPatterns, exclusionPatterns, true)) {
                    // must walk children if inclusionPatterns != null, can skip them if == null
                    // but folder is excluded so do not create it in the output folder
                    inclusionPatterns != null
                  } else true
                } else true // recurse into subfolders

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
    val resource = EclipseUtils.workspaceRoot.findMember(outputLocation)
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
      EclipseUtils.workspaceRoot.findContainersForLocationURI(path.toFile.toURI) match {
        case Array(container) => Some(container.getDefaultCharset)
        case _ => None
      }
    }

  protected def shownSettings(settings: Settings, filter: Settings#Setting => Boolean): Seq[(Settings#Setting, String)] = {
    // save the current preferences state, so we don't go through the logic of the workspace
    // or project-specific settings for each setting in turn.
    val currentStorage = storage
    for {
      box <- IDESettings.shownSettings(settings)
      setting <- box.userSettings if filter(setting)
      value = currentStorage.getString(SettingConverterUtil.convertNameToProperty(setting.name))
      if (value.nonEmpty)
    } yield (setting, value)
  }

  def scalacArguments: Seq[String] = {
    import ScalaPresentationCompiler.defaultScalaSettings
    val encArgs = encoding.toSeq flatMap (Seq("-encoding", _))

    val shownArgs = {
      val defaultSettings = defaultScalaSettings()
      setupCompilerClasspath(defaultSettings)
      val userSettings = for ((setting, value) <- shownSettings(defaultSettings, _ => true)) yield {
        initializeSetting(setting, value)
        setting
      }

      val classpathSettings = List(defaultSettings.javabootclasspath, defaultSettings.javaextdirs, defaultSettings.bootclasspath)

      (classpathSettings ++ userSettings) map (_.unparse)
    }
    val extraArgs = defaultScalaSettings().splitParams(storage.getString(CompilerSettings.ADDITIONAL_PARAMS))
    shownArgs.flatten ++ encArgs ++ extraArgs
  }

  private def prepareCompilerSettings(): Settings = {
    val settings = ScalaPresentationCompiler.defaultScalaSettings()
    initializeCompilerSettings(settings, isPCSetting(settings))
    settings
  }

  /** Compiler settings that are honored by the presentation compiler. */
  private def isPCSetting(settings: Settings): Set[Settings#Setting] = {
    import settings.{ plugin => pluginSetting, _ }

    val compilerPluginSettings: Set[Settings#Setting] = Set(pluginOptions,
      pluginSetting,
      pluginsDir)

    val generalSettings: Set[Settings#Setting] = Set(deprecation,
      unchecked,
      verbose,
      Xexperimental,
      future,
      Ylogcp,
      YpresentationDebug,
      YpresentationVerbose,
      YpresentationLog,
      YpresentationReplay,
      YpresentationDelay)

    if (effectiveScalaInstallation().version == ScalaInstallation.platformInstallation.version)
      generalSettings ++ compilerPluginSettings
    else
      generalSettings
  }

  private def initializeSetting(setting: Settings#Setting, propValue: String): Unit = {
    try {
      setting.tryToSetFromPropertyValue(propValue)
      logger.debug("[%s] initializing %s to %s (%s)".format(underlying.getName(), setting.name, setting.value.toString, storage.getString(SettingConverterUtil.convertNameToProperty(setting.name))))
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

    for (enc <- encoding)
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

  private def setupCompilerClasspath(settings: Settings): Unit = {
    val scalaCp = scalaClasspath // don't need to recompute it each time we use it

    settings.javabootclasspath.value = scalaCp.jdkPaths.map(_.toOSString).mkString(pathSeparator)
    // extdirs are already included in Platform JDK paths
    // here we disable Scala's default that would pick up the running JVM extdir, resulting in
    // a mix of classes from the running JVM and configured JDK
    // (we use a space because an empty string is considered as 'value not set by user')
    settings.javaextdirs.value = " "
    settings.classpath.value = (scalaCp.userCp ++ scalaCp.scalaLibrary.toSeq).map(_.toOSString).mkString(pathSeparator)
    scalaCp.scalaLibrary.foreach(scalaLib => settings.bootclasspath.value = scalaLib.toOSString)

    logger.debug("javabootclasspath: " + settings.javabootclasspath.value)
    logger.debug("javaextdirs: " + settings.javaextdirs.value)
    logger.debug("scalabootclasspath: " + settings.bootclasspath.value)
    logger.debug("user classpath: " + settings.classpath.value)
  }

  /** Return a the project-specific preference store. This does not take into account the
   *  user-preference whether to use project-specific compiler settings or not.
   *
   *  @see  #1001241.
   *  @see `storage` for a method that decides based on user preference
   */
  lazy val projectSpecificStorage: IPersistentPreferenceStore = {
    val p = new PropertyStore(new ProjectScope(underlying), SdtConstants.PluginId) {
      override def save(): Unit = {
        try {
          super.save()
        } catch {
          case e: IOException =>
            logger.error(s"An Exception occured saving the project-specific preferences for ${underlying.getName()} ! Your settings will not be persisted. Please report !")
            throw e
        }
      }

    }
    p.addPropertyChangeListener(compilerSettingsListener)
    p
  }

  /** Return the current project preference store.
   *
   *  @see  #1001241.
   */
  def storage: IPreferenceStore = {
    if (usesProjectSettings) projectSpecificStorage else IScalaPlugin().getPreferenceStore()
  }

  @deprecated("This method is not called from anywhere, consider removing in the next release", "4.0.0")
  def isStandardSource(file: IFile, qualifiedName: String): Boolean = {
    val pathString = file.getLocation.toString
    val suffix = qualifiedName.replace(".", "/") + ".scala"
    pathString.endsWith(suffix) && {
      val suffixPath = new Path(suffix)
      val sourceFolderPath = file.getLocation.removeLastSegments(suffixPath.segmentCount)
      sourceFolders.exists(_ == sourceFolderPath)
    }
  }

  @deprecated("Don't use or depend on this because it will be removed soon.", since = "4.0.0")
  def defaultOrElse[T]: T = {
    throw InvalidCompilerSettings()
  }

  @deprecated("Use `presentationCompiler.askRestart()` instead", since = "4.0.0")
  def resetPresentationCompiler(): Boolean = {
    presentationCompiler.askRestart()
    true
  }

  def buildManager: EclipseBuildManager = {
    if (buildManager0 == null) {
      val settings = ScalaPresentationCompiler.defaultScalaSettings(msg => settingsError(IMarker.SEVERITY_ERROR, msg, null))
      clearSettingsErrors()
      initializeCompilerSettings(settings, _ => true)
      // source path should be empty. The build manager decides what files get recompiled when.
      // if scalac finds a source file newer than its corresponding classfile, it will 'compileLate'
      // that file, using an AbstractFile/PlainFile instead of the EclipseResource instance. This later
      // causes problems if errors are reported against that file. Anyway, it's wrong to have a sourcepath
      // when using the build manager.
      settings.sourcepath.value = ""

      logger.info("BM: SBT enhanced Build Manager for " + IScalaPlugin().scalaVersion + " Scala library")

      buildManager0 = {
        val useScopeCompilerProperty = SettingConverterUtil.convertNameToProperty(ScalaPluginSettings.useScopesCompiler.name)
        if (storage.getBoolean(useScopeCompilerProperty))
          new SbtScopesBuildManager(this, settings)
        else new ProjectsDependentSbtBuildManager(this, settings)
      }
    }
    buildManager0
  }

  /* If true, then it means that all source files have to be reloaded */
  def prepareBuild(): Boolean = if (!hasBeenBuilt)
    buildManager.invalidateAfterLoad
  else false

  def build(addedOrUpdated: Set[IFile], removed: Set[IFile], monitor: SubMonitor): Unit = {
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
      publish(BuildSuccess())
    }
  }

  def resetDependentProjects(): Unit = {
    for {
      prj <- underlying.getReferencingProjects()
      if prj.isOpen() && ScalaProject.isScalaProject(prj)
      dependentScalaProject <- IScalaPlugin().asScalaProject(prj)
    } {
      logger.debug("[%s] Reset PC of referring project %s".format(this, dependentScalaProject))
      dependentScalaProject.presentationCompiler.askRestart()
    }
  }

  def clean(implicit monitor: IProgressMonitor) = {
    clearAllBuildProblemMarkers()
    resetClasspathCheck()

    if (buildManager != null)
      buildManager.clean(monitor)
    cleanOutputFolders
    logger.info("Resetting compilers due to Project.clean")
    resetCompilers // reset them only after the output directory is emptied
  }

  private def resetBuildCompiler(): Unit = {
    buildManager0 = null
    hasBeenBuilt = false
  }

  protected def resetCompilers(implicit monitor: IProgressMonitor = null) = {
    logger.info("resetting compilers!  project: " + this.toString)
    resetBuildCompiler()
    presentationCompiler.askRestart()
  }

  /** Should only be called when `this` project is being deleted or closed from the workspace. */
  private[core] def dispose(): Unit = {
    def shutDownCompilers(): Unit = {
      logger.info("shutting down compilers for " + this)
      resetBuildCompiler()
      presentationCompiler.shutdown()
    }

    if (!IScalaPlugin().headlessMode)
      SWTUtils.getWorkbenchWindow map (_.getPartService().removePartListener(worbenchPartListener))
    projectSpecificStorage.removePropertyChangeListener(compilerSettingsListener)
    shutDownCompilers()
  }

  override def newSearchableEnvironment(workingCopyOwner: WorkingCopyOwner = DefaultWorkingCopyOwner.PRIMARY): SearchableEnvironment = {
    val jProject = javaProject.asInstanceOf[JavaProject]
    jProject.newSearchableNameEnvironment(workingCopyOwner)
  }

  override def toString: String = underlying.getName

  override def equals(other: Any): Boolean = other match {
    case otherSP: IScalaProject => underlying == otherSP.underlying
    case _ => false
  }

  override def hashCode(): Int = underlying.hashCode()
}

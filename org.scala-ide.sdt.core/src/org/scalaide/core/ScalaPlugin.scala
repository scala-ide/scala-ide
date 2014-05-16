package org.scalaide.core

import org.eclipse.jdt.core.IJavaProject
import scala.collection.mutable
import scala.util.control.ControlThrowable
import org.eclipse.core.resources.IFile
import org.eclipse.core.resources.IProject
import org.eclipse.core.resources.IResourceChangeEvent
import org.eclipse.core.resources.IResourceChangeListener
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.runtime.CoreException
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.Platform
import org.eclipse.core.runtime.Status
import org.eclipse.core.runtime.content.IContentTypeSettings
import org.eclipse.jdt.core.ElementChangedEvent
import org.eclipse.jdt.core.IElementChangedListener
import org.eclipse.jdt.core.JavaCore
import org.eclipse.jdt.core.IJavaElement
import org.eclipse.jdt.core.IJavaElementDelta
import org.eclipse.jdt.core.IPackageFragmentRoot
import org.eclipse.jdt.internal.core.JavaModel
import org.eclipse.jdt.internal.core.JavaProject
import org.eclipse.jdt.internal.core.PackageFragment
import org.eclipse.jdt.internal.core.PackageFragmentRoot
import org.eclipse.jdt.internal.core.util.Util
import org.eclipse.jdt.internal.ui.javaeditor.IClassFileEditorInput
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.swt.widgets.Shell
import org.eclipse.swt.graphics.Color
import org.eclipse.ui.IEditorInput
import org.eclipse.ui.IFileEditorInput
import org.eclipse.ui.PlatformUI
import org.eclipse.ui.IPartListener
import org.eclipse.ui.IWorkbenchPart
import org.eclipse.ui.IWorkbenchPage
import org.eclipse.ui.IPageListener
import org.eclipse.ui.IEditorPart
import org.eclipse.ui.part.FileEditorInput
import org.eclipse.ui.plugin.AbstractUIPlugin
import org.osgi.framework.BundleContext
import org.scalaide.core.internal.jdt.model.ScalaSourceFile
import org.scalaide.util.internal.eclipse.OSGiUtils
import org.scalaide.ui.internal.templates.ScalaTemplateManager
import org.eclipse.jdt.ui.PreferenceConstants
import org.eclipse.core.resources.IResourceDelta
import org.scalaide.logging.HasLogger
import org.osgi.framework.Bundle
import org.scalaide.util.internal.Utils
import org.eclipse.jdt.core.ICompilationUnit
import scala.tools.nsc.io.AbstractFile
import scala.tools.nsc.settings.ScalaVersion
import scala.tools.nsc.settings.SpecificScalaVersion
import org.scalaide.core.resources.EclipseResource
import org.scalaide.logging.PluginLogConfigurator
import scala.tools.nsc.Settings
import org.scalaide.core.internal.project.ScalaProject
import org.scalaide.ui.internal.diagnostic
import org.scalaide.util.internal.CompilerUtils
import org.eclipse.core.runtime.IPath
import java.io.File
import org.scalaide.core.internal.builder.zinc.CompilerInterfaceStore
import org.scalaide.util.internal.FixedSizeCache
import org.scalaide.core.internal.project.ScalaInstallation

object ScalaPlugin {
  final val IssueTracker = "https://www.assembla.com/spaces/scala-ide/support/tickets"

  private final val HeadlessTest = "sdtcore.headless"
  private final val NoTimeouts = "sdtcore.notimeouts"

  @volatile var plugin: ScalaPlugin = _

  def prefStore = plugin.getPreferenceStore

  def getWorkbenchWindow = {
    val workbench = PlatformUI.getWorkbench
    Option(workbench.getActiveWorkbenchWindow) orElse workbench.getWorkbenchWindows.headOption
  }

  def getShell: Shell = getWorkbenchWindow.map(_.getShell).orNull

  def defaultScalaSettings(errorFn: String => Unit = Console.println): Settings = new Settings(errorFn)
}

class ScalaPlugin extends AbstractUIPlugin with PluginLogConfigurator with IResourceChangeListener with IElementChangedListener with HasLogger {
  import CompilerUtils.{ ShortScalaVersion, isBinaryPrevious, isBinarySame }

  def pluginId = "org.scala-ide.sdt.core"
  def compilerPluginId = "org.scala-lang.scala-compiler"
  def libraryPluginId = "org.scala-lang.scala-library"
  def actorsPluginId = "org.scala-lang.scala-actors"
  def reflectPluginId = "org.scala-lang.scala-reflect"
  def swingPluginId = "org.scala-lang.modules.scala-swing"
  def sbtPluginId = "org.scala-ide.sbt.full.library"
  lazy val sbtCompilerInterfaceId = {
    val ShortScalaVersion(major, minor) = scalaVer
    s"org.scala-ide.sbt.compiler$major$minor.interface"
  }

  def wizardPath = pluginId + ".wizards"
  def wizardId(name: String) = wizardPath + ".new" + name
  def classWizId = wizardId("Class")
  def traitWizId = wizardId("Trait")
  def objectWizId = wizardId("Object")
  def packageObjectWizId = wizardId("PackageObject")
  def applicationWizId = wizardId("Application")
  def projectWizId = wizardId("Project")
  def netProjectWizId = wizardId("NetProject")

  def editorId = "scala.tools.eclipse.ScalaSourceFileEditor"
  def builderId = pluginId + ".scalabuilder"
  def natureId = pluginId + ".scalanature"
  def launchId = "org.scala-ide.sdt.launching"
  val scalaCompiler = "SCALA_COMPILER_CONTAINER"
  val scalaLib = "SCALA_CONTAINER"
  def scalaCompilerId = launchId + "." + scalaCompiler
  def scalaLibId = launchId + "." + scalaLib
  def launchTypeId = "scala.application"
  def problemMarkerId = pluginId + ".problem"
  def classpathProblemMarkerId = pluginId + ".classpathProblem"
  def settingProblemMarkerId = pluginId + ".settingProblem"
  def taskMarkerId = pluginId + ".task"

  /** All Scala error markers. */
  val scalaErrorMarkers = Set(classpathProblemMarkerId, problemMarkerId, settingProblemMarkerId)

  val scalaFileExtn = ".scala"
  val javaFileExtn = ".java"
  val jarFileExtn = ".jar"

   /** Check if the given version is compatible with the current plug-in version.
   *  Check on the major/minor number, discard the maintenance number.
   *
   *  For example 2.9.1 and 2.9.2-SNAPSHOT are compatible versions whereas
   *  2.8.1 and 2.9.0 aren't.
   */
  def isCompatibleVersion(version: ScalaVersion, project: ScalaProject): Boolean = {
    if (project.isUsingCompatibilityMode())
      isBinaryPrevious(ScalaVersion.current, version)
    else
      isBinarySame(ScalaVersion.current, version)// don't treat 2 unknown versions as equal
  }

  lazy val scalaVer = ScalaVersion.current
  lazy val shortScalaVer = scalaVer match {
    case ShortScalaVersion(major, minor) => f"$major%d.$minor%2d"
    case _ => "none"
  }

  lazy val sdtCoreBundle = getBundle()
  lazy val scalaCompilerBundle = Platform.getBundle(compilerPluginId)
  lazy val scalaCompilerBundleVersion = scalaCompilerBundle.getVersion()
  lazy val compilerClasses = OSGiUtils.getBundlePath(scalaCompilerBundle)
  lazy val compilerSources = OSGiUtils.pathInBundle(sdtCoreBundle, "/target/src/scala-compiler-src.jar")

  lazy val sbtCompilerBundle = Platform.getBundle(sbtPluginId)
  lazy val sbtCompilerInterfaceBundle = Platform.getBundle(sbtCompilerInterfaceId)
  lazy val sbtCompilerInterface = OSGiUtils.pathInBundle(sbtCompilerInterfaceBundle, "/")
  // Disable for now, until we introduce a way to have multiple scala libraries, compilers available for the builder
  //lazy val sbtScalaLib = pathInBundle(sbtCompilerBundle, "/lib/scala-" + shortScalaVer + "/lib/scala-library.jar")
  //lazy val sbtScalaCompiler = pathInBundle(sbtCompilerBundle, "/lib/scala-" + shortScalaVer + "/lib/scala-compiler.jar")

  lazy val scalaLibBundle = {
    // all library bundles
    val bundles = Option(Platform.getBundles(libraryPluginId, null)).getOrElse(Array[Bundle]())
    logger.debug("[scalaLibBundle] Found %d bundles: %s".format(bundles.size, bundles.toList.mkString(", ")))
    bundles.find(b => b.getVersion().getMajor() == scalaCompilerBundleVersion.getMajor() && b.getVersion().getMinor() == scalaCompilerBundleVersion.getMinor()).getOrElse {
      eclipseLog.error("Could not find a match for %s in %s. Using default.".format(scalaCompilerBundleVersion, bundles.toList.mkString(", ")), null)
      Platform.getBundle(libraryPluginId)
    }
  }

  // TODO: add doc
  def computeSourcePath(bundleId: String, bundlePath: IPath): Option[IPath] = {


    val jarFile = bundlePath.lastSegment()
    val parentFolder = bundlePath.removeLastSegments(1)

    val sourceBundleId = bundleId + ".source"
    val sourceJarFile = jarFile.replace(bundleId, sourceBundleId)

    val installedLocation = parentFolder.append(sourceJarFile)

    val versionString = parentFolder.lastSegment()
    val groupFolder = parentFolder.removeLastSegments(2)

    val buildLocation = groupFolder.append(sourceBundleId).append(versionString).append(sourceJarFile)

    if (installedLocation.toFile().exists()) {
      Some(installedLocation)
    } else {
      val versionString = parentFolder.lastSegment()
      val groupFolder = parentFolder.removeLastSegments(2)

      val buildLocation = groupFolder.append(sourceBundleId).append(versionString).append(sourceJarFile)
      if (buildLocation.toFile().exists()) {
        Some(buildLocation)
      } else {
        None
      }
    }

  }

  lazy val libClasses = OSGiUtils.getBundlePath(scalaLibBundle)
  lazy val libSources = libClasses.flatMap(l => computeSourcePath(libraryPluginId, l))
  //  lazy val libSources = OSGiUtils.pathInBundle(sdtCoreBundle, "/target/src/scala-library-src.jar")

  // 2.10 specific libraries
  lazy val scalaActorsBundle = Platform.getBundle(actorsPluginId)
  lazy val actorsClasses = OSGiUtils.getBundlePath(scalaActorsBundle)
  lazy val actorsSources = actorsClasses.flatMap(l => computeSourcePath(actorsPluginId, l))
  //  lazy val actorsSources = OSGiUtils.pathInBundle(sdtCoreBundle, "/target/src/scala-actors-src.jar")

  lazy val scalaReflectBundle = Platform.getBundle(reflectPluginId)
  lazy val reflectClasses = OSGiUtils.getBundlePath(scalaReflectBundle)
  lazy val reflectSources = reflectClasses.flatMap(l => computeSourcePath(reflectPluginId, l))
  //  lazy val reflectSources = OSGiUtils.pathInBundle(sdtCoreBundle, "/target/src/scala-reflect-src.jar")

  // TODO: 2.10 swing support
  lazy val swingClasses = OSGiUtils.getBundlePath(Platform.getBundle(swingPluginId))
  lazy val swingSources = swingClasses.flatMap(l => computeSourcePath(swingPluginId, l))

  lazy val templateManager = new ScalaTemplateManager()
  lazy val headlessMode = System.getProperty(ScalaPlugin.HeadlessTest) ne null
  lazy val noTimeoutMode = System.getProperty(ScalaPlugin.NoTimeouts) ne null

  private val projects = new mutable.HashMap[IProject, ScalaProject]

  override def start(context: BundleContext) = {
    ScalaPlugin.plugin = this
    super.start(context)

    if (!headlessMode) {
      PlatformUI.getWorkbench.getEditorRegistry.setDefaultEditor("*.scala", editorId)
      diagnostic.StartupDiagnostics.run
    }
    ResourcesPlugin.getWorkspace.addResourceChangeListener(this, IResourceChangeEvent.PRE_CLOSE)
    JavaCore.addElementChangedListener(this)
    logger.info("Scala compiler bundle: " + scalaCompilerBundle.getLocation)
  }

  override def stop(context: BundleContext) = {
    ResourcesPlugin.getWorkspace.removeResourceChangeListener(this)
    super.stop(context)
    ScalaPlugin.plugin = null
  }

  /** The compiler-interface store, located in this plugin configuration area (usually inside the metadata directory */
  lazy val compilerInterfaceStore: CompilerInterfaceStore = new CompilerInterfaceStore(Platform.getStateLocation(sdtCoreBundle), this)

  /** A LRU cache of class loaders for Scala builders */
  lazy val classLoaderStore: FixedSizeCache[ScalaInstallation,ClassLoader] = new FixedSizeCache(initSize = 2, maxSize = 3)

  def workspaceRoot = ResourcesPlugin.getWorkspace.getRoot

  def getJavaProject(project: IProject) = JavaCore.create(project)

  def getScalaProject(project: IProject): ScalaProject = projects.synchronized {
    projects.get(project) getOrElse {
        val scalaProject = ScalaProject(project)
        projects(project) = scalaProject
        scalaProject
    }
  }

  /** Restart all presentation compilers in the workspace. Need to do it in order
   *  for them to pick up the new std out/err streams.
   */
  def resetAllPresentationCompilers() {
    for {
      iProject <- ResourcesPlugin.getWorkspace.getRoot.getProjects
      if iProject.isOpen
      scalaProject <- asScalaProject(iProject)
    } scalaProject.presentationCompiler.askRestart()
  }

  /** Return Some(ScalaProject) if the project has the Scala nature, None otherwise.
   */
  def asScalaProject(project: IProject): Option[ScalaProject] = {
    if (isScalaProject(project)) {
      Some(getScalaProject(project))
    } else {
      logger.debug("`%s` is not a Scala Project.".format(project.getName()))
      None
    }
  }

  def isScalaProject(project: IJavaProject): Boolean =
    (project ne null) && isScalaProject(project.getProject)

  def isScalaProject(project: IProject): Boolean =
    try {
      project != null && project.isOpen && project.hasNature(natureId)
    } catch {
      case _: CoreException => false
    }

  override def resourceChanged(event: IResourceChangeEvent) {
    (event.getResource, event.getType) match {
      case (project: IProject, IResourceChangeEvent.PRE_CLOSE) =>
        disposeProject(project)
      case _ =>
    }
  }

  private def disposeProject(project: IProject): Unit = {
    projects.synchronized {
      projects.get(project) foreach { (scalaProject) =>
          projects.remove(project)
          scalaProject.dispose()
      }
    }
  }

  override def elementChanged(event: ElementChangedEvent) {
    import scala.collection.mutable.ListBuffer
    import IJavaElement._
    import IJavaElementDelta._

    // check if the changes are linked with the build path
    val modelDelta = event.getDelta()

    // check that the notification is about a change (CHANGE) of some elements (F_CHILDREN) of the java model (JAVA_MODEL)
    if (modelDelta.getElement().getElementType() == JAVA_MODEL && modelDelta.getKind() == CHANGED && (modelDelta.getFlags() & F_CHILDREN) != 0) {
      for (innerDelta <- modelDelta.getAffectedChildren()) {
        // check that the notification no the child is about a change (CHANDED) relative to a resolved classpath change (F_RESOLVED_CLASSPATH_CHANGED)
        if (innerDelta.getKind() == CHANGED && (innerDelta.getFlags() & IJavaElementDelta.F_RESOLVED_CLASSPATH_CHANGED) != 0) {
          innerDelta.getElement() match {
            // classpath change should only impact projects
            case javaProject: IJavaProject => {
              asScalaProject(javaProject.getProject()).foreach(_.classpathHasChanged())
            }
            case _ =>
          }
        }
      }
    }

    // process deleted files
    val buff = new ListBuffer[ScalaSourceFile]
    val changed = new ListBuffer[ICompilationUnit]
    val projectsToReset = new mutable.HashSet[ScalaProject]

    def findRemovedSources(delta: IJavaElementDelta) {
      val isChanged = delta.getKind == CHANGED
      val isRemoved = delta.getKind == REMOVED
      val isAdded = delta.getKind == ADDED

      def hasFlag(flag: Int) = (delta.getFlags & flag) != 0

      val elem = delta.getElement

      val processChildren: Boolean = elem.getElementType match {
        case JAVA_MODEL =>
          true

        case JAVA_PROJECT if isRemoved =>
          disposeProject(elem.getJavaProject.getProject)
          false

        case JAVA_PROJECT if !hasFlag(F_CLOSED) =>
          true

        case PACKAGE_FRAGMENT_ROOT =>
          val hasContentChanged = isRemoved || hasFlag(F_REMOVED_FROM_CLASSPATH | F_ADDED_TO_CLASSPATH | F_ARCHIVE_CONTENT_CHANGED)
          if (hasContentChanged) {
            logger.info("package fragment root changed (resetting pres compiler): " + elem.getElementName())
            asScalaProject(elem.getJavaProject().getProject).foreach(projectsToReset += _)
          }
          !hasContentChanged

        case PACKAGE_FRAGMENT =>
          val hasContentChanged = isAdded || isRemoved
          if (hasContentChanged) {
            logger.debug("package framgent added or removed" + elem.getElementName())
            asScalaProject(elem.getJavaProject().getProject).foreach(projectsToReset += _)
          }
          // stop recursion here, we need to reset the PC anyway
          !hasContentChanged

        // TODO: the check should be done with isInstanceOf[ScalaSourceFile] instead of
        // endsWith(scalaFileExtn), but it is not working for Play 2.0 because of #1000434
        case COMPILATION_UNIT if isChanged && elem.getResource.getName.endsWith(scalaFileExtn) =>
          val hasContentChanged = hasFlag(IJavaElementDelta.F_CONTENT)
          if (hasContentChanged)
            // mark the changed Scala files to be refreshed in the presentation compiler if needed
            changed += elem.asInstanceOf[ICompilationUnit]
          false

        case COMPILATION_UNIT if elem.isInstanceOf[ScalaSourceFile] && isRemoved =>
          buff += elem.asInstanceOf[ScalaSourceFile]
          false

        case COMPILATION_UNIT if isAdded =>
          logger.debug("added compilation unit " + elem.getElementName())
          asScalaProject(elem.getJavaProject().getProject).foreach(projectsToReset += _)
          false

        case _ =>
          false
      }

      if (processChildren)
        delta.getAffectedChildren foreach findRemovedSources
    }
    findRemovedSources(event.getDelta)

    // ask for the changed scala files to be refreshed in each project presentation compiler if needed
    if (changed.nonEmpty) {
      changed.toList groupBy (_.getJavaProject.getProject) foreach {
        case (project, units) =>
          asScalaProject(project) foreach { p =>
            if (project.isOpen && !projectsToReset(p)) {
              p.presentationCompiler(_.refreshChangedFiles(units.map(_.getResource.asInstanceOf[IFile])))
            }
          }
      }
    }

    projectsToReset.foreach(_.presentationCompiler.askRestart())
    if (buff.nonEmpty) {
      buff.toList groupBy (_.getJavaProject.getProject) foreach {
        case (project, srcs) =>
          asScalaProject(project) foreach { p =>
            if (project.isOpen && !projectsToReset(p))
              p presentationCompiler (_.filesDeleted(srcs))
          }
      }
    }
  }

  /** Is the file buildable by the Scala plugin? In other words, is it a
   *  Java or Scala source file?
   *
   *  @note If you don't have an IFile yet, prefer the String overload, as
   *        creating an IFile is usually expensive
   */
  def isBuildable(file: IFile): Boolean =
    isBuildable(file.getName())

  /** Is the file buildable by the Scala plugin? In other words, is it a
   *  Java or Scala source file?
   */
  def isBuildable(fileName: String): Boolean =
    (fileName.endsWith(scalaFileExtn) || fileName.endsWith(javaFileExtn))
}

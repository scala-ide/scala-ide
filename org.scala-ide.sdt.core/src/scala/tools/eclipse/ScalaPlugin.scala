/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

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
import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.eclipse.util.OSGiUtils
import scala.tools.eclipse.templates.ScalaTemplateManager
import org.eclipse.jdt.ui.PreferenceConstants
import org.eclipse.core.resources.IResourceDelta
import scala.tools.eclipse.logging.HasLogger
import org.osgi.framework.Bundle
import scala.tools.eclipse.util.Utils
import org.eclipse.jdt.core.ICompilationUnit
import scala.tools.nsc.io.AbstractFile
import scala.tools.eclipse.util.EclipseResource
import scala.tools.eclipse.logging.PluginLogConfigurator
import scala.tools.eclipse.util.Trim
import scala.tools.nsc.Settings

object ScalaPlugin {
  final val IssueTracker = "https://www.assembla.com/spaces/scala-ide/support/tickets"

  private final val HeadlessTest  = "sdtcore.headless"
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
  def pluginId = "org.scala-ide.sdt.core"
  def compilerPluginId = "org.scala-lang.scala-compiler"
  def libraryPluginId = "org.scala-lang.scala-library"
  def actorsPluginId = "org.scala-lang.scala-actors"
  def reflectPluginId = "org.scala-lang.scala-reflect"
  def sbtPluginId = "org.scala-ide.sbt.full.library"
  def sbtCompilerInterfaceId = "org.scala-ide.sbt.compiler.interface"

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

  private def cutVersion(version: String): String = {
    val pattern = "(\\d)\\.(\\d+)\\..*".r
    version match {
      case pattern(major, minor) =>
        major + "." + minor
      case _ =>
        "(unknown)"
    }
  }

  /**
   * Check if the given version is compatible with the current plug-in version.
   * Check on the major/minor number, discard the maintenance number.
   *
   * For example 2.9.1 and 2.9.2-SNAPSHOT are compatible versions whereas
   * 2.8.1 and 2.9.0 aren't.
   */
  def isCompatibleVersion(version: Option[String]): Boolean = version match {
    case Some(v) =>
      cutVersion(v) == shortScalaVer
    case None =>
      false
  }

  lazy val scalaVer = scala.util.Properties.scalaPropOrElse("version.number", "(unknown)")
  lazy val shortScalaVer = cutVersion(scalaVer)

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

  lazy val libClasses = OSGiUtils.getBundlePath(scalaLibBundle)
  lazy val libSources = OSGiUtils.pathInBundle(sdtCoreBundle, "/target/src/scala-library-src.jar")

  // 2.10 specific libraries
  lazy val scalaActorsBundle = Platform.getBundle(actorsPluginId)
  lazy val actorsClasses = OSGiUtils.getBundlePath(Platform.getBundle(actorsPluginId))
  lazy val actorsSources = OSGiUtils.pathInBundle(sdtCoreBundle, "/target/src/scala-actors-src.jar")

  lazy val scalaReflectBundle = Platform.getBundle(reflectPluginId)
  lazy val reflectClasses = OSGiUtils.getBundlePath(Platform.getBundle(reflectPluginId))
  lazy val reflectSources = OSGiUtils.pathInBundle(sdtCoreBundle, "/target/src/scala-reflect-src.jar")

  lazy val swingClasses = OSGiUtils.pathInBundle(sdtCoreBundle, "/target/lib/scala-swing.jar")
  lazy val swingSources = OSGiUtils.pathInBundle(sdtCoreBundle, "/target/src/scala-swing-src.jar")

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

  def workspaceRoot = ResourcesPlugin.getWorkspace.getRoot

  def getJavaProject(project: IProject) = JavaCore.create(project)

  def getScalaProject(project: IProject): ScalaProject = projects.synchronized {
    projects.get(project) match {
      case Some(scalaProject) => scalaProject
      case None =>
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

  /**
   * Return Some(ScalaProject) if the project has the Scala nature, None otherwise.
   */
  def asScalaProject(project: IProject): Option[ScalaProject]= {
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
      projects.get(project) match {
        case Some(scalaProject) =>
          projects.remove(project)
          scalaProject.dispose()
        case None =>
      }
    }
  }

  override def elementChanged(event: ElementChangedEvent) {
    import scala.collection.mutable.ListBuffer
    import IJavaElement._
    import IJavaElementDelta._

    // check if the changes are linked with the build path
    val modelDelta= event.getDelta()

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
      val isAdded   = delta.getKind == ADDED

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
          if(hasContentChanged)
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
      changed.toList groupBy(_.getJavaProject.getProject) foreach {
        case (project, units) =>
          asScalaProject(project) foreach { p =>
            if (project.isOpen && !projectsToReset(p)) {
              p.presentationCompiler(_.refreshChangedFiles(units.map(_.getResource.asInstanceOf[IFile])))
            }
          }
      }
    }

    projectsToReset.foreach(_.presentationCompiler.askRestart())
    if(buff.nonEmpty) {
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

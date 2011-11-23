/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import org.eclipse.jdt.core.IJavaProject
import scala.collection.mutable
import scala.util.control.ControlThrowable
import org.eclipse.core.resources.{ IFile, IProject, IResourceChangeEvent, IResourceChangeListener, ResourcesPlugin }
import org.eclipse.core.runtime.{ CoreException, FileLocator, IStatus, Platform, Status }
import org.eclipse.core.runtime.content.IContentTypeSettings
import org.eclipse.jdt.core.{ ElementChangedEvent, IElementChangedListener, JavaCore, IJavaElement, IJavaElementDelta, IPackageFragmentRoot }
import org.eclipse.jdt.internal.core.{ JavaModel, JavaProject, PackageFragment, PackageFragmentRoot }
import org.eclipse.jdt.internal.core.util.Util
import org.eclipse.jdt.internal.ui.javaeditor.IClassFileEditorInput
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.swt.widgets.Shell
import org.eclipse.swt.graphics.Color
import org.eclipse.ui.{ IEditorInput, IFileEditorInput, PlatformUI, IPartListener, IWorkbenchPart, IWorkbenchPage, IPageListener, IEditorPart }
import org.eclipse.ui.part.FileEditorInput
import org.eclipse.ui.plugin.AbstractUIPlugin
import util.SWTUtils.asyncExec
import org.osgi.framework.BundleContext
import scala.tools.eclipse.javaelements.{ ScalaElement, ScalaSourceFile }
import scala.tools.eclipse.util.OSGiUtils.pathInBundle
import scala.tools.eclipse.templates.ScalaTemplateManager
import org.eclipse.jdt.ui.PreferenceConstants
import org.eclipse.core.resources.IResourceDelta
import scala.tools.eclipse.util.HasLogger
import org.osgi.framework.Bundle
import scala.tools.eclipse.util.Utils
import org.eclipse.jdt.core.ICompilationUnit
import scala.tools.nsc.io.AbstractFile
import scala.tools.eclipse.util.EclipseResource

object ScalaPlugin {
  var plugin: ScalaPlugin = _
  
  def getWorkbenchWindow = {
    val workbench = PlatformUI.getWorkbench
    Option(workbench.getActiveWorkbenchWindow) orElse workbench.getWorkbenchWindows.headOption
  }
  
  def getShell: Shell = getWorkbenchWindow map (_.getShell) orNull
}

class ScalaPlugin extends AbstractUIPlugin with IResourceChangeListener with IElementChangedListener with IPartListener with HasLogger {
  ScalaPlugin.plugin = this

  final val HEADLESS_TEST  = "sdtcore.headless"
  
  def pluginId = "org.scala-ide.sdt.core"
  def compilerPluginId = "org.scala-ide.scala.compiler"
  def libraryPluginId = "org.scala-ide.scala.library"
  def sbtPluginId = "org.scala-ide.sbt.full.library"

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

  // Retained for backwards compatibility
  val oldPluginId = "ch.epfl.lamp.sdt.core"
  val oldLibraryPluginId = "scala.library"
  val oldNatureId = oldPluginId + ".scalanature"
  val oldBuilderId = oldPluginId + ".scalabuilder"
  val oldLaunchId = "ch.epfl.lamp.sdt.launching"
  val oldScalaLibId = oldLaunchId + "." + scalaLib

  val scalaFileExtn = ".scala"
  val javaFileExtn = ".java"
  val jarFileExtn = ".jar"

  private def cutVersion(version: String): String = {
          val pattern = "(\\d)\\.(\\d+)\\..*".r
          version match {
            case pattern(major, minor)=>
              major + "." + minor
            case _ =>
              "(unknown)"
          }
      }
  
  /**
   * Check if the given version is compatible with the current plug-in version.
   * Check on the major/minor number, discard the maintenance number.
   * 2.9.1 and 2.9.2-SNAPSHOT are compatible
   * 2.8.1 and 2.9.0 are no compatible
   */
  def isCompatibleVersion(version: Option[String]): Boolean =
    version match {
    case Some(v) =>
      cutVersion(v) == shortScalaVer
    case None =>
      false
  }

  lazy val scalaVer = scala.util.Properties.scalaPropOrElse("version.number", "(unknown)")
  lazy val shortScalaVer = cutVersion(scalaVer)

  val scalaCompilerBundle = Platform.getBundle(ScalaPlugin.plugin.compilerPluginId)
  val scalaCompilerBundleVersion = scalaCompilerBundle.getVersion()
  val compilerClasses = pathInBundle(scalaCompilerBundle, "/lib/scala-compiler.jar")
  val continuationsClasses = pathInBundle(scalaCompilerBundle, "/lib/continuations.jar")
  val compilerSources = pathInBundle(scalaCompilerBundle, "/lib/scala-compiler-src.jar")
  
  lazy val sbtCompilerBundle = Platform.getBundle(ScalaPlugin.plugin.sbtPluginId)
  lazy val sbtCompilerInterface = pathInBundle(sbtCompilerBundle, "/lib/scala-" + shortScalaVer + "/lib/compiler-interface.jar")
  // Disable for now, until we introduce a way to have multiple scala libraries, compilers available for the builder
  //lazy val sbtScalaLib = pathInBundle(sbtCompilerBundle, "/lib/scala-" + shortScalaVer + "/lib/scala-library.jar")
  //lazy val sbtScalaCompiler = pathInBundle(sbtCompilerBundle, "/lib/scala-" + shortScalaVer + "/lib/scala-compiler.jar")
  
  val scalaLibBundle = {
    val bundles = Option(Platform.getBundles(ScalaPlugin.plugin.libraryPluginId, scalaCompilerBundleVersion.toString())).getOrElse(Array[Bundle]())
    logger.debug("[scalaLibBundle] Found %d bundles: %s".format(bundles.size, bundles.toList.mkString(", ")))
    bundles.find(_.getVersion() == scalaCompilerBundleVersion).getOrElse {
      logger.warning("Couldnt find a match for %s in %s. Using default.".format(scalaCompilerBundleVersion, bundles.toList.mkString(", ")))
      Platform.getBundle(ScalaPlugin.plugin.libraryPluginId)
    }
  }
  
  val libClasses = pathInBundle(scalaLibBundle, "/lib/scala-library.jar")
  val libSources = pathInBundle(scalaLibBundle, "/lib/scala-library-src.jar")
  val dbcClasses = pathInBundle(scalaLibBundle, "/lib/scala-dbc.jar")
  val dbcSources = pathInBundle(scalaLibBundle, "/lib/scala-dbc-src.jar")
  val swingClasses = pathInBundle(scalaLibBundle, "/lib/scala-swing.jar")
  val swingSources = pathInBundle(scalaLibBundle, "/lib/scala-swing-src.jar")

  lazy val templateManager = new ScalaTemplateManager()
  lazy val headlessMode = System.getProperty(HEADLESS_TEST) ne null

  private val projects = new mutable.HashMap[IProject, ScalaProject]

  override def start(context: BundleContext) = {
    super.start(context)

    if (!headlessMode) {
      PlatformUI.getWorkbench.getEditorRegistry.setDefaultEditor("*.scala", editorId)
      ScalaPlugin.getWorkbenchWindow map (_.getPartService().addPartListener(ScalaPlugin.this))
      diagnostic.StartupDiagnostics.run
    }
    ResourcesPlugin.getWorkspace.addResourceChangeListener(this, IResourceChangeEvent.PRE_CLOSE)
    JavaCore.addElementChangedListener(this)
    logger.info("Scala compiler bundle: " + scalaCompilerBundle.getLocation)
  }

  override def stop(context: BundleContext) = {
    ResourcesPlugin.getWorkspace.removeResourceChangeListener(this)
    super.stop(context)
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
      project != null && project.isOpen && (project.hasNature(natureId) || project.hasNature(oldNatureId))
    } catch {
      case _: CoreException => false
    }

  override def resourceChanged(event: IResourceChangeEvent) {
    (event.getResource, event.getType) match {
      case (project: IProject, IResourceChangeEvent.PRE_CLOSE) =>
        projects.synchronized {
          projects.get(project) match {
            case Some(scalaProject) =>
              projects.remove(project)
              logger.info("shutting down compilers for " + project.getName)
              scalaProject.shutDownCompilers()
            case None =>
          }
        }
      case _ =>
    }
  }
  
  override def elementChanged(event: ElementChangedEvent) {
    import scala.collection.mutable.ListBuffer
    import IJavaElement._
    import IJavaElementDelta._

    // check if the changes are linked with the build path
    val modelDelta= event.getDelta()
    
    // check that the notification is about a change (CHANGE) of some elements (F_CHILDREN) of the java model (JAVA_MODEL)
    if (JAVA_MODEL == modelDelta.getElement().getElementType() && modelDelta.getKind() == CHANGED && (modelDelta.getFlags() & F_CHILDREN) != 0) {
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
        case JAVA_MODEL => true
        case JAVA_PROJECT if !isRemoved && !hasFlag(F_CLOSED) => true

        case PACKAGE_FRAGMENT_ROOT =>
          if (isRemoved || hasFlag(F_REMOVED_FROM_CLASSPATH | F_ADDED_TO_CLASSPATH | F_ARCHIVE_CONTENT_CHANGED)) {
            logger.info("package fragment root changed (resetting pres compiler): " + elem)
            asScalaProject(elem.getJavaProject().getProject).foreach(projectsToReset +=)
            false
          } else true

        case PACKAGE_FRAGMENT => 
          if (isAdded || isRemoved) {
            logger.debug("package framgent added or removed" + elem.getElementName())
            asScalaProject(elem.getJavaProject().getProject).foreach(projectsToReset +=)
            false // stop recursion, we need to reset the PC anyway
          } else 
            true

        // TODO: the check should be done with isInstanceOf[ScalaSourceFile] instead of
        // endsWith(scalaFileExtn), but it is not working for Play 2.0 because of #1000434
        case COMPILATION_UNIT if isChanged && elem.getResource.getName.endsWith(scalaFileExtn) =>
          // marked the changed scala files to be refreshed in the presentation compiler if needed
          changed += elem.asInstanceOf[ICompilationUnit]
          false
        case COMPILATION_UNIT if elem.isInstanceOf[ScalaSourceFile] && isRemoved =>
          buff += elem.asInstanceOf[ScalaSourceFile]
          false
          
        case COMPILATION_UNIT if isAdded =>
          logger.debug("added compilation unit " + elem.getElementName())
          asScalaProject(elem.getJavaProject().getProject).foreach(projectsToReset +=)
          false

        case _ => false
      }

      if (processChildren)
        delta.getAffectedChildren foreach { findRemovedSources(_) }
    }
    findRemovedSources(event.getDelta)
    
    // ask for the changed scala files to be refreshed in each project presentation compiler if needed
    if (changed.nonEmpty) {
      changed.toList groupBy(_.getJavaProject.getProject) foreach {
        case (project, units) =>
          asScalaProject(project) foreach { p =>
            if (project.isOpen && !projectsToReset(p)) {
              p.refreshChangedFiles(units.map(_.getResource.asInstanceOf[IFile]))
            }
          }
      }
    }
    
    projectsToReset.foreach(_.resetPresentationCompiler)
    if(buff.nonEmpty) {
      buff.toList groupBy (_.getJavaProject.getProject) foreach {
        case (project, srcs) =>
          asScalaProject(project) foreach { p =>
            if (project.isOpen && !projectsToReset(p))
              p doWithPresentationCompiler (_.filesDeleted(srcs))
          }
      }
    }
  }

  
  def bundlePath = Utils.tryExecute {
    val bundle = getBundle
    val bpath = bundle.getEntry("/")
    val rpath = FileLocator.resolve(bpath)
    rpath.getPath
  }.getOrElse("unresolved")

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

  // IPartListener
  def partActivated(part: IWorkbenchPart) {}
  def partDeactivated(part: IWorkbenchPart) {}
  def partBroughtToTop(part: IWorkbenchPart) {}
  def partOpened(part: IWorkbenchPart) {
    logger.debug("open " + part.getTitle)
    doWithCompilerAndFile(part) { (compiler, ssf) =>
      compiler.askToDoFirst(ssf)
      compiler.askReload(ssf, ssf.getContents)
    }
  }
  def partClosed(part: IWorkbenchPart) {
    logger.debug("close " + part.getTitle)
    doWithCompilerAndFile(part) { (compiler, ssf) =>
      compiler.discardSourceFile(ssf)
    }
  }

  private def doWithCompilerAndFile(part: IWorkbenchPart)(op: (ScalaPresentationCompiler, ScalaSourceFile) => Unit) {
    part match {
      case editor: IEditorPart =>
        editor.getEditorInput match {
          case fei: FileEditorInput =>
            val f = fei.getFile
            if (f.getName.endsWith(scalaFileExtn)) {
              for (ssf <- ScalaSourceFile.createFromPath(f.getFullPath.toString)) {
                val proj = getScalaProject(f.getProject)
                if (proj.underlying.isOpen)
                  proj.doWithPresentationCompiler(op(_, ssf)) // so that an exception is not thrown
              }
            }
          case _ =>
        }
      case _ =>
    }
  }
}

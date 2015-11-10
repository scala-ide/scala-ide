package org.scalaide.core.internal

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.tools.nsc.settings.ScalaVersion
import org.eclipse.core.resources.IFile
import org.eclipse.core.resources.IProject
import org.eclipse.core.resources.IResourceChangeEvent
import org.eclipse.core.resources.IResourceChangeListener
import org.eclipse.core.resources.IResourceDelta
import org.eclipse.core.resources.IResourceDeltaVisitor
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.runtime.Platform
import org.eclipse.core.runtime.content.IContentType
import org.eclipse.jdt.core.ElementChangedEvent
import org.eclipse.jdt.core.IClassFile
import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.jdt.core.IElementChangedListener
import org.eclipse.jdt.core.IJavaElement
import org.eclipse.jdt.core.IJavaElementDelta
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.JavaCore
import org.eclipse.ui.IEditorInput
import org.eclipse.ui.PlatformUI
import org.osgi.framework.BundleContext
import org.scalaide.core.IScalaInstallation
import org.scalaide.core.IScalaPlugin
import org.scalaide.core.SdtConstants
import org.scalaide.core.internal.builder.zinc.CompilerInterfaceStore
import org.scalaide.core.internal.jdt.model.ScalaClassFile
import org.scalaide.core.internal.jdt.model.ScalaCompilationUnit
import org.scalaide.core.internal.jdt.model.ScalaSourceFile
import org.scalaide.core.internal.project._
import org.scalaide.core.internal.project.ScalaInstallation.platformInstallation
import org.scalaide.logging.HasLogger
import org.scalaide.logging.PluginLogConfigurator
import org.scalaide.ui.internal.diagnostic
import org.scalaide.ui.internal.editor.ScalaDocumentProvider
import org.scalaide.ui.internal.migration.RegistryExtender
import org.scalaide.ui.internal.templates.ScalaTemplateManager
import org.scalaide.util.Utils.WithAsInstanceOfOpt
import org.scalaide.core.internal.statistics.Statistics
import org.scalaide.util.eclipse.OSGiUtils
import org.scalaide.util.internal.CompilerUtils._
import org.scalaide.util.internal.FixedSizeCache
import org.eclipse.jdt.internal.ui.JavaPlugin

object ScalaPlugin {

  @volatile private var plugin: ScalaPlugin = _

  def apply(): ScalaPlugin = plugin

}

class ScalaPlugin extends IScalaPlugin with PluginLogConfigurator with IResourceChangeListener with IElementChangedListener with HasLogger {

 /**
  * Check if the given version is compatible with the current plug-in version.
  * Check on the major/minor number, discard the maintenance number.
  *
  * For example 2.9.1 and 2.9.2-SNAPSHOT are compatible versions whereas
  * 2.8.1 and 2.9.0 aren't.
  */
  def isCompatibleVersion(version: ScalaVersion, project: ScalaProject): Boolean = project.getCompatibilityMode match {
    case Same ⇒
      isBinarySame(ScalaVersion.current, version) // don't treat 2 unknown versions as equal
    case Previous ⇒
      isBinaryPrevious(ScalaVersion.current, version)
    case Subsequent ⇒
      isBinarySubsequent(ScalaVersion.current, version)
  }

  private lazy val sdtCoreBundle = getBundle()

  lazy val sbtCompilerBundle = Platform.getBundle(SdtConstants.SbtPluginId)
  lazy val sbtCompilerInterfaceBundle = Platform.getBundle(SdtConstants.SbtCompilerInterfacePluginId)
  lazy val sbtCompilerInterface = OSGiUtils.pathInBundle(sbtCompilerInterfaceBundle, "/")

  lazy val templateManager = new ScalaTemplateManager()

  lazy val scalaSourceFileContentType: IContentType =
    Platform.getContentTypeManager().getContentType("scala.tools.eclipse.scalaSource")

  lazy val scalaClassFileContentType: IContentType =
    Platform.getContentTypeManager().getContentType("scala.tools.eclipse.scalaClass")

  /**
   * The document provider needs to exist only a single time because it caches
   * compilation units (their working copies). Each `ScalaSourceFileEditor` is
   * associated with this document provider.
   */
  private[scalaide] lazy val documentProvider = new ScalaDocumentProvider

  override def start(context: BundleContext) = {
    ScalaPlugin.plugin = this
    super.start(context)

    if (!headlessMode) {
      PlatformUI.getWorkbench.getEditorRegistry.setDefaultEditor("*.scala", SdtConstants.EditorId)
      diagnostic.StartupDiagnostics.run

      new RegistryExtender().perform()
    }
    ResourcesPlugin.getWorkspace.addResourceChangeListener(this, IResourceChangeEvent.PRE_CLOSE | IResourceChangeEvent.POST_CHANGE)
    JavaCore.addElementChangedListener(this)
    logger.info("Scala compiler bundle: " + platformInstallation.compiler.classJar.toOSString() )
  }

  override def stop(context: BundleContext) = {
    ResourcesPlugin.getWorkspace.removeResourceChangeListener(this)
    for {
      iProject <- ResourcesPlugin.getWorkspace.getRoot.getProjects
      if iProject.isOpen
      scalaProject <- asScalaProject(iProject)
    } scalaProject.projectSpecificStorage.save()
    super.stop(context)
    ScalaPlugin.plugin = null
  }

  /** The compiler-interface store, located in this plugin configuration area (usually inside the metadata directory */
  lazy val compilerInterfaceStore: CompilerInterfaceStore = new CompilerInterfaceStore(Platform.getStateLocation(sdtCoreBundle), this)

  /** A LRU cache of class loaders for Scala builders */
  lazy val classLoaderStore: FixedSizeCache[IScalaInstallation,ClassLoader] = new FixedSizeCache(initSize = 2, maxSize = 3)

  // TODO: eventually scala plugin should have its own image description registry
  lazy val imageDescriptorRegistry = JavaPlugin.getImageDescriptorRegistry
  // Scala project instances
  private val projects = new mutable.HashMap[IProject, ScalaProject]

  private lazy val stats = new Statistics

  /** Returns the statistics tracker. */
  def statistics = stats

  override def scalaCompilationUnit(input: IEditorInput): Option[ScalaCompilationUnit] = {
    def unitOfSourceFile = Option(documentProvider.getWorkingCopy(input)) map (ScalaCompilationUnit.castFrom)

    def unitOfClassFile = input.getAdapter(classOf[IClassFile]) match {
      case tr: ScalaClassFile => Some(tr)
      case _                  => None
    }

    unitOfSourceFile orElse unitOfClassFile
  }

  def getJavaProject(project: IProject) = JavaCore.create(project)

  override def getScalaProject(project: IProject): ScalaProject = projects.synchronized {
    projects.get(project) getOrElse {
      val scalaProject = ScalaProject(project)
      projects(project) = scalaProject
      scalaProject
    }
  }

  override def asScalaProject(project: IProject): Option[ScalaProject] = {
    if (ScalaProject.isScalaProject(project)) {
      Some(getScalaProject(project))
    } else {
      None
    }
  }

  def disposeProject(project: IProject): Unit = {
    projects.synchronized {
      projects.get(project) foreach { (scalaProject) =>
        projects.remove(project)
        scalaProject.dispose()
      }
    }
  }

  /** Restart all presentation compilers in the workspace. Need to do it in order
   *  for them to pick up the new std out/err streams.
   */
  def resetAllPresentationCompilers(): Unit = {
    for {
      iProject <- ResourcesPlugin.getWorkspace.getRoot.getProjects
      if iProject.isOpen
      scalaProject <- asScalaProject(iProject)
    } scalaProject.presentationCompiler.askRestart()
  }

  override def resourceChanged(event: IResourceChangeEvent): Unit = {
    (event.getResource, event.getType) match {
      case (project: IProject, IResourceChangeEvent.PRE_CLOSE) =>
        disposeProject(project)
      case _ =>
    }
    Option(event.getDelta()) foreach (_.accept(new IResourceDeltaVisitor() {
      override def visit(delta: IResourceDelta): Boolean = {
        // This is obtained at project opening or closing, meaning the 'openness' state changed
        if (delta.getFlags == IResourceDelta.OPEN) {
          val resource = delta.getResource().asInstanceOfOpt[IProject]
          resource foreach { r =>
            // that particular classpath check can set the Installation (used, e.g., for sbt-eclipse imports)
            // setting the Installation triggers a recursive check
            asScalaProject(r) foreach { p =>
              try {
                // It's important to save this /before/ checking classpath : classpath
                // checks create their own preference modifications under some conditions.
                // Doing them concurrently can wreak havoc.
                p.projectSpecificStorage.save()
              } finally {
                p.checkClasspath(true)
              }
            }
          }
          false
        }
        else
          true
      }
    }))
  }

  override def elementChanged(event: ElementChangedEvent): Unit = {
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
            case javaProject: IJavaProject =>
              asScalaProject(javaProject.getProject()) foreach (_.classpathHasChanged(queue = false))
            case _ =>
          }
        }
      }
    }

    // process deleted files
    val buff = new ListBuffer[ScalaSourceFile]
    val changed = new ListBuffer[ICompilationUnit]
    val projectsToReset = new mutable.HashSet[ScalaProject]

    def findRemovedSources(delta: IJavaElementDelta): Unit = {
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
            logger.info("package fragment root changed (resetting presentation compiler): " + elem.getElementName())
            asScalaProject(elem.getJavaProject().getProject).foreach(projectsToReset += _)
          }
          !hasContentChanged

        case PACKAGE_FRAGMENT =>
          val hasContentChanged = isAdded || isRemoved
          if (hasContentChanged) {
            logger.debug("package fragment added or removed: " + elem.getElementName())
            asScalaProject(elem.getJavaProject().getProject).foreach(projectsToReset += _)
          }
          // stop recursion here, we need to reset the PC anyway
          !hasContentChanged

        // TODO: the check should be done with isInstanceOf[ScalaSourceFile] instead of
        // endsWith(scalaFileExtn), but it is not working for Play 2.0 because of #1000434
        case COMPILATION_UNIT if isChanged && elem.getResource != null && elem.getResource.getName.endsWith(SdtConstants.ScalaFileExtn) =>
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
              p.presentationCompiler.internal (_.filesDeleted(srcs))
          }
      }
    }
  }

}

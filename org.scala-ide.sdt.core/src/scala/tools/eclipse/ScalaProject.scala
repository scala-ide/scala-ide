/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import scala.tools.eclipse.util.Tracer
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
import scala.tools.nsc.{ MissingRequirementError }
import scala.tools.nsc.interactive.compat.Settings
import scala.tools.nsc.util.SourceFile
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.tools.eclipse.properties.PropertyStore
import scala.tools.eclipse.util.{ Cached, EclipseResource, IDESettings, OSGiUtils, ReflectionUtils }
import scala.tools.eclipse.ui.semantic.highlighting.UnusedImportsAnalyzer

class ScalaProject(val underlying: IProject) {
  import ScalaPlugin.plugin

  private var buildManager0 : EclipseBuildManager = null
  private val resetPendingLock = new Object
  private var resetPending = false

  private var scalaVersion = "2.8.x"

  private val presentationCompiler = new Cached[ScalaPresentationCompiler] {
    override def create() = {
      try {
        Tracer.println("create a new ScalaPresentationCompiler for " + underlying.getName )
        val settings = new Settings({x => ScalaPlugin.plugin.logWarning(x, None)})
        settings.printtypes.tryToSet(Nil)
        settings.verbose.tryToSetFromPropertyValue("true")
        settings.XlogImplicits.tryToSetFromPropertyValue("true")
        //TODO replace the if by a conditional Extension Point (or better)
        val compiler = if (scalaVersion.startsWith("2.9")) {
          initialize(settings, _.name.startsWith("-Ypresentation"))
          new ScalaPresentationCompiler(settings)
        } else if (IDESettings.exceptionOnCreatePresentationCompiler.value) {
          throw new Exception("exceptionOnCreatePresentationCompiler == true")
        } else {
          initialize(settings, _ => true)
          new ScalaPresentationCompiler(settings) with scalac_28.TopLevelMapTyper {
            def project = ScalaProject.this
          }
        }
        compiler
      } catch {
        case ex@MissingRequirementError(required) =>
          failedCompilerInitialization("Could not initialize Scala compiler because it could not find a required class: " + required)
          plugin.logError(ex)
          throw ex
        case ex =>
          failedCompilerInitialization("failed to initialize Scala compiler properly : "+ ex.getMessage)
          plugin.logError(ex)
          throw ex
      }
    }

    override def destroy(compiler : ScalaPresentationCompiler) {
      compiler.destroy()
    }

    private def failedCompilerInitialization(msg: String) {
      import org.eclipse.jface.dialogs.MessageDialog
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

  private def getOrFailed[U](v : Either[Throwable, U]) : U = v match {
    case Right(t) => t
    case Left(ex) => throw new IllegalStateException("failed to access value", ex) //to have the stack trace of the caller and the of the compiler creation (async)
  }

  override def toString = underlying.getName

  def externalDepends = underlying.getReferencedProjects

  lazy val javaProject = {
    if (!underlying.exists())
      underlying.create(null)
    JavaCore.create(underlying)
  }

  private def toIFolder(v : IPath) : IFolder = {
    // findMember return null if IPath to a non existing resource
    //val b = plugin.workspaceRoot.findMember(v)
    //if (b == null) Tracer.println("IResource is null from plugin.workspaceRoot.findMember(" + v + ")")

    // getFolder don't test if resource exist
    val b = plugin.workspaceRoot.getFolder(v)
    b
  }
  private def toIFolder(cpe : IClasspathEntry) : IFolder = toIFolder(cpe.getPath)
  private def toOutput(cpe : IClasspathEntry, jproject : IJavaProject = javaProject) : IFolder = {
    val cpeOutput = cpe.getOutputLocation
    val p = if (cpeOutput == null) jproject.getOutputLocation else cpeOutput
    toIFolder(p)
  }

  private def findSelectedIFile(cpe : IClasspathEntry) : Seq[IFile] = {
    def toCharArray(v : IPath) = v.toPortableString.toCharArray

    val selected = new HashSet[IFile]()
    val inclusionPatterns = cpe.getInclusionPatterns.map{ toCharArray }
    val exclusionPatterns = cpe.getExclusionPatterns.map{ toCharArray }
    val folder = toIFolder(cpe)
    if (folder.exists) {
      folder.accept(
        new IResourceProxyVisitor {
          def visit(proxy : IResourceProxy) : Boolean = proxy.getType match {
            case IResource.FILE => {
              val resource = proxy.requestResource
              if(plugin.isBuildable(resource.asInstanceOf[IFile]) && Util.isExcluded(resource.getFullPath, inclusionPatterns, exclusionPatterns, false)) {
                selected += resource.asInstanceOf[IFile]
              }
              false
            }
            case IResource.FOLDER => {
              //TODO case of source folder is project root
  //                var folderPath : IPath = null
  //                if (isAlsoProject) {
  //                  folderPath = proxy.requestFullPath
  //                  if (isExcludedFromProject(env, folderPath))
  //                    return false
  //                }
              if ((exclusionPatterns != null) && Util.isExcluded(proxy.requestFullPath, inclusionPatterns, exclusionPatterns, true)) {
                // must walk children if inclusionPatterns != null, can skip them if == null
                // but folder is excluded so do not create it in the output folder
                inclusionPatterns != null
              } else {
                true
              }
            }
            case _ => true
          }
        }
        , IResource.NONE
      )
    }

    selected.toSeq
  }

  private def sourcesFoldersInfo = (javaProject.getResolvedClasspath(true)
      .filter(_.getEntryKind == IClasspathEntry.CPE_SOURCE)
      .filter(cpe => toIFolder(cpe) != null)
  )

  def sourceFolders : Seq[IResource] = sourcesFoldersInfo.map{ cpe => toIFolder(cpe.getPath) }.toSeq

  private def outputFolders : Seq[IResource] = sourcesFoldersInfo.map{ cpe => toOutput(cpe) }.toSeq.distinct

  /**
   * @return a classpath with absolute IPath (location)
   *
   * @TODO adding or not the output folder of current sourcefolders ??
   */
  private def classpath : Seq[IPath] = {
    val path = new LinkedHashSet[IPath]

    // location is the path on local filesystem
    // should work for File (jar) and Folder
    def pathToLocation(p : IPath) : IPath = {
      plugin.workspaceRoot.findMember(p) match {
        case null => p
        case iresource => iresource.getLocation
      }
    }

    def classpath(jProject : IJavaProject, exportedOnly : Boolean, includeSourceOutput : Boolean) : Unit = {
      val cpes = jProject.getResolvedClasspath(true)

      for (cpe <- cpes ) cpe.getEntryKind match {
        case IClasspathEntry.CPE_SOURCE if includeSourceOutput => {
          val output = toOutput(cpe, jProject)
          if (output != null) {
            path += output.getLocation
          }
        }
        case IClasspathEntry.CPE_PROJECT if (!exportedOnly || cpe.isExported) => {
          val depProject = plugin.workspaceRoot.getProject(cpe.getPath.lastSegment)
          if (JavaProject.hasJavaNature(depProject)) {
            classpath(JavaCore.create(depProject), true, true)
          }
        }
        case IClasspathEntry.CPE_LIBRARY if (cpe.getPath != null && (!exportedOnly || cpe.isExported)) =>{
          path += pathToLocation(cpe.getPath.makeAbsolute)
        }
        case _ =>
      }
    }
    //TODO sort the classpath's entries ?
    classpath(javaProject, false, IDESettings.outputInClasspath.value)
    path.toList
  }



  def allSourceFiles() : Set[IFile] = sourcesFoldersInfo.flatMap{ findSelectedIFile }.toSet

  private def cleanOutputFolders(implicit monitor : IProgressMonitor) = {
    def delete(container : IContainer, deleteDirs : Boolean)(f : String => Boolean) : Unit = {
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
    }
    for(outputFolder <- outputFolders) outputFolder match {
      case container : IContainer => delete(container, container != underlying)(_.endsWith(".class"))
      case _ =>
    }
  }

  def refreshOutput: Unit = {
    val res = plugin.workspaceRoot.findMember(javaProject.getOutputLocation)
    if (res ne null)
      res.refreshLocal(IResource.DEPTH_INFINITE, null)
  }


  def initialize(settings : Settings, filter: Settings#Setting => Boolean) = {
    val sfs = sourcesFoldersInfo
    sfs.foreach { cpe =>
      settings.outputDirs.add(EclipseResource(toIFolder(cpe)), EclipseResource(toOutput(cpe)))
    }

    // TODO Per-file encodings, but as eclipse user it's easier to handler Charset at project level
    settings.encoding.value = underlying.getDefaultCharset
    settings.classpath.value = classpath.map{ _.toOSString }.mkString(pathSeparator)

    settings.classpath.value = classpath.map(_.toOSString).mkString(pathSeparator)
    // source path should be emtpy. the build manager decides what files get recompiled when.
    // if scalac finds a source file newer than its corresponding classfile, it will 'compileLate'
    // that file, using an AbstractFile/PlainFile instead of the EclipseResource instance. This later
    // causes problems if errors are reported against that file. Anyway, it's wrong to have a sourcepath
    // when using the build manager.
    settings.sourcepath.value = sfs.map{ x => toIFolder(x).getLocation.toOSString }.mkString(pathSeparator)
  
    val store = storage
    for (
      box <- IDESettings.compilerSettings;
      setting <- box.userSettings;
      if filter(setting)
    ) {
      val value0 = store.getString(SettingConverterUtil.convertNameToProperty(setting.name))
      try {
        val value = if (setting ne settings.pluginsDir) value0 else {
          ScalaPlugin.plugin.continuationsClasses map {
            _.removeLastSegments(1).toOSString + (if (value0 == null || value0.length == 0) "" else ":" + value0)
          } getOrElse value0
        }
        if (value != null && value.length != 0) {
          setting.tryToSetFromPropertyValue(value)
        }
        Tracer.println("initializing %s to %s".format(setting.name, value0.toString))
      } catch {
        case t: Throwable => plugin.logError("Unable to set setting '" + setting.name + "' to '" + value0 + "'", t)
      }
    }
    Tracer.println("initializing " + settings.encoding)
    Tracer.println("sourcepath : " + settings.sourcepath.value)
    Tracer.println("classpath  : " + settings.classpath.value)
    Tracer.println("outputdirs : " + settings.outputDirs.outputs)
  }
  
  private def buildManagerInitialize: String =
    storage.getString(SettingConverterUtil.convertNameToProperty(util.ScalaPluginSettings.buildManager.name))
  
  private def storage = {
    val workspaceStore = ScalaPlugin.plugin.getPreferenceStore
    val projectStore = new PropertyStore(underlying, workspaceStore, plugin.pluginId)
    val useProjectSettings = projectStore.getBoolean(SettingConverterUtil.USE_PROJECT_SETTINGS_PREFERENCE)

    if (useProjectSettings) projectStore else workspaceStore
  }


  def defaultOrElse[T]: T = {  
//    if (underlying.isOpen)
//      failedCompilerInitialization("Compiler failed to initialize properly.")

    // FIXME: this now shows 2 dialog boxes, the one above and the one caused by the throw below
    // will investigate further -DM
    throw new IllegalStateException("InvalidCompilerSettings") // DM: see if this error is easier to catch
//    DM: commented out the null below.
//    null.asInstanceOf[T] // we're already in deep trouble here, so one more NPE won't kill us    
  }

  /** 
   * If the presentation compiler has failed to initialize and no `orElse` is specified, 
   * the default handler throws an `InvalidCompilerSettings` exception
   * If T = Unit, then doWithPresentationCompiler can be used, which does not throw.
   */
  def withPresentationCompiler[T](op: ScalaPresentationCompiler => T)(orElse: => T = defaultOrElse): T = {
//    getOrFailed(presentationCompiler.apply(op))
    presentationCompiler.apply(op) match {
      case Right(t) => t
      case Left(ex) => {
        Tracer.println("failed to access value : " + ex) //to have the stack trace of the caller and the of the compiler creation (async)
        orElse
      }
    }
  }

  def withPresentationCompilerIfExists(op : ScalaPresentationCompiler => Unit) : Unit = {
    presentationCompiler.doIfExist(op)
  }

  def withSourceFile[T](scu: ScalaCompilationUnit)(op: (SourceFile, ScalaPresentationCompiler) => T)(orElse: => T = defaultOrElse): T = {
    withPresentationCompiler { compiler =>
      compiler.withSourceFile(scu)(op)
    } {orElse}
  }

  def resetPresentationCompiler() {
    Tracer.println("resetPresentationCompiler")
    presentationCompiler.invalidate
  }

  def buildManager = {
    if (buildManager0 == null) {
      val settings = new Settings({x => ScalaPlugin.plugin.logWarning(x, None)})
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
      Tracer.println("creating a new EclipseBuildManager : " + choice)
      choice match {
      	case "refined" =>
      	  println("BM: Refined Build Manager")
      	  buildManager0 = new buildmanager.refined.EclipseRefinedBuildManager(this, settings)
      	case "sbt0.9"  =>
      	  println("BM: SBT 0.9 enhanced Build Manager")
      	  buildManager0 = new buildmanager.sbtintegration.EclipseSbtBuildManager(this, settings)
      	case _         =>
      	  println("Invalid build manager choice '" + choice  + "'. Setting to (default) refined build manager")
      	  buildManager0 = new buildmanager.refined.EclipseRefinedBuildManager(this, settings)
      }

      //buildManager0 = new EclipseBuildManager(this, settings)
    }
    buildManager0
  }

  def build(addedOrUpdated : Set[IFile], removed : Set[IFile])(implicit monitor : IProgressMonitor) {
    buildManager.build(addedOrUpdated, removed)
    if (IDESettings.markUnusedImports.value) {
      for ( file <- addedOrUpdated) {
        UnusedImportsAnalyzer.markUnusedImports(file)
      }
    }
    refreshOutput

    // Already performs saving the dependencies
  }

  def clean(implicit monitor : IProgressMonitor) = {
    Tracer.println("clean scala project " + underlying.getName)
    cleanOutputFolders(monitor)
    resetCompilers(monitor)
  }

  /**
   * remove markers + clean output dear + remove builder from memory
   * can raise exception when deleteMarkers (ResourceException: The resource tree is locked for modifications.)
   */
  private def resetBuildCompiler(monitor : IProgressMonitor) {
    Tracer.println("resetting compilers for " + underlying.getName)
    try {
      //underlying.deleteMarkers(plugin.problemMarkerId, true, IResource.DEPTH_INFINITE)
      if (buildManager0 != null) buildManager0.clean(monitor)
    } finally {
      buildManager0 = null
    }
  }

  def resetCompilers(implicit monitor : IProgressMonitor) = {
    resetPresentationCompiler()
    resetBuildCompiler(monitor)
  }
}

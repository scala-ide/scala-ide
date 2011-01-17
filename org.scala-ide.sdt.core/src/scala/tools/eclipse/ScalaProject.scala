/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import scala.tools.eclipse.internal.logging.Tracer
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
import org.eclipse.swt.widgets.Display

import scala.tools.nsc.Settings
import scala.tools.nsc.util.SourceFile

import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.tools.eclipse.properties.PropertyStore
import scala.tools.eclipse.util.{ Cached, EclipseResource, IDESettings, OSGiUtils, ReflectionUtils } 

class ScalaProject(val underlying : IProject) {
  import ScalaPlugin.plugin

  private var buildManager0 : EclipseBuildManager = null
  private val resetPendingLock = new Object
  private var resetPending = false
    
  private var scalaVersion = "2.8.x"
    
  private val presentationCompiler = new Cached[ScalaPresentationCompiler] {
    override def create() = {
      val settings = new Settings
      settings.printtypes.tryToSet(Nil)
      settings.verbose.tryToSetFromPropertyValue("true")
      settings.XlogImplicits.tryToSetFromPropertyValue("true")
      //TODO replace the if by a conditional Extension Point (or better)
      if (scalaVersion.startsWith("2.9")) {
        initialize(settings, _.name.startsWith("-Ypresentation"))
        new ScalaPresentationCompiler(settings)
      } else {
        initialize(settings, _ => true)
        new ScalaPresentationCompiler(settings) with scalac_28.TopLevelMapTyper {
          def project = ScalaProject.this
        }
      }
    }
    
    override def destroy(compiler : ScalaPresentationCompiler) {
      compiler.destroy()
    }
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
    
    toIFolder(cpe).accept(
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
  
//  def sourceOutputFolders(env : NameEnvironment) : Seq[(IContainer, IContainer)] = {
//    val sourceLocations = NameEnvironmentUtils.sourceLocations(env)
//    sourceLocations.map(cl => (ClasspathLocationUtils.sourceFolder(cl), ClasspathLocationUtils.binaryFolder(cl))) 
//  }

//  def isExcludedFromProject(env : NameEnvironment, childPath : IPath) : Boolean = {
//    // answer whether the folder should be ignored when walking the project as a source folder
//    if (childPath.segmentCount() > 2) return false // is a subfolder of a package
//
//    val sourceLocations = NameEnvironmentUtils.sourceLocations(env)
//    for (sl <- sourceLocations) {
//      val binaryFolder = ClasspathLocationUtils.binaryFolder(sl)
//      if (childPath == binaryFolder.getFullPath) return true
//      val sourceFolder = ClasspathLocationUtils.sourceFolder(sl)
//      if (childPath == sourceFolder.getFullPath) return true
//    }
//    
//    // skip default output folder which may not be used by any source folder
//    return childPath == javaProject.getOutputLocation
//  }
  
  def allSourceFiles() : Set[IFile] = sourcesFoldersInfo.flatMap{ findSelectedIFile }.toSet
//  def allSourceFiles() : Set[IFile] = allSourceFiles(new NameEnvironment(javaProject)) 
  
//  def allSourceFiles(env : NameEnvironment) : Set[IFile] = {
//    val sourceFiles = new HashSet[IFile]
//    val sourceLocations = NameEnvironmentUtils.sourceLocations(env)
//
//    for (sourceLocation <- sourceLocations) {
//      val sourceFolder = ClasspathLocationUtils.sourceFolder(sourceLocation)
//      val exclusionPatterns = ClasspathLocationUtils.exclusionPatterns(sourceLocation)
//      val inclusionPatterns = ClasspathLocationUtils.inclusionPatterns(sourceLocation)
//      val isAlsoProject = sourceFolder == javaProject
//      val segmentCount = sourceFolder.getFullPath.segmentCount
//      val outputFolder = ClasspathLocationUtils.binaryFolder(sourceLocation)
//      val isOutputFolder = sourceFolder == outputFolder
//      sourceFolder.accept(
//        new IResourceProxyVisitor {
//          def visit(proxy : IResourceProxy) : Boolean = {
//            proxy.getType match {
//              case IResource.FILE =>
//                val resource = proxy.requestResource
//                if (plugin.isBuildable(resource.asInstanceOf[IFile])) {
//                  if (exclusionPatterns != null || inclusionPatterns != null)
//                    if (Util.isExcluded(resource.getFullPath, inclusionPatterns, exclusionPatterns, false))
//                      return false
//                  sourceFiles += resource.asInstanceOf[IFile]
//                }
//                return false
//                
//              case IResource.FOLDER => 
//                var folderPath : IPath = null
//                if (isAlsoProject) {
//                  folderPath = proxy.requestFullPath
//                  if (isExcludedFromProject(env, folderPath))
//                    return false
//                }
//                if (exclusionPatterns != null) {
//                  if (folderPath == null)
//                    folderPath = proxy.requestFullPath
//                  if (Util.isExcluded(folderPath, inclusionPatterns, exclusionPatterns, true)) {
//                    // must walk children if inclusionPatterns != null, can skip them if == null
//                    // but folder is excluded so do not create it in the output folder
//                    return inclusionPatterns != null
//                  }
//                }
//                
//              case _ =>
//            }
//            return true
//          }
//        },
//        IResource.NONE
//      )
//    }
//    sourceFiles.toSet
//  }
    
//  private def createOutputFolders() = {
//    for(outputFolder <- outputFolders) outputFolder match {
//      case fldr : IFolder =>
//        def createParentFolder(parent : IContainer) {
//          if(!parent.exists()) {
//            createParentFolder(parent.getParent)
//            parent.asInstanceOf[IFolder].create(true, true, null)
//            parent.setDerived(true)
//          }
//        }
//      
//        fldr.refreshLocal(IResource.DEPTH_ZERO, null)
//        if(!fldr.exists()) {
//          createParentFolder(fldr.getParent)
//          fldr.create(IResource.FORCE | IResource.DERIVED, true, null)
//        }
//      case _ => 
//    }
//  }
  
  private def cleanOutputFolders(monitor : IProgressMonitor) = {
    def delete(container : IContainer, deleteDirs : Boolean)(f : String => Boolean) : Unit =
      if (container.exists()) {
        container.members.foreach {
          case cntnr : IContainer =>
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
            }
            else
              delete(cntnr, deleteDirs)(f)
          case file : IFile if f(file.getName) =>
            try {
              file.delete(true, monitor)
            } catch {
              case t => plugin.logError(t)
            }
          case _ => 
        }
      }
    for(outputFolder <- outputFolders) outputFolder match {
      case container : IContainer => delete(container, container != underlying)(_.endsWith(".class"))
      case _ => 
    }    
  }
  
  def refreshOutput : Unit = {
    val res = plugin.workspaceRoot.findMember(javaProject.getOutputLocation)
    if (res ne null)
      res.refreshLocal(IResource.DEPTH_INFINITE, null)
  }
    

  def initialize(settings : Settings, filter: Settings#Setting => Boolean) = {
//    val env = new NameEnvironment(javaProject)
//    
//    for((src, dst) <- sourceOutputFolders(env))
//      settings.outputDirs.add(EclipseResource(src), EclipseResource(dst))
    val sfs = sourcesFoldersInfo
    sfs.foreach { cpe =>
      settings.outputDirs.add(EclipseResource(toIFolder(cpe)), EclipseResource(toOutput(cpe)))
    }
    
    // TODO Per-file encodings, but as eclipse user it's easier to handler Charset at project level
    settings.encoding.value = underlying.getDefaultCharset
//    if (!sfs.isEmpty) {
//      val path = sfs.iterator.next
//      plugin.workspaceRoot.findContainersForLocation(path) match {
//        case Array(container) => settings.encoding.value = container.getDefaultCharset   
//        case _ =>
//      }
//    }

    settings.classpath.value = classpath.map{ _.toOSString }.mkString(pathSeparator)
    settings.sourcepath.value = sfs.map{ x => toIFolder(x).getLocation.toOSString }.mkString(pathSeparator)
    
    
    val workspaceStore = ScalaPlugin.plugin.getPreferenceStore
    val projectStore = new PropertyStore(underlying, workspaceStore, plugin.pluginId)
    val useProjectSettings = projectStore.getBoolean(SettingConverterUtil.USE_PROJECT_SETTINGS_PREFERENCE)
    
    val store = if (useProjectSettings) projectStore else workspaceStore  
    for (
      box <- IDESettings.shownSettings(settings);
      setting <- box.userSettings;
      if filter(setting)
    ) {
      val value0 = store.getString(SettingConverterUtil.convertNameToProperty(setting.name))
      try {
        val value = if (setting ne settings.pluginsDir) value0 else {
          ScalaPlugin.plugin.continuationsClasses map {
            _.removeLastSegments(1).toOSString+(if (value0 == null || value0.length == 0) "" else ":"+value0)
          } getOrElse value0
        }
        if (value != null && value.length != 0) {
          setting.tryToSetFromPropertyValue(value)
        }
        Tracer.println("initializing %s to %s".format(setting.name, value0.toString))
      } catch {
        case t : Throwable => plugin.logError("Unable to set setting '"+setting.name+"' to '"+value0+"'", t)
      }
    }
    Tracer.println("initializing " + settings.encoding)
    Tracer.println("sourcepath : " + settings.sourcepath.value)
    Tracer.println("classpath  : " + settings.classpath.value)
    Tracer.println("outputdirs : " + settings.outputDirs.outputs)
  }
  
  def withPresentationCompiler[T](op : ScalaPresentationCompiler => T) : T = {
    presentationCompiler(op)
  }

  def withSourceFile[T](scu : ScalaCompilationUnit)(op : (SourceFile, ScalaPresentationCompiler) => T) : T =
    withPresentationCompiler { compiler =>
      compiler.withSourceFile(scu)(op)
    }
  
  def resetPresentationCompiler() {
    Tracer.println("resetPresentationCompiler")
    presentationCompiler.invalidate
  }
  
  def buildManager = {
    if (buildManager0 == null) {
      Tracer.println("creating a new EclipseBuildManager")
      val settings = new Settings
      initialize(settings, _ => true)
      buildManager0 = new EclipseBuildManager(this, settings)
    }
    buildManager0
  }

  def build(addedOrUpdated : Set[IFile], removed : Set[IFile], monitor : IProgressMonitor) {
    buildManager.build(addedOrUpdated, removed, monitor)
    refreshOutput
  }

  def clean(monitor : IProgressMonitor) = {
    Tracer.println("clean scala project " + underlying.getName)
    resetCompilers(monitor)
  }

  /**
   * remove markers + clean output dear + remove builder from memory  
   * can raise exception when deleteMarkers (ResourceException: The resource tree is locked for modifications.)
   */
  def resetBuildCompiler(monitor : IProgressMonitor) {
    Tracer.println("resetting compilers for " + underlying.getName)
    try {
      underlying.deleteMarkers(plugin.problemMarkerId, true, IResource.DEPTH_INFINITE)
      cleanOutputFolders(monitor)
    } finally {
      buildManager0 = null
    }
  }
  
  def resetCompilers(monitor : IProgressMonitor) = {
    resetBuildCompiler(monitor)
    resetPresentationCompiler()
  }
}

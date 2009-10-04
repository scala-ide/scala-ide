/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import scala.collection.immutable.Set
import scala.collection.mutable.{ LinkedHashSet, ListBuffer, HashSet }

import java.io.File.pathSeparator

import org.eclipse.core.resources.{ IContainer, IFile, IFolder, IMarker, IProject, IResource, IResourceProxy, IResourceProxyVisitor, IWorkspaceRunnable, ResourcesPlugin}
import org.eclipse.core.runtime.{ IPath, IProgressMonitor, Path }
import org.eclipse.jdt.core.{ IClasspathEntry, IJavaElement, IJavaProject, IPackageFragmentRoot, IType, JavaCore }
import org.eclipse.jdt.internal.core.JavaProject
import org.eclipse.jdt.internal.core.builder.{ ClasspathDirectory, ClasspathLocation, NameEnvironment }
import org.eclipse.jdt.internal.core.util.Util
import org.eclipse.jdt.ui.JavaUI
import org.eclipse.jface.text.TextPresentation
import org.eclipse.jface.text.hyperlink.IHyperlink
import org.eclipse.swt.SWT
import org.eclipse.swt.custom.StyleRange
import org.eclipse.ui.PlatformUI
import org.eclipse.ui.texteditor.ITextEditor

import scala.tools.nsc.{ Global, Settings, interactive }
import scala.tools.nsc.interactive.{ BuildManager, RefinedBuildManager }
import scala.tools.nsc.ast.parser.Scanners
import scala.tools.nsc.io.AbstractFile
import scala.tools.nsc.reporters.{ Reporter }
import scala.tools.nsc.util.Position

import scala.tools.eclipse.properties.PropertyStore
import scala.tools.eclipse.util.{ EclipseFile, EclipseResource, FileUtils, IDESettings, ReflectionUtils, Style } 

class ScalaProject(val underlying : IProject) {
  import ScalaPlugin.plugin

  private var classpathUpdate : Long = IResource.NULL_STAMP
  private var buildManager0 : EclipseBuildManager = null
  private var presentationCompiler0 : ScalaPresentationCompiler = null 
  private var hasBeenBuilt = false
  private val depFile = underlying.getFile(".scala_dependencies")
  
  override def toString = underlying.getName
  
  def buildError(severity : Int, msg : String, monitor : IProgressMonitor) =
    underlying.getWorkspace.run(new IWorkspaceRunnable {
      def run(monitor : IProgressMonitor) = {
        val mrk = underlying.createMarker(plugin.problemMarkerId)
        mrk.setAttribute(IMarker.SEVERITY, severity)
        val string = msg.map{
          case '\n' => ' '
          case '\r' => ' '
          case c => c
        }.mkString("","","")
        mrk.setAttribute(IMarker.MESSAGE , msg)
      }
    }, monitor)
  
  def clearBuildErrors(monitor : IProgressMonitor) =
    underlying.getWorkspace.run(new IWorkspaceRunnable {
      def run(monitor : IProgressMonitor) = {
        underlying.deleteMarkers(plugin.problemMarkerId, true, IResource.DEPTH_ZERO)
      }
    }, monitor)
  
  def externalDepends = underlying.getReferencedProjects 

  def javaProject = {
    if (!underlying.exists())
      underlying.create(null)
    JavaCore.create(underlying)
  }

  def sourceFolders : Iterable[IContainer] = sourceFolders(javaProject)
  
  def sourceFolders(javaProject : IJavaProject) : Seq[IContainer] = sourceFolders(new NameEnvironment(javaProject))
  
  def outputFolders : Seq[IContainer] = outputFolders(new NameEnvironment(javaProject))

  def classpath : Seq[IPath] = {
    val path = new LinkedHashSet[IPath]
    classpath(javaProject, false, path)
    path.toList
  }
  
  private def classpath(javaProject : IJavaProject, exportedOnly : Boolean, path : LinkedHashSet[IPath]) : Unit = {
    val cpes = javaProject.getResolvedClasspath(true)
    
    for (cpe <- cpes if !exportedOnly || cpe.isExported || cpe.getEntryKind == IClasspathEntry.CPE_SOURCE) cpe.getEntryKind match {
      case IClasspathEntry.CPE_PROJECT => 
        val depProject = plugin.workspaceRoot.getProject(cpe.getPath.lastSegment)
        if (JavaProject.hasJavaNature(depProject))
          classpath(JavaCore.create(depProject), true, path)
      case IClasspathEntry.CPE_LIBRARY =>   
        if (cpe.getPath != null) {
          val absPath = plugin.workspaceRoot.findMember(cpe.getPath)
          if (absPath != null)
            path += absPath.getLocation
          else
            path += cpe.getPath
        }
      case IClasspathEntry.CPE_SOURCE =>
        val specificOutputLocation = cpe.getOutputLocation
        val outputLocation =
          if (specificOutputLocation != null)
            specificOutputLocation
            else
              javaProject.getOutputLocation
        if (outputLocation != null) {
          val absPath = plugin.workspaceRoot.findMember(outputLocation)
          if (absPath != null)
            path += absPath.getLocation
        }
    }
  }

  def sourceFolders(env : NameEnvironment) : Seq[IContainer] = {
    val sourceLocations = NameEnvironmentUtils.sourceLocations(env)
    sourceLocations.map(ClasspathLocationUtils.sourceFolder) 
  }

  def outputFolders(env : NameEnvironment) : Seq[IContainer] = {
    val sourceLocations = NameEnvironmentUtils.sourceLocations(env)
    sourceLocations.map(ClasspathLocationUtils.binaryFolder)
  }

  def sourceOutputFolders(env : NameEnvironment) : Seq[(IContainer, IContainer)] = {
    val sourceLocations = NameEnvironmentUtils.sourceLocations(env)
    sourceLocations.map(cl => (ClasspathLocationUtils.sourceFolder(cl), ClasspathLocationUtils.binaryFolder(cl))) 
  }

  def isExcludedFromProject(env : NameEnvironment, childPath : IPath) : Boolean = {
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
  
  def allSourceFiles() : Set[IFile] = allSourceFiles(new NameEnvironment(javaProject))
  
  def allSourceFiles(env : NameEnvironment) : Set[IFile] = {
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
          def visit(proxy : IResourceProxy) : Boolean = {
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
                var folderPath : IPath = null
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
        IResource.NONE
      )
    }
    
    Set.empty ++ sourceFiles
  }
    
  def createOutputFolders = {
    for(cntnr <- outputFolders) cntnr match {
      case fldr : IFolder =>
        def createParentFolder(parent : IContainer) {
          if(!parent.exists()) {
            createParentFolder(parent.getParent)
            parent.asInstanceOf[IFolder].create(true, true, null)
            parent.setDerived(true)
          }
        }
      
        fldr.refreshLocal(IResource.DEPTH_ZERO, null)
        if(!fldr.exists()) {
          createParentFolder(fldr.getParent)
          fldr.create(IResource.FORCE | IResource.DERIVED, true, null)
        }
      case _ => 
    }
  }
  
  def cleanOutputFolders(monitor : IProgressMonitor) = {
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
    
    val outputLocation = javaProject.getOutputLocation
    val resource = plugin.workspaceRoot.findMember(outputLocation)
    resource match {
      case container : IContainer => delete(container, container != javaProject.getProject)(_.endsWith(".class"))
      case _ =>
    }
  }
    
  def checkClasspath : Unit = plugin.check {
    val cp = underlying.getFile(".classpath")
    if (cp.exists)
      classpathUpdate match {
        case IResource.NULL_STAMP => classpathUpdate = cp.getModificationStamp()
        case stamp if stamp == cp.getModificationStamp() => 
        case _ =>
          classpathUpdate = cp.getModificationStamp()
          resetCompilers
      }
  }
  
  def refreshOutput : Unit = {
    val res = plugin.workspaceRoot.findMember(javaProject.getOutputLocation)
    if (res ne null)
      res.refreshLocal(IResource.DEPTH_INFINITE, null)
  }
    
  def initialize(settings : Settings) = {
    val env = new NameEnvironment(javaProject)
    
    for((src, dst) <- sourceOutputFolders(env))
      settings.outputDirs.add(EclipseResource(src), EclipseResource(dst))
      
    // TODO Per-file encodings
    val sfs = sourceFolders
    if (!sfs.isEmpty) {
      settings.encoding.value = sfs.iterator.next.getDefaultCharset
    }

    settings.classpath.value = classpath.toList.map(_.toOSString).mkString("", pathSeparator, "")
    
    settings.deprecation.value = true
    settings.unchecked.value = true

    val workspaceStore = ScalaPlugin.plugin.getPreferenceStore
    val projectStore = new PropertyStore(underlying, workspaceStore, plugin.pluginId)
    val useProjectSettings = projectStore.getBoolean(SettingConverterUtil.USE_PROJECT_SETTINGS_PREFERENCE)
    
    val store = if (useProjectSettings) projectStore else workspaceStore  
    IDESettings.shownSettings(settings).foreach {
      setting =>
        val value = store.getString(SettingConverterUtil.convertNameToProperty(setting.name))
        try {          
          if (value != null)
            setting.tryToSetFromPropertyValue(value)
        } catch {
          case t : Throwable => plugin.logError("Unable to set setting '"+setting.name+"'", t)
        }
    }
  }
  
  def resetCompilers = {
    buildManager0 = null
    hasBeenBuilt = false
    
    if (presentationCompiler0 != null) {
      presentationCompiler0.askShutdown()
      presentationCompiler0 = null
    }
  }
  
  class EclipseBuildManager(settings: Settings) extends RefinedBuildManager(settings) {
    var monitor : IProgressMonitor = _
    
    class EclipseBuilderGlobal(settings: Settings) extends BuilderGlobal(settings) {

      reporter = new Reporter {
        override def info0(pos : Position, msg : String, severity : Severity, force : Boolean) = {
          severity.count += 1
  
          val eclipseSeverity = severity.id match {
            case 2 => IMarker.SEVERITY_ERROR
            case 1 => IMarker.SEVERITY_WARNING
            case 0 => IMarker.SEVERITY_INFO
          }
          
          try {
            if(pos.isDefined) {
              val source = pos.source
              val length = source.identifier(pos, EclipseBuilderGlobal.this).map(_.length).getOrElse(0)
              source.file match {
                case EclipseResource(i : IFile) => FileUtils.buildError(i, eclipseSeverity, msg, pos.point, length, pos.line, null)
                case _ => buildError(eclipseSeverity, msg, null)
              }
            }
            else
              buildError(eclipseSeverity, msg, null)
          } catch {
            case ex : UnsupportedOperationException => 
              buildError(eclipseSeverity, msg, null)
          }
        }
      }
    
      override def newRun() =
        new Run {
          var worked = 0
          
          override def progress(current : Int, total : Int) : Unit = {
            if (monitor != null && monitor.isCanceled) {
              cancel
              return
            }
            
            val newWorked = if (current >= total) 100 else ((current.toDouble/total)*100).toInt
            if (worked < newWorked) {
              if (monitor != null)
                monitor.worked(newWorked-worked)
              worked = newWorked
            }
          }
        
          override def compileLate(file : AbstractFile) = {
            file match {
              case EclipseResource(i : IFile) => FileUtils.clearBuildErrors(i, monitor)
              case _ => 
            }
            super.compileLate(file)
          }
        }
    }
    
    override def newCompiler(settings: Settings) = new EclipseBuilderGlobal(settings)
    
    override def buildingFiles(included: scala.collection.Set[AbstractFile]) {
      for(file <- included) {
        file match {
          case EclipseResource(f : IFile) => FileUtils.clearBuildErrors(f, null)
          case _ =>
        }
      }
    }
  }
  
  def buildManager = {
    checkClasspath
    if (buildManager0 == null) {
      val settings = new Settings
      initialize(settings)
      buildManager0 = new EclipseBuildManager(settings)
    }
    buildManager0
  }

  def presentationCompiler = {
    checkClasspath
    if (presentationCompiler0 eq null) {
      val settings = new Settings
      initialize(settings)
      settings.printtypes.tryToSet(Nil)
      presentationCompiler0 = new ScalaPresentationCompiler(settings)
    }
    presentationCompiler0
  }

  def prepareBuild() : Boolean = {
    if (!hasBeenBuilt) {
      if (!depFile.exists())
        true
      else {
        !buildManager.loadFrom(EclipseResource(depFile), EclipseResource.fromString)
      }
    }
    else
      false
  }
  
  def build(addedOrUpdated : Set[IFile], removed : Set[IFile], monitor : IProgressMonitor) {
    if (addedOrUpdated.isEmpty && removed.isEmpty)
      return
      
    hasBeenBuilt = true

    buildManager.monitor = monitor
    buildManager.update(addedOrUpdated.map(EclipseResource(_)), removed.map(EclipseResource(_)))
    refreshOutput

    buildManager.saveTo(EclipseResource(depFile), _.toString)
    depFile.setDerived(true)
    depFile.refreshLocal(IResource.DEPTH_INFINITE, null)
  }

  def clean(monitor : IProgressMonitor) = {
    underlying.deleteMarkers(plugin.problemMarkerId, true, IResource.DEPTH_INFINITE)
    resetCompilers
    depFile.delete(true, false, monitor)
    cleanOutputFolders(monitor)
  }
}

object NameEnvironmentUtils extends ReflectionUtils {
  val neClazz = classOf[NameEnvironment]
  val sourceLocationsField = getDeclaredField(neClazz, "sourceLocations")
  val binaryLocationsField = getDeclaredField(neClazz, "binaryLocations")
  
  def sourceLocations(env : NameEnvironment) = sourceLocationsField.get(env).asInstanceOf[Array[ClasspathLocation]]
  def binaryLocations(env : NameEnvironment) = binaryLocationsField.get(env).asInstanceOf[Array[ClasspathLocation]]
}

object ClasspathLocationUtils extends ReflectionUtils {
  val cdClazz =  classOf[ClasspathDirectory]
  val binaryFolderField = getDeclaredField(cdClazz, "binaryFolder")
  
  val cpmlClazz = Class.forName("org.eclipse.jdt.internal.core.builder.ClasspathMultiDirectory")
  val sourceFolderField = getDeclaredField(cpmlClazz, "sourceFolder")
  val inclusionPatternsField = getDeclaredField(cpmlClazz, "inclusionPatterns")
  val exclusionPatternsField = getDeclaredField(cpmlClazz, "exclusionPatterns")
                         
  def binaryFolder(cl : ClasspathLocation) = binaryFolderField.get(cl).asInstanceOf[IContainer]
  def sourceFolder(cl : ClasspathLocation) = sourceFolderField.get(cl).asInstanceOf[IContainer]
  def inclusionPatterns(cl : ClasspathLocation) = inclusionPatternsField.get(cl).asInstanceOf[Array[Array[Char]]]
  def exclusionPatterns(cl : ClasspathLocation) = exclusionPatternsField.get(cl).asInstanceOf[Array[Array[Char]]]
}

/*
 * Copyright 2005-2009 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.eclipse

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
import scala.tools.nsc.{ Global, Settings }
import scala.tools.nsc.ast.parser.Scanners
import scala.tools.nsc.io.AbstractFile

import scala.tools.eclipse.properties.PropertyStore
import scala.tools.eclipse.util.{ EclipseFile, EclipseResource, IDESettings, ReflectionUtils, Style } 

class ScalaProject(val underlying : IProject) {
  import ScalaPlugin.plugin

  override def toString = underlying.getName
  
  def buildError0(severity : Int, msg : String, monitor : IProgressMonitor) =
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
  
  
  protected def findFile(path : String) = new ScalaFile(underlying.getFile(path))

  def highlight(sv : ScalaSourceViewer, offset0 : Int, length0 : Int, style0 : Style, txt : TextPresentation) : Unit = {
    if (sv == null || sv.getTextWidget == null || sv.getTextWidget.isDisposed) return
    //val offset = sv.modelOffset2WidgetOffset(offset0)
    //val length = sv.modelOffset2WidgetOffset(offset0 + length0) - offset
    val extent = txt.getExtent
    val offset = offset0
    if (offset >= extent.getOffset + extent.getLength) return
    val length = if (offset + length0 <= extent.getOffset + extent.getLength) length0
                 else return // extent.getOffset + extent.getLength - offset
      
    if (offset == -1 || length <= 0) return
    val range = new StyleRange
    range.length = length
    val style = style0
    range.foreground = style.foreground // could be null
    range.background = style.background 
      
    range.underline = style.underline
    range.strikeout = style.strikeout
    range.fontStyle = (if (style.bold) SWT.BOLD else SWT.NORMAL) |
      (if (style.italics) SWT.ITALIC else SWT.NORMAL)
    range.start = offset
    txt mergeStyleRange range
  }
  
  def hover(file : ScalaFile, offset : Int) : Option[RandomAccessSeq[Char]] = {
    Some("Not yet implemented") // TODO reinstate
  }

  def hyperlink(file : ScalaFile, offset : Int) : Option[IHyperlink] = {
    None // TODO reinstate
  }

  def openAndSelect(file : ScalaFile, select : => (Int,Int)) : Unit = {
    file.doLoad
    val editor =
      if (file.isLoaded)  file.editor.get else { 
        val wb = PlatformUI.getWorkbench
        val page = wb.getActiveWorkbenchWindow.getActivePage
        val e = file.doLoad0(page)
        if (e eq null) {
          plugin.logError("cannot load " + file, null)
          return
        }
        e.asInstanceOf[ITextEditor]
      }
      
    val site = editor.getSite
    val page = site.getPage
    if (!page.isPartVisible(editor)) file.doLoad0(page)
    val (offset,length) = select
    editor.selectAndReveal(offset, length)
  }
  
  def externalDepends = underlying.getReferencedProjects 

  def javaProject = JavaCore.create(underlying)

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
        val depProject = plugin.workspace.getProject(cpe.getPath.lastSegment)
        if (JavaProject.hasJavaNature(depProject))
          classpath(JavaCore.create(depProject), true, path)
      case IClasspathEntry.CPE_LIBRARY =>   
        if (cpe.getPath != null) {
          val absPath = plugin.workspace.findMember(cpe.getPath)
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
          val absPath = plugin.workspace.findMember(outputLocation)
          if (absPath != null)
            path += absPath.getLocation
        }
    }

    /*
    for (pfr <- javaProject.getAllPackageFragmentRoots if pfr.isExternal) {
       val cpe = JavaCore.getResolvedClasspathEntry(pfr.getRawClasspathEntry)
       if (!exportedOnly || cpe.isExported)
         path += cpe.getPath
    }
    */
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
    val resource = plugin.workspace.findMember(outputLocation)
    resource match {
      case container : IContainer => delete(container, container != javaProject.getProject)(_.endsWith(".class"))
      case _ =>
    }
  }
    
  private var classpathUpdate : Long = IResource.NULL_STAMP
  
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
  
  private def nullToOption[T <: AnyRef](x : T) = if (x == null) None else Some(x)
  
  def buildError(file : AbstractFile, severity0 : Int, msg : String, offset : Int, identifier : Int) : Unit =
    nscToLampion(file).buildError({
      severity0 match { //hard coded constants from reporters
        case 2 => IMarker.SEVERITY_ERROR
        case 1 => IMarker.SEVERITY_WARNING
        case 0 => IMarker.SEVERITY_INFO
      }
    }, msg, offset, identifier, null)
  
  def buildError(severity0 : Int, msg : String) = buildError0(severity0, msg, null)
  
  def clearBuildErrors(file : AbstractFile) : Unit  = {
    nscToLampion(file).clearBuildErrors(null)
    clearBuildErrors(null : IProgressMonitor)
  }
  
  def clearBuildErrors() : Unit = clearBuildErrors(null : IProgressMonitor)
  
  def hasBuildErrors(file : AbstractFile) : Boolean = 
    nscToLampion(file).hasBuildErrors

  def projectFor(path : String) : Option[ScalaProject] = {
    val root = ResourcesPlugin.getWorkspace.getRoot.getLocation.toOSString
    if (!path.startsWith(root)) return None
    val path1 = path.substring(root.length)
    
    val res = ResourcesPlugin.getWorkspace.getRoot.findMember(Path.fromOSString(path1))
    plugin.projectSafe(res.getProject)
  }
  
  def refreshOutput : Unit = {
    val res = plugin.workspace.findMember(javaProject.getOutputLocation)
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
      settings.encoding.value = sfs.elements.next.getDefaultCharset
    }

    settings.classpath.value = classpath.toList.map(_.toOSString).mkString("", ":", "")
    
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
  
  def nscToLampion(nscFile : AbstractFile) = new ScalaFile(nscToEclipse(nscFile))
  
  def lampionToNSC(file : ScalaFile) : AbstractFile = EclipseResource(file.underlying)
    
  def nscToEclipse(nscFile : AbstractFile) = nscFile match {
    case ef : EclipseFile => ef.underlying
    case f => println(f.getClass.getName) ; throw new MatchError
  }

  private var buildCompiler0 : BuildCompiler = _
  private var presentationCompiler0 : Global = _ 

  def resetCompilers = {
    buildCompiler0 = null
    presentationCompiler0 = null
  } 

  def buildCompiler = {
    checkClasspath
    if (buildCompiler0 == null) {
      val settings = new Settings
      initialize(settings)
      buildCompiler0 = new BuildCompiler(this, settings)
    }
    buildCompiler0
  }

  def compiler = {
    checkClasspath
    if (presentationCompiler0 eq null) {
      val settings = new Settings
      initialize(settings)
      presentationCompiler0 = new Global(settings) {
        override def logError(msg : String, t : Throwable) =
          plugin.logError(msg, t)
      }
    }
    presentationCompiler0
  }

  def build(toBuild : List[ScalaFile], monitor : IProgressMonitor) = {
    if (!toBuild.isEmpty)
      buildCompiler.build(toBuild.map(lampionToNSC), monitor)
  }

  def clean(monitor : IProgressMonitor) = {
    underlying.deleteMarkers(plugin.problemMarkerId, true, IResource.DEPTH_INFINITE)
    resetCompilers
    cleanOutputFolders(monitor)
  }

  protected def findJava(sym : Global#Symbol) : Option[IJavaElement] = {
    if (sym == compiler.NoSymbol) None
    else if (sym.owner.isPackageClass) {
      val found = javaProject.findType(sym.owner.fullNameString('.'), sym.simpleName.toString, null : IProgressMonitor)
      if (found eq null) None
      else if (sym.isConstructor) {
        val params = sym.info.paramTypes.map(signatureFor(compiler, _)).toArray
        nullToOption(found.getMethod(sym.nameString, params))
      }
      else Some(found)
    } else {
      findJava(sym.owner) match {
        case Some(owner : IType) =>
          var ret : IJavaElement = null
          if (sym.isMethod) {
            val params = sym.info.paramTypes.map(signatureFor(compiler, _)).toArray
            val name = if (sym.isConstructor) sym.owner.nameString else sym.nameString 
            val methods = owner.findMethods(owner.getMethod(name, params))
            if ((methods ne null) && methods.length > 0)
              ret = methods(0)
          }
          if (ret == null && sym.isType) ret = owner.getType(sym.nameString)
          if (ret == null) ret = owner.getField(sym.nameString)
          if (ret != null)
            Some(ret)
          else
            None
        case _ => None
      }
    }
  }
  
  def signatureFor(compiler : Global, tpe : Global#Type) : String = {
    import compiler.definitions._
    import org.eclipse.jdt.core.Signature._
    
    def signatureFor0(sym : Global#Symbol) : String = {
      if (sym == ByteClass) return SIG_BYTE
      if (sym == CharClass) return SIG_CHAR
      if (sym == DoubleClass) return SIG_DOUBLE
      if (sym == FloatClass) return SIG_FLOAT
      if (sym == IntClass) return SIG_INT
      if (sym == LongClass) return SIG_LONG
      if (sym == ShortClass) return SIG_SHORT
      if (sym == BooleanClass) return SIG_BOOLEAN
      if (sym == UnitClass) return SIG_VOID
      if (sym == AnyClass) return "Ljava.lang.Object;"
      if (sym == AnyRefClass) return "Ljava.lang.Object;"
      return 'L' + sym.fullNameString.replace('/', '.') + ';'
    }
    
    tpe match {
      case tpe : Global#PolyType if tpe.typeParams.length == 1 && tpe.resultType == ArrayClass.tpe => 
        "[" + signatureFor0(tpe.typeParams(0))
      case tpe : Global#PolyType => signatureFor(compiler, tpe.resultType) 
      case tpe => signatureFor0(tpe.typeSymbol)  
    }
  }
  
  trait IdeRef {
    def hyperlink : Unit
    def hover : Option[RandomAccessSeq[Char]]
    def symbol : Option[Global#Symbol]
  }

  case object NoRef extends IdeRef {
    def hyperlink : Unit = {}
    def hover : Option[RandomAccessSeq[Char]] = None
    override def symbol = None
  }    
  
  private case class JavaRef(elem : IJavaElement, symbol0 : Global#Symbol) extends IdeRef {
    override def hover = try {
      val str = elem.getAttachedJavadoc(null)
      if (str eq null) None else Some(str)
    } catch {
      case ex => 
        plugin.logError(ex)
        Some("Method added to Java class by Scala compiler.")
    }
    
    override def hyperlink = JavaUI.openInEditor(elem, true, true)
    
    override def symbol = Some(symbol0)
  }
  
  protected def javaRef(symbol : Global#Symbol) : IdeRef = {
    val elem = findJava(symbol) match {
      case Some(elem) => elem
      case None => return NoRef
    }
    JavaRef(elem,symbol)
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

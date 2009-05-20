/*
 * Copyright 2005-2009 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.eclipse

import scala.collection.mutable.{ LinkedHashSet, HashSet }

import java.io.File.pathSeparator

import org.eclipse.core.resources.{ IContainer, IFile, IFolder, IMarker, IProject, IResource, IWorkspaceRunnable, ResourcesPlugin}
import org.eclipse.core.runtime.{ IProgressMonitor, Path }
import org.eclipse.jdt.core.{ IClasspathEntry, IJavaElement, IJavaProject, IPackageFragmentRoot, IType, JavaCore }
import org.eclipse.jdt.internal.core.JavaProject
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
import scala.tools.eclipse.util.{ EclipseFile, EclipseResource, IDESettings, Style } 

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
  
  def sourceFolders(javaProject : IJavaProject) : Iterable[IContainer] = {
    val isOpen = javaProject.isOpen
    if (!isOpen) javaProject.open(null)
    javaProject.getAllPackageFragmentRoots.filter(p =>
      plugin.check(p.getKind == IPackageFragmentRoot.K_SOURCE && p.getResource.isInstanceOf[IContainer] && (p == javaProject || p.getParent == javaProject)) getOrElse false
    ).map(_.getResource.asInstanceOf[IContainer])
  }
  
  def outputPath = plugin.check {
    val outputLocation = javaProject.getOutputLocation
    val cntnr = plugin.workspace.findMember(outputLocation)
    assert(cntnr ne null)
    
    val project = javaProject.getProject
    if (cntnr != project && !ResourcesPlugin.getWorkspace.isTreeLocked) cntnr match {
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

    cntnr.getLocation
  } getOrElse underlying.getLocation
  

  private var classpathUpdate : Long = IResource.NULL_STAMP
  
  def checkClasspath : Unit = plugin.check {
    val cp = underlying.getFile(".classpath")
    if (cp.exists)
      classpathUpdate match {
        case IResource.NULL_STAMP => classpathUpdate = cp.getModificationStamp()
        case stamp if stamp == cp.getModificationStamp() => 
        case _ =>
          classpathUpdate = cp.getModificationStamp()
          resetCompiler
      }
  }
  
  def resetCompiler = {
    buildCompiler = null
    // TODO Also reset the presentation compiler
  } 

  object compiler extends Global(new Settings(null)) with Scanners {
    override val global = this    // For Scanners
    initialize(this)

    override def logError(msg : String, t : Throwable) =
      plugin.logError(msg, t)
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
    res.refreshLocal(IResource.DEPTH_INFINITE, null)
  }
    
  def initialize(global : Global) = {
    val settings = new Settings(null)
    val sfs = sourceFolders
    val sourcePath = sfs.map(_.getLocation.toOSString).mkString("", pathSeparator, "")
    settings.sourcepath.tryToSetFromPropertyValue(sourcePath)
    settings.outdir.tryToSetFromPropertyValue(outputPath.toOSString)
    settings.classpath.tryToSetFromPropertyValue("")     // Is this really needed?
    settings.bootclasspath.tryToSetFromPropertyValue("") // Is this really needed?
    if (!sfs.isEmpty) {
      settings.encoding.value = sfs.elements.next.getDefaultCharset
    }
    settings.deprecation.value = true
    settings.unchecked.value = true
    //First check whether to use preferences or properties
    //TODO - should we rely on ScalaPlugin?  Well.. we need these preferences...
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
    global.settings = settings
    global.classPath.entries.clear // Is this really needed? Why not filter out from allSettings?
    // setup the classpaths!
    intializePaths(global)
  }
    
  def intializePaths(global : nsc.Global) = {
    val sfs = sourceFolders
    val sourcePath = sfs.map(_.getLocation.toOSString).mkString("", pathSeparator, "")
    global.classPath.output(outputPath.toOSString, sourcePath)
    
    val cps = javaProject.getResolvedClasspath(true)
    cps.foreach(cp =>
      plugin.check {
        cp.getEntryKind match { 
          case IClasspathEntry.CPE_PROJECT => 
            val path = cp.getPath
            val project = plugin.workspace.getProject(path.lastSegment)
            if (JavaProject.hasJavaNature(project)) {
              val p = JavaCore.create(project)
              if (p.getOutputLocation != null) {
                val classes = plugin.resolve(p.getOutputLocation).toOSString
                val sources = sourceFolders(p).map(_.getLocation.toOSString).mkString("", pathSeparator, "")
                global.classPath.library(classes, sources)
              }
              p.getAllPackageFragmentRoots.elements.filter(!_.isExternal).foreach { root =>
                val cp = JavaCore.getResolvedClasspathEntry(root.getRawClasspathEntry)
                if (cp.isExported) {
                  val classes = plugin.resolve(cp.getPath).toOSString
                  val sources = nullToOption(cp.getSourceAttachmentPath).map(p => plugin.resolve(p).toOSString).getOrElse(null)
                  global.classPath.library(classes, sources)
                }
              }
            }
            
          case IClasspathEntry.CPE_LIBRARY =>   
            val classes = plugin.resolve(cp.getPath).toOSString
            val sources = nullToOption(cp.getSourceAttachmentPath).map(p => plugin.resolve(p).toOSString).getOrElse(null)
            global.classPath.library(classes, sources)
            
          case IClasspathEntry.CPE_SOURCE =>  
          case _ => 
    }})
  }
  
  def nscToLampion(nscFile : AbstractFile) = new ScalaFile(nscToEclipse(nscFile))
  
  def nscToEclipse(nscFile : AbstractFile) = nscFile match {
    case ef : EclipseFile => ef.underlying
    case f => println(f.getClass.getName) ; throw new MatchError
  }
  
  def lampionToNSC(file : ScalaFile) : AbstractFile = EclipseResource(file.underlying)
  
  private var buildCompiler : BuildCompiler = _
  
  def build(toBuild : List[ScalaFile], monitor : IProgressMonitor) = {
    checkClasspath
    if (buildCompiler == null) {
      buildCompiler = new BuildCompiler(this) // causes it to initialize.
    }
    
    buildCompiler.build(toBuild.map(lampionToNSC), monitor)
  }

  def clean(monitor : IProgressMonitor) = {
    underlying.deleteMarkers(plugin.problemMarkerId, true, IResource.DEPTH_INFINITE)

    buildCompiler = null // throw out the compiler.
    // delete the class files in bin
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

  protected def findJava(sym : compiler.Symbol) : Option[IJavaElement] = {
    if (sym == compiler.NoSymbol) None
    else if (sym.owner.isPackageClass) {
      val found = javaProject.findType(sym.owner.fullNameString('.'), sym.simpleName.toString, null : IProgressMonitor)
      if (found eq null) None
      else if (sym.isConstructor) {
        val params = sym.info.paramTypes.map(signatureFor).toArray
        nullToOption(found.getMethod(sym.nameString, params))
      }
      else Some(found)
    } else {
      findJava(sym.owner) match {
        case Some(owner : IType) =>
          var ret : IJavaElement = null
          if (sym.isMethod) {
            val params = sym.info.paramTypes.map(signatureFor).toArray
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
  
  def signatureFor(tpe : compiler.Type) : String = {
    import compiler.definitions._
    import org.eclipse.jdt.core.Signature._
    
    def signatureFor0(sym : compiler.Symbol) : String = {
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
      case tpe : compiler.PolyType if tpe.typeParams.length == 1 && tpe.resultType == ArrayClass.tpe => 
        "[" + signatureFor0(tpe.typeParams(0))
      case tpe : compiler.PolyType => signatureFor(tpe.resultType) 
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
  
  private case class JavaRef(elem : IJavaElement, symbol0 : compiler.Symbol) extends IdeRef {
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
  
  protected def javaRef(symbol : compiler.Symbol) : IdeRef = {
    val elem = findJava(symbol) match {
      case Some(elem) => elem
      case None => return NoRef
    }
    JavaRef(elem,symbol)
  }
}

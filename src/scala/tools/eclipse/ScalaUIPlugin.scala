/*
 * Copyright 2005-2008 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.eclipse;

import org.eclipse.core.resources.{ IFile, IResource, IResourceChangeEvent, IResourceDelta, IResourceDeltaVisitor, ResourcesPlugin }
import org.eclipse.core.runtime.{ IAdapterFactory, Platform }
import org.eclipse.core.runtime.content.IContentTypeSettings
import org.eclipse.jdt.core.{ IClassFile, IJavaElement, JavaCore, JavaModelException }
import org.eclipse.jdt.internal.core.util.Util
import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitDocumentProvider
import org.eclipse.jdt.ui.JavaUI
import org.eclipse.jface.text.IDocument
import org.eclipse.ui.{ IWorkbenchPage, IEditorInput, PlatformUI }
import org.eclipse.ui.part.FileEditorInput
import org.eclipse.swt.widgets.Display
import org.osgi.framework.BundleContext

import scala.tools.eclipse.javaelements.{ JDTUtils, ScalaCompilationUnitManager }
import scala.tools.nsc.io.AbstractFile

object ScalaUIPlugin {
  var plugin : ScalaUIPlugin = _ 
}

trait ScalaUIPlugin extends {
  override val OverrideIndicator = "scala.overrideIndicator"  
} with lampion.eclipse.UIPlugin with ScalaPlugin {
  assert(ScalaUIPlugin.plugin == null)
  ScalaUIPlugin.plugin = this
  override def editorId : String = "scala.tools.eclipse.Editor"

  override def start(context : BundleContext) = {
    super.start(context)
    
    ScalaCompilationUnitManager.initCompilationUnits(ResourcesPlugin.getWorkspace)

    val scuAdapter = new IAdapterFactory() {
      override def getAdapterList = Array(classOf[IJavaElement])
      override def getAdapter(adaptableObject : AnyRef, adapterType : Class[_]) : AnyRef = {
        if(adaptableObject.isInstanceOf[FileEditorInput]) {
          val input = adaptableObject.asInstanceOf[FileEditorInput]
          ScalaCompilationUnitManager.getScalaCompilationUnit(input.getFile)
        }
        else 
          null
      }
    }
    
    Platform.getAdapterManager().registerAdapters(scuAdapter, classOf[FileEditorInput])
    
    Platform.getContentTypeManager.
      getContentType(JavaCore.JAVA_SOURCE_CONTENT_TYPE).
        addFileSpec("scala", IContentTypeSettings.FILE_EXTENSION_SPEC)
    Util.resetJavaLikeExtensions

    PlatformUI.getWorkbench.getEditorRegistry.setDefaultEditor("*.scala", editorId)
  }
  
  override def stop(context : BundleContext) = {
    super.stop(context)
  }
  
  override def resourceChanged(event : IResourceChangeEvent) {
    if(event.getType == IResourceChangeEvent.POST_CHANGE) {
      event.getDelta.accept(new IResourceDeltaVisitor {
        def visit(delta : IResourceDelta) : Boolean = {
          delta.getKind match {
            case IResourceDelta.ADDED => {
              delta.getResource match {
                case f : IFile => {
                  if (ScalaCompilationUnitManager.creatingCUisAllowedFor(f)) {
                    ScalaCompilationUnitManager.getScalaCompilationUnit(f)
                    JDTUtils.refreshPackageExplorer
                  }
                }
                case _ =>
              }
            }
            case IResourceDelta.REMOVED => {
              delta.getResource match {
                case f : IFile => {
                  ScalaCompilationUnitManager.removeFileFromModel(f)
                  JDTUtils.refreshPackageExplorer
                }
                case _ =>
              }
            }
            case IResourceDelta.CHANGED => {
              delta.getResource match {
                case f : IFile => {
                  if (ScalaPlugin.isScalaProject(f.getProject) &&
                    (JavaCore.create(f.getProject).isOnClasspath(f))) {
                      projectSafe(f.getProject).get.stale(f.getLocation)
                  }
                }
                case _ =>
              }
            }
            case _ =>
          }
          true
        }
      })
    }

    super.resourceChanged(event)
  }
  
  class DocumentProvider extends CompilationUnitDocumentProvider {
    override def createCompilationUnit(file : IFile) = {
      ScalaCompilationUnitManager.getScalaCompilationUnit(file)
    }
  }
  
  type Project <: ProjectImpl
  trait ProjectImplA extends super[UIPlugin].ProjectImpl
  trait ProjectImplB extends super[ScalaPlugin].ProjectImpl
  trait ProjectImpl extends ProjectImplA with ProjectImplB {
    def self : Project
    type File <: FileImpl
    trait FileImpl extends super[ProjectImplA].FileImpl with super[ProjectImplB].FileImpl {selfX:File=>
      def self : File 
      var outlineTrees0 : List[compiler.Tree] = null
      def outlineTrees = {
        if (outlineTrees0 == null) outlineTrees0 = List(unloadedBody) 
        outlineTrees0
      }
      override def doLoad0(page : IWorkbenchPage) = underlying match {
      case ClassFileSpec(source,clazz) => page.openEditor(new ClassFileInput(project,source,clazz), editorId) 
      case _ => super.doLoad0(page)
      }
      override def parseChanged(node : ParseNode) = {
        super.parseChanged(node)
        //Console.println("PARSE_CHANGED: " + node)
        outlineTrees0 = rootParse.lastTyped
      }
      override  def prepareForEditing = {
        super.prepareForEditing
        outlineTrees0 = rootParse.lastTyped
      }
    }
    override def imageFor(style : Style) : Option[Image] = {
      val middle = style match {
      case x if x == `classStyle` => "class"
      case x if x == `objectStyle` => "object"
      case x if x == `traitStyle` => "trait"
      case x if x == `defStyle` => "defpub"
      case x if x == `varStyle` => "valpub"
      case x if x == `valStyle` => "valpub"
      case x if x == `typeStyle` => "typevariable"
      case _ => return super.imageFor(style)
      }
      return Some(fullIcon("obj16/" + middle + "_obj.gif"))
    }
    
    private case class JavaRef(elem : IJavaElement, symbol0 : compiler.Symbol) extends IdeRef {
      override def hover = try {
        val str = elem.getAttachedJavadoc(null)
        if (str eq null) None
        else Some(str)
      } catch {
      case ex => 
        if (false) logError(ex)
        Some("Method added to Java class by Scala compiler.")
      }
      import org.eclipse.jdt.ui._
      override def hyperlink =
        JavaUI.openInEditor(elem, true, true)
      override def symbol = Some(symbol0)
    }
    override protected def javaRef(symbol : compiler.Symbol) : IdeRef = {
      val elem = findJava(symbol) match {
        case Some(elem) => elem
        case None => return NoRef
      }
      JavaRef(elem,symbol)
    }
  }
  def inputFor(that : AnyRef) : Option[IEditorInput] = that match {
  case that : IClassFile  => 
    scalaSourceFile(that).map{
    case (project,source) => new ClassFileInput(project,source,that)
    }
  case _ => None
  }
  import org.eclipse.jdt.internal.ui.javaeditor._
  import org.eclipse.jdt.internal.ui._
  class ClassFileInput(val project : Project, val source : AbstractFile, val classFile : IClassFile) extends InternalClassFileEditorInput(classFile) with FixedInput {
    assert(source != null)
    override def getAdapter(clazz : java.lang.Class[_]) = clazz match {
    case clazz if clazz == classOf[AbstractFile] => source
    case _ => super.getAdapter(clazz)  
    }
    override def initialize(doc : IDocument) : Unit = {
      if (doc == null || source == null) {
        assert(true)
        assert(true)
        assert(true)
      }
      doc.set(new String(source.toCharArray))
    }
    override def neutralFile = (project.classFile(source,classFile))
    override def createAnnotationModel = {
      (classFile.getAdapter(classOf[IResourceLocator]) match {
      case null => null
      case locator : IResourceLocator =>  locator.getContainingResource(classFile)
      }) match {
      case null => super.createAnnotationModel
      case resource =>
        val model = new ClassFileMarkerAnnotationModel(resource)
        model.setClassFile(classFile)
        model
      }
    }
  }
}

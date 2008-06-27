/*
 * Copyright 2005-2008 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.eclipse;
import org.eclipse.jface.text.{IDocument,Document,Position,Region}
import org.eclipse.ui.{IWorkbenchPage,IEditorInput}
import org.eclipse.jdt.internal.ui.JavaPluginImages
import scala.tools.nsc.io.{AbstractFile,PlainFile,ZipArchive}
import org.eclipse.jdt.internal.debug.ui._
import org.eclipse.jdt.internal.core._
import org.eclipse.jdt.core._
import org.eclipse.jdt.internal.compiler.env._

object ScalaUIPlugin {
  var plugin : ScalaUIPlugin = _ 
}

trait ScalaUIPlugin extends {
  override val OverrideIndicator = "scala.overrideIndicator"  
} with lampion.eclipse.UIPlugin with ScalaPlugin {
  assert(ScalaUIPlugin.plugin == null)
  ScalaUIPlugin.plugin = this
  override def editorId : String = "scala.tools.eclipse.Editor"

  type Project <: ProjectImpl
  trait ProjectImplA extends super[UIPlugin].ProjectImpl
  trait ProjectImplB extends super[ScalaPlugin].ProjectImpl
  trait ProjectImpl extends ProjectImplA with ProjectImplB {
    def self : Project
    type File <: FileImpl
    trait FileImpl extends super[ProjectImplA].FileImpl with super[ProjectImplB].FileImpl {selfX:File=>
      def self : File
      override def doLoad0(page : IWorkbenchPage) = underlying match {
      case ClassFileSpec(source,clazz) => page.openEditor(new ClassFileInput(project,source,clazz), editorId)
      case _ => super.doLoad0(page)
      }
      // Miles: edit this code to re-synch the outline during editing. 
      override def parseChanged(node : ParseNode) = {
        super.parseChanged(node)
        Console.println("PARSE_CHANGED: " + node)
      }
      // Miles: edit this code to manage the transition to editing
      override  def prepareForEditing = {
        super.prepareForEditing
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
      override def hyperlink = JavaUI.openInEditor(elem, true, true)
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


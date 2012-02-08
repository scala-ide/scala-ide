package scala.tools.eclipse.launching

import org.eclipse.debug.ui.ILaunchShortcut
import org.eclipse.jface.viewers.ISelection
import org.eclipse.ui.IEditorPart
import org.eclipse.jdt.core.IType
import org.eclipse.jdt.core.ITypeHierarchy
import org.eclipse.core.runtime.IAdaptable
import scala.tools.eclipse.javaelements.ScalaSourceFile
import org.eclipse.jdt.core.IJavaElement
import scala.tools.eclipse.javaelements.ScalaClassElement
import org.eclipse.jface.viewers.ISelectionProvider
import org.eclipse.jdt.core.ITypeRoot
import org.eclipse.jface.text.ITextSelection
import org.eclipse.jdt.internal.ui.actions.SelectionConverter
import org.eclipse.jdt.ui.JavaUI
import org.eclipse.ui.IFileEditorInput
import scala.tools.eclipse.ScalaPlugin
import scala.tools.eclipse.javaelements.ScalaElement
import scala.reflect.generic.Trees
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.tools.nsc.util.OffsetPosition
import scala.tools.eclipse.javaelements.ScalaClassElement
import scala.annotation.tailrec
import scala.tools.nsc.util.Position
import scala.tools.nsc.util.Position$
//import org.scalatest.tools.Runner
import org.scalatest.spi.location.AstNode

class ScalaTestLaunchShortcut extends ILaunchShortcut {
  
  trait TreeSupport {
  
    type Tree = Trees#Tree
  
    def getParent(className: String, root: Tree, node: Tree): AstNode = {
      val parentTreeOpt = getParentTree(root, node)
      parentTreeOpt match {
        case Some(parentTree) =>
          transformAst(className, parentTree, root).getOrElse(null)
        case None => 
          null
      }
    }
  
    def getChildren(className: String, root: Tree, node: Tree): Array[AstNode] = {
      val children: List[Tree] = node match {
        case apply: Trees#Apply =>
          if (apply.children.length > 0)
            apply.children.last.children
          else
            List.empty
        case _ =>
          node.children
      }
      children.map(mapAst(className, _, root)).filter(_.isDefined).map(_.get).toArray
    }
  }
  
  class ConstructorBlock(pClassName: String, rootTree: Trees#Tree, nodeTree: Trees#Tree) 
    extends org.scalatest.spi.location.ConstructorBlock(pClassName, Array.empty) with TreeSupport {
    override lazy val children = {
      val rawChildren = getChildren(pClassName, rootTree, nodeTree).toList
      // Remove the primary constructor method definition.
      rawChildren match {
        case primary :: rest => 
          if (primary.isInstanceOf[MethodDefinition] && primary.name == "this")
            rest.toArray
          else
            rawChildren.toArray
        case _ =>
          rawChildren.toArray
      }
    }
  }

  class MethodDefinition(
    pClassName: String,
    rootTree: Trees#Tree,
    nodeTree: Trees#Tree,
    pName: String, 
    pParamTypes: String*)
    extends org.scalatest.spi.location.MethodDefinition(pClassName, null, Array.empty, pName, pParamTypes.toList: _*) with TreeSupport {
    override lazy val parent = getParent(pClassName, rootTree, nodeTree)
    override lazy val children = getChildren(pClassName, rootTree, nodeTree)
  }
  
  class MethodInvocation(
    pClassName: String,
    pTarget: AstNode, 
    rootTree: Trees#Tree,
    nodeTree: Trees#Tree,
    pName: String, 
    pArgs: AstNode*)
    extends org.scalatest.spi.location.MethodInvocation(pClassName, pTarget, null, Array.empty, pName, pArgs.toList: _*) with TreeSupport {
    override lazy val parent = getParent(pClassName, rootTree, nodeTree)
    override lazy val children = getChildren(pClassName, rootTree, nodeTree)
  }
  
  class StringLiteral(pClassName: String, rootTree: Trees#Tree, nodeTree: Trees#Tree, pValue: String)
    extends org.scalatest.spi.location.StringLiteral(pClassName, null, pValue) with TreeSupport {
    override lazy val parent = getParent(pClassName, rootTree, nodeTree)
  }
  
  class ToStringTarget(pClassName: String, rootTree: Trees#Tree, nodeTree: Trees#Tree, target: AnyRef) 
    extends org.scalatest.spi.location.ToStringTarget(pClassName, null, Array.empty, target) with TreeSupport {
    override lazy val parent = getParent(pClassName, rootTree, nodeTree)
    override lazy val children = getChildren(pClassName, rootTree, nodeTree)
  }
  
  type Tree = Trees#Tree
  
  def launch(selection:ISelection, mode:String) {
    // This get called when user right-clicked .scala file on package navigator and choose 'Run As' -> ScalaTest
    // Should just run all suites within the selected file.
  }
  
  def launch(editorPart:IEditorPart, mode:String) {
    // This get called when user right-clicked within the opened file editor and choose 'Run As' -> ScalaTest
    val element = resolveSelectedAst(editorPart, JavaUI.getEditorInputTypeRoot(editorPart.getEditorInput()))
    if(element == null)
      println("#####Launch all suites within the source file")
    else {
      val children = element.children
      println("#####Launch selected element: " + element.getClass.getName + ", children: " + children.map(_.getClass.getName).mkString(", "))
    }
    //Runner.run(Array("-o"));
  }
  
  @tailrec
  private def getClassElement(element: IJavaElement): ScalaClassElement = {
    element match {
      case scClassElement: ScalaClassElement => 
        scClassElement
      case _ =>
        if (element.getParent != null)
          getClassElement(element.getParent)
        else
          null
    }
  }
  
  @tailrec
  final def getParentTree(candidate: Tree, node: Tree): Option[Tree] = {
    val foundOpt = candidate.children.find(c => c == node)
    foundOpt match {
      case Some(a) =>
        Some(candidate)
      case _ =>
        val nextCandidateOpt = candidate.children.find {c => 
          // Why the following does not compile?  value includes is not a member of scala.reflect.generic.Trees#Position
          // c.pos includes node.pos
          // These are ugly but does compile and work
          val pos = c.pos.asInstanceOf[scala.tools.nsc.util.Position]
          pos.includes(node.pos.asInstanceOf[scala.tools.nsc.util.Position])
        }
        nextCandidateOpt match {
          case Some(nextCandidate) => 
            getParentTree(nextCandidate, node)
          case None => 
            None
        }
    }
  }
   
  private def getTarget(className: String, nodeTree: Tree, rootTree: Tree, apply: Trees#Apply): AstNode = {
    apply.fun match {
      case select: Trees#Select => 
        val q = select.qualifier
        select.qualifier match {
          case lit: Trees#Literal =>
            new ToStringTarget(className, rootTree, nodeTree, lit.value.stringValue)
          case impl: scala.tools.nsc.ast.Trees$ApplyImplicitView => 
            val implFirstArg: Tree = impl.args(0)
            implFirstArg match {
              case litArg: Trees#Literal =>
                new ToStringTarget(className, rootTree, nodeTree, litArg.value.stringValue)
              case _ => 
                new ToStringTarget(className, rootTree, nodeTree, implFirstArg.toString)
            }
          case _ =>
            new ToStringTarget(className, rootTree, nodeTree, select.qualifier.toString)
        }
      case _ =>
        new ToStringTarget(className, rootTree, nodeTree, apply.fun.toString)
    }
  }
  
  private def mapAst(className: String, selectedTree: Tree, rootTree: Tree): Option[AstNode] = {    
    selectedTree match {
      case defDef: Trees#DefDef =>
        val defDefSym = defDef.symbol
        //This does not work?  Error: value paramsType is not a member of Symbols.this.Type
        //println("#####param types: " + defDefSym.info.paramTypes)
        Some(new MethodDefinition(className, rootTree, selectedTree, defDefSym.decodedName, defDef.vparamss.flatten.toList.map(valDef => valDef.tpt.symbol.fullName): _*))
      case apply: Trees#Apply =>
        val target = getTarget(className, selectedTree, rootTree, apply)
        val name = apply.symbol.decodedName
        val args = apply.args.map(arg => arg match {
          case lit: Trees#Literal =>
            new StringLiteral(className, rootTree, selectedTree, lit.value.stringValue)
          case _ =>
            new ToStringTarget(className, rootTree, selectedTree, arg.toString)
        })
        Some(new MethodInvocation(className, target, rootTree, selectedTree, name, args: _*))
      case template: Trees#Template =>
        Some(new ConstructorBlock(className, rootTree, selectedTree))
      case _ =>
        None
    }
  }
  
  private def transformAst(className: String, selectedTree: Tree, rootTree: Tree): Option[AstNode] = {
    val astNodeOpt = mapAst(className, selectedTree, rootTree)
    println("#####selectedTree: " + selectedTree.getClass.getName + ", astNodeOpt: " + astNodeOpt)
    astNodeOpt match {
      case Some(astNode) => astNodeOpt
      case None => 
        val parentOpt = getParentTree(rootTree, selectedTree)
        parentOpt match {
          case Some(parent) =>
            println("#####parent is: " + parent.getClass.getName)
            transformAst(className, parent, rootTree)
          case None =>
            None
        }
    }
  }
  
  def resolveSelectedAst(editorPart: IEditorPart, typeRoot: ITypeRoot): AstNode = {
    val selectionProvider:ISelectionProvider = editorPart.getSite().getSelectionProvider()
    if(selectionProvider == null)
      return null
    val selection:ISelection = selectionProvider.getSelection()
    
    if(!selection.isInstanceOf[ITextSelection])
      return null
    else {
      val textSelection:ITextSelection = selection.asInstanceOf[ITextSelection]
      val element = SelectionConverter.getElementAtOffset(typeRoot, selection.asInstanceOf[ITextSelection])
      element match {
        case scElement: ScalaElement => 
          val classElement = getClassElement(element)
          println("#####scElement: " + scElement.getClass.getName + ", children count: " + scElement.getChildren.length)
          val project = editorPart.getEditorInput.asInstanceOf[IFileEditorInput].getFile.getProject
          val scProject = ScalaPlugin.plugin.getScalaProject(project)
          scProject.withPresentationCompiler { compiler =>
            val scu = scElement.getCompilationUnit.asInstanceOf[ScalaCompilationUnit]
            
            val classPosition = new OffsetPosition(scu.createSourceFile, classElement.getSourceRange.getOffset)
            val rootTree = compiler.locateTree(classPosition)
            println(rootTree.symbol.info.baseClasses)
            
            val position = new OffsetPosition(scu.createSourceFile, textSelection.getOffset)
            val selectedTree = compiler.locateTree(position)
            transformAst(classElement.getFullyQualifiedName, selectedTree, rootTree).getOrElse(null)
          } (null)
        case _ =>
          return null
      }
    }
  }
}

object ScalaTestLaunchShortcut {
  def getScalaTestSuites(element: AnyRef):List[IType] = {
    def isScalaTestSuite(iType: IType): Boolean = {
      val typeHier:ITypeHierarchy = iType.newSupertypeHierarchy(null)
      val superTypeArr:Array[IType] = typeHier.getAllSupertypes(iType)
      superTypeArr.findIndexOf {superType => superType.getFullyQualifiedName == "org.scalatest.Suite"} >= 0
    }
    
    val je = element.asInstanceOf[IAdaptable].getAdapter(classOf[IJavaElement]).asInstanceOf[IJavaElement]
    je.getOpenable match {
      case scu: ScalaSourceFile => 
        val ts = scu.getAllTypes()
        ts.filter {tpe => 
          tpe.isInstanceOf[ScalaClassElement] && isScalaTestSuite(tpe)
        }.toList
    }
  }
}
package scala.tools.eclipse.launching

import scala.tools.eclipse.ScalaPresentationCompiler
import org.scalatest.spi.location.AstNode
import scala.annotation.tailrec
import org.eclipse.jdt.core.IJavaElement
import scala.tools.eclipse.javaelements.ScalaClassElement
import scala.tools.eclipse.javaelements.ScalaElement
import scala.tools.eclipse.ScalaPlugin
import org.eclipse.core.resources.IProject
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.tools.nsc.util.OffsetPosition
import org.scalatest.Style
import org.eclipse.jface.text.ITextSelection
import org.scalatest.spi.location.Selection

class ScalaTestFinder(val compiler: ScalaPresentationCompiler, loader: ClassLoader) {
  
  import compiler._

  trait TreeSupport {
  
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
      val children = node match {
        case apply: Apply =>
          apply.fun match {
            case funApply: Apply => 
              if (apply.children.length > 0 && apply.children.last.isInstanceOf[Block]) 
                apply.children.last.children
              else
                apply.children
            case funSelect: Select =>
              val applyParentOpt = getParentTree(root, funSelect)
              applyParentOpt match {
                case Some(applyParent) => 
                  if (applyParent.children.length > 0 && applyParent.children.last.isInstanceOf[Block])
                    applyParent.children.last.children
                  else
                    node.children
                case None =>
                  node.children
              }              
            case _ =>
              node.children
          }
        case _ =>
          node.children
      }
      children.map(mapAst(className, _, root)).filter(_.isDefined).map(_.get).toArray
    }
  }
  
  class ConstructorBlock(pClassName: String, rootTree: Tree, nodeTree: Tree) 
    extends org.scalatest.spi.location.ConstructorBlock(pClassName, Array.empty) with TreeSupport {
    override lazy val children = {
      val rawChildren = getChildren(pClassName, rootTree, nodeTree).toList
      // Remove the primary constructor method definition.
      rawChildren match {
        case primary :: rest => 
          primary match {
            case MethodDefinition(_, _, _, "this", _) =>
              rest.toArray
            case _ =>
              rawChildren.toArray
          }
        case _ =>
          rawChildren.toArray
      }
    }
  }

  case class MethodDefinition(
    pClassName: String,
    rootTree: Tree,
    nodeTree: Tree,
    pName: String, 
    pParamTypes: String*)
    extends org.scalatest.spi.location.MethodDefinition(pClassName, null, Array.empty, pName, pParamTypes.toList: _*) with TreeSupport {
    override def getParent() = getParent(pClassName, rootTree, nodeTree)
    override lazy val children = getChildren(pClassName, rootTree, nodeTree)
  }
  
  case class MethodInvocation(
    pClassName: String,
    pTarget: AstNode, 
    rootTree: Tree,
    nodeTree: Tree,
    pName: String, 
    pArgs: AstNode*)
    extends org.scalatest.spi.location.MethodInvocation(pClassName, pTarget, null, Array.empty, pName, pArgs.toList: _*) with TreeSupport {
    override def getParent() = getParent(pClassName, rootTree, nodeTree)
    override lazy val children = getChildren(pClassName, rootTree, nodeTree)
  }
  
  case class StringLiteral(pClassName: String, rootTree: Tree, nodeTree: Tree, pValue: String)
    extends org.scalatest.spi.location.StringLiteral(pClassName, null, pValue) with TreeSupport {
    override def getParent() = getParent(pClassName, rootTree, nodeTree)
  }
  
  case class ToStringTarget(pClassName: String, rootTree: Tree, nodeTree: Tree, pTarget: AnyRef) 
    extends org.scalatest.spi.location.ToStringTarget(pClassName, null, Array.empty, pTarget) with TreeSupport {
    override def getParent() = getParent(pClassName, rootTree, nodeTree)
    override lazy val children = getChildren(pClassName, rootTree, nodeTree)
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
        val nextCandidateOpt = candidate.children.find(c => c.pos includes node.pos)
        nextCandidateOpt match {
          case Some(nextCandidate) => 
            getParentTree(nextCandidate, node)
          case None => 
            None
        }
    }
  }
   
  private def getTarget(className: String, apply: Apply, rootTree: Tree): AstNode = {
    println("#####Getting target for: " + apply.symbol.decodedName)
    apply.fun match {
      case select: Select => 
        select.qualifier match {
          case lit: Literal =>
            println("#####target is a literal")
            new ToStringTarget(className, rootTree, apply, lit.value.stringValue)
          case impl: ApplyImplicitView => 
            println("#####target is a apply implicit view")
            val implFirstArg: Tree = impl.args(0)
            implFirstArg match {
              case litArg: Literal =>
                new ToStringTarget(className, rootTree, apply, litArg.value.stringValue)
              case implArgs: ApplyToImplicitArgs =>
                val implArgsFun: Tree = implArgs.fun
                implArgsFun match  {
                  case implArgsApply: Apply =>
                    mapApplyToMethodInvocation(className, implArgsApply, rootTree)
                  case _ =>
                    new ToStringTarget(className, rootTree, apply, implArgs.fun)
                }
              case _ => 
                new ToStringTarget(className, rootTree, apply, implFirstArg.toString)
            }
          case apply: Apply =>
            println("#####target is a apply")
            mapApplyToMethodInvocation(className, apply, rootTree)
          case _ =>
            println("#####target is a something else, which is: " + select.qualifier.getClass.getName)
            new ToStringTarget(className, rootTree, apply, select.name)
        }
      case _ =>
        new ToStringTarget(className, rootTree, apply, apply.fun.toString)
    }
  }
  
  private def mapApplyToMethodInvocation(className: String, apply: Apply, rootTree: Tree): MethodInvocation = {
    val target = getTarget(className, apply, rootTree)
    val name = apply.symbol.decodedName
    val rawArgs = if (apply.fun.hasSymbol) apply.args else apply.fun.asInstanceOf[Apply].args
    val args = rawArgs.map(arg => arg match {
      case lit: Literal =>
        new StringLiteral(className, rootTree, apply, lit.value.stringValue)
      case _ =>
        new ToStringTarget(className, rootTree, apply, arg.toString)
    })
    new MethodInvocation(className, target, rootTree, apply, name, args: _*)
  }
  
  private def mapAst(className: String, selectedTree: Tree, rootTree: Tree): Option[AstNode] = {    
    selectedTree match {
      case defDef: DefDef =>
        val defDefSym = defDef.symbol
        //println("#####param types: " + defDefSym.info.paramTypes.map(t => t.typeSymbol.fullName))
        //println("$$$$$param types: " + defDef.vparamss.flatten.toList.map(valDef => valDef.tpt.symbol.fullName))
        Some(new MethodDefinition(className, rootTree, selectedTree, defDefSym.decodedName, defDefSym.info.paramTypes.map(t => t.typeSymbol.fullName): _*))
      case applyImplicitView: ApplyImplicitView =>
        None
      case apply: Apply =>
        Some(mapApplyToMethodInvocation(className, apply, rootTree))
      case template: Template =>
        Some(new ConstructorBlock(className, rootTree, selectedTree))
      case _ =>
        None
    }
  }
  
  @tailrec
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
  
  def find(textSelection: ITextSelection, element: IJavaElement) = {
    element match {
      case scElement: ScalaElement => 
        val classElement = getClassElement(element)
        println("#####scElement: " + scElement.getClass.getName + ", children count: " + scElement.getChildren.length)
        val scu = scElement.getCompilationUnit.asInstanceOf[ScalaCompilationUnit]
          
        val classPosition = new OffsetPosition(scu.createSourceFile, classElement.getSourceRange.getOffset)
        val rootTree = compiler.locateTree(classPosition)
            
        val linearizedBaseClasses = rootTree.symbol.info.baseClasses
        val styleAnnotatedBaseClassOpt = linearizedBaseClasses.find(baseClass => baseClass.annotations.exists(aInfo => aInfo.atp.toString == "org.scalatest.Style"))
        styleAnnotatedBaseClassOpt match {
          case Some(styleAnnotattedBaseClass) => 
            /*val suiteClass = Class.forName(styleAnnotattedBaseClass.info.typeSymbol.fullName)
            val style = suiteClass.getAnnotation(classOf[Style])
            val finderClass = style.value
            val finder = finderClass.newInstance*/
            
            val suiteClass: Class[_] = loader.loadClass(styleAnnotattedBaseClass.info.typeSymbol.fullName)
            //val styleClass: Class[_ <: java.lang.annotation.Annotation] = loader.loadClass("org.scalatest.Style").asInstanceOf[Class[_ <: java.lang.annotation.Annotation]]
            //val style = suiteClass.getAnnotation(styleClass)
            val styleAnnotation = suiteClass.getAnnotations.find(annt => annt.annotationType.getName == "org.scalatest.Style").get
            val valueMethod = styleAnnotation.annotationType.getMethod("value")
            val finderClass = valueMethod.invoke(styleAnnotation).asInstanceOf[Class[_]]
            val finder = finderClass.newInstance
                
            val position = new OffsetPosition(scu.createSourceFile, textSelection.getOffset)
            val selectedTree = compiler.locateTree(position)

            val scalatestAstOpt = transformAst(classElement.getFullyQualifiedName, selectedTree, rootTree)
            scalatestAstOpt match {
              case Some(scalatestAst) => 
                //finder.find(scalatestAst)
                val findMethod = finder.getClass.getMethods.find { mtd =>
                  mtd.getName == "find" && mtd.getParameterTypes.length == 1 && mtd.getParameterTypes()(0).getName == "org.scalatest.spi.location.AstNode"
                }.get
                findMethod.invoke(finder, scalatestAst).asInstanceOf[Option[Selection]]
              case None => 
                None
            }
          case None =>
            println("#####Base class not found")
            None
        }
      case _ =>
        None
    }
  }
}
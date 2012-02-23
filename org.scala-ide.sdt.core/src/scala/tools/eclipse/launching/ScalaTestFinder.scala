package scala.tools.eclipse.launching

import scala.tools.eclipse.ScalaPresentationCompiler
import org.scalatest.finders.AstNode
import scala.annotation.tailrec
import org.eclipse.jdt.core.IJavaElement
import scala.tools.eclipse.javaelements.ScalaClassElement
import scala.tools.eclipse.javaelements.ScalaElement
import scala.tools.eclipse.ScalaPlugin
import org.eclipse.core.resources.IProject
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.tools.nsc.util.OffsetPosition
import org.eclipse.jface.text.ITextSelection
import org.scalatest.finders.Selection

class ScalaTestFinder(val compiler: ScalaPresentationCompiler, loader: ClassLoader) {
  
  import compiler._

  trait TreeSupport {
  
    def getParent(className: String, root: Tree, node: Tree): AstNode = {
      val parentTreeOpt = getParentTree(root, node)
      parentTreeOpt match {
        case Some(parentTree) => {
          val skippedParentTree = skipApplyToImplicit(parentTree, root)
          transformAst(className, skippedParentTree, root).getOrElse(null)
        }
        case None => 
          null
      }
    }
    
    @tailrec
    private def skipApplyToImplicit(nodeTree: Tree, rootTree: Tree): Tree = {
      nodeTree match {
        case _: ApplyToImplicitArgs | 
             _: ApplyImplicitView =>  
          val nextParentOpt = getParentTree(rootTree, nodeTree)
          nextParentOpt match {
            case Some(nextParent) => 
              skipApplyToImplicit(nextParent, rootTree)
            case None => 
              nodeTree
          }
        case _ =>
          nodeTree
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
  
  private case class ConstructorBlock(pClassName: String, rootTree: Tree, nodeTree: Tree) 
    extends org.scalatest.finders.ConstructorBlock(pClassName, Array.empty) with TreeSupport {
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
    override def equals(other: Any) = if (other != null && other.isInstanceOf[ConstructorBlock]) nodeTree eq other.asInstanceOf[ConstructorBlock].nodeTree else false 
    override def hashCode = nodeTree.hashCode
  }

  private case class MethodDefinition(
    pClassName: String,
    rootTree: Tree,
    nodeTree: Tree,
    pName: String, 
    pParamTypes: String*)
    extends org.scalatest.finders.MethodDefinition(pClassName, null, Array.empty, pName, pParamTypes.toList: _*) with TreeSupport {
    override def getParent() = getParent(pClassName, rootTree, nodeTree)
    override lazy val children = getChildren(pClassName, rootTree, nodeTree)
    override def equals(other: Any) = if (other != null && other.isInstanceOf[MethodDefinition]) nodeTree eq other.asInstanceOf[MethodDefinition].nodeTree else false
    override def hashCode = nodeTree.hashCode
  }
  
  private case class MethodInvocation(
    pClassName: String,
    pTarget: AstNode, 
    rootTree: Tree,
    nodeTree: Tree,
    pName: String, 
    pArgs: AstNode*)
    extends org.scalatest.finders.MethodInvocation(pClassName, pTarget, null, Array.empty, pName, pArgs.toList: _*) with TreeSupport {
    override def getParent() = getParent(pClassName, rootTree, nodeTree)
    override lazy val children = getChildren(pClassName, rootTree, nodeTree)
    override def equals(other: Any) = if (other != null && other.isInstanceOf[MethodInvocation]) nodeTree eq other.asInstanceOf[MethodInvocation].nodeTree else false
    override def hashCode = nodeTree.hashCode
  }
  
  private case class StringLiteral(pClassName: String, rootTree: Tree, nodeTree: Tree, pValue: String)
    extends org.scalatest.finders.StringLiteral(pClassName, null, pValue) with TreeSupport {
    override def getParent() = getParent(pClassName, rootTree, nodeTree)
    override def equals(other: Any) = if (other != null && other.isInstanceOf[StringLiteral]) nodeTree eq other.asInstanceOf[StringLiteral].nodeTree else false
    override def hashCode = nodeTree.hashCode
  }
  
  private case class ToStringTarget(pClassName: String, rootTree: Tree, nodeTree: Tree, pTarget: AnyRef) 
    extends org.scalatest.finders.ToStringTarget(pClassName, null, Array.empty, pTarget) with TreeSupport {
    override def getParent() = getParent(pClassName, rootTree, nodeTree)
    override lazy val children = getChildren(pClassName, rootTree, nodeTree)
    override def equals(other: Any) = if (other != null && other.isInstanceOf[ToStringTarget]) nodeTree eq other.asInstanceOf[ToStringTarget].nodeTree else false
    override def hashCode = nodeTree.hashCode
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
                    new ToStringTarget(className, rootTree, select.qualifier, implArgs.fun)
                }
              case _ => 
                new ToStringTarget(className, rootTree, select.qualifier, implFirstArg.toString)
            }
          case qualifierApply: Apply =>
            println("#####target is a apply")
            mapApplyToMethodInvocation(className, qualifierApply, rootTree)
          case qualiferSelect: Select => 
            println("#####target is a select")
            new ToStringTarget(className, rootTree, qualiferSelect, qualiferSelect.name)
          case _ =>
            println("#####target is a something else, which is: " + select.qualifier.getClass.getName)
            new ToStringTarget(className, rootTree, select.qualifier, select.name)
        }
      case _ =>
        new ToStringTarget(className, rootTree, apply.fun, apply.fun.toString)
    }
  }
  
  private def mapApplyToMethodInvocation(className: String, apply: Apply, rootTree: Tree): MethodInvocation = {
    val target = getTarget(className, apply, rootTree)
    val name = apply.symbol.decodedName
    val rawArgs = if (apply.fun.hasSymbol) apply.args else apply.fun.asInstanceOf[GenericApply].args
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
            val finderClassName = valueMethod.invoke(styleAnnotation).asInstanceOf[String]
            val finderClass = loader.loadClass(finderClassName)
            val finder = finderClass.newInstance
                
            val position = new OffsetPosition(scu.createSourceFile, textSelection.getOffset)
            val selectedTree = compiler.locateTree(position)

            val scalatestAstOpt = transformAst(classElement.getFullyQualifiedName, selectedTree, rootTree)
            scalatestAstOpt match {
              case Some(scalatestAst) => 
                //finder.find(scalatestAst)
                val findMethod = finder.getClass.getMethods.find { mtd =>
                  mtd.getName == "find" && mtd.getParameterTypes.length == 1 && mtd.getParameterTypes()(0).getName == "org.scalatest.finders.AstNode"
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
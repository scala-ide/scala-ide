package org.scalaide.ui.internal.editor

import scala.tools.nsc.Settings
import scala.tools.nsc.interactive.CompilerControl
import scala.tools.nsc.interactive.Global
import scala.tools.nsc.reporters.ConsoleReporter
import scala.reflect.internal.util.BatchSourceFile
import scala.reflect.internal.util.SourceFile
import scala.tools.nsc.interactive.Response
import org.scalaide.core.compiler.IScalaPresentationCompiler
import org.scalaide.logging.HasLogger
import scala.collection.mutable.MutableList

trait HasReturnType {
  private var rt: Option[String] = None
  def returnType = rt
  def returnType_=(rt: Option[String]) = {
    this.rt = rt
  }
}
trait HasModifiers {
  import scala.reflect.internal.Flags._
  private[editor] var flags = 0L
  def setFlags(f: Long) = {
    flags = f
  }
  def isPrivate = (flags & PRIVATE) != 0
  def isProtected = (flags & PROTECTED) != 0
  def isTrait = (flags & TRAIT) != 0
  def isParam = (flags & PARAM) != 0
}
sealed abstract class NodeKey

sealed abstract class Node(val isLeaf: Boolean, val parent:ContainerNode) extends HasModifiers {
  def name: String
  //def parent: ContainerNode
  var start: Int = 0
  var end: Int = 0
  def displayName: String
  def key: NodeKey
  def <= (that:Node):Boolean={
    start= that.start
    end = that.end
    val f = flags
    flags = that.flags
    f != that.flags
  }
}

sealed abstract class ContainerNode(parent: ContainerNode) extends Node(false,parent) {
  //override def parent =p
  protected var _children: Map[NodeKey, Node] =  new scala.collection.immutable.ListMap[NodeKey, Node]()
  def children = _children
  def children_=(ch:Map[NodeKey, Node])= _children =ch
  def addChild(ch: Node) = {
    _children += (ch.key -> ch)
  }
}

case class ClassKey(name: String) extends NodeKey
case class ClassNode(name: String, override val parent: ContainerNode) extends ContainerNode(parent) {
  override def displayName = name
  override def key = ClassKey(name)
}
case class ObjectKey(name: String) extends NodeKey
case class ObjectNode(name: String, override val  parent: ContainerNode) extends ContainerNode(parent) {
  override def displayName =  name
  override def key = ObjectKey(name)
}

case class TraitKey(name: String) extends NodeKey
case class TraitNode(name: String, override val parent: ContainerNode) extends ContainerNode(parent) {
  override def displayName =  name
  override def key = ObjectKey(name)
}

case class PackageKey(name: String) extends NodeKey
case class PackageNode(name: String, override val parent: ContainerNode) extends Node(true, parent) {
  override def displayName =  name
  override def key = PackageKey(name)
}

case class RootNode() extends ContainerNode(null) {
  //override def parent = null
  override def name = "ROOT"
  override def displayName = name
  override def key = null
  def diff(rn:RootNode):(Iterable[Node],Iterable[Node])={
    val toUpdate= new MutableList[Node]
    val toRefresh= new MutableList[Node]
    def visitContainer(cn:ContainerNode, cn1:ContainerNode):Unit={
      var children = collection.immutable.ListMap[NodeKey, Node]()
      var hasNew = false

      cn1.children.foreach(p =>
        cn.children.get(p._1) match {
          case Some(n) =>
             visitNode(n, p._2)
             children += (p._1-> n)
           case None =>
             hasNew =true
             children += (p._1-> p._2)
        }
        )
        if(children.size != cn.children.size)
          hasNew = true
        if(hasNew )
          toUpdate += cn
        cn.children = children
      }

    def visitNode(n:Node, n1:Node)= {
      if(n <= n1)
           toRefresh += n
      n match {
        case cn:ContainerNode =>
          n1 match {
            case cn1:ContainerNode =>
              visitContainer(cn, cn1)
            case _ =>
          }
        case _ =>
      }
    }
    visitContainer(this, rn)
    (toUpdate,toRefresh)
  }
}

case class MethodKey(name: String, pList: List[List[String]] = List()) extends NodeKey
case class MethodNode(name: String, override val parent: ContainerNode, val argTypes: List[List[String]] = List())
    extends Node(true, parent) with HasModifiers with HasReturnType {
  override def displayName = name
  override def key = MethodKey(name, argTypes)
  override def <= (n:Node):Boolean={
    val b =super.<=(n)
    n match {
      case that:MethodNode =>
        val rt= returnType
        returnType=that.returnType
        b || rt != returnType
      case _ => b
    }
  }
}

//case class FieldKey(name: String) extends NodeKey
case class ValNode(name: String, override val parent: ContainerNode, rt: String)
    extends Node(true, parent) with HasModifiers with HasReturnType {

  override def displayName = name
  override def key = MethodKey(name)
  override def <= (n:Node):Boolean={
    val b =super.<=(n)
    n match {
      case that:ValNode =>
        val rt= returnType
        returnType=that.returnType
        b || rt != returnType
      case _ => b
    }
  }
}

case class VarNode(name: String, override val parent: ContainerNode, rt: String)
    extends Node(true, parent) with HasModifiers with HasReturnType {
  override def displayName = name
  override def key = MethodKey(name)
  override def <= (n:Node):Boolean={
    val b =super.<=(n)
    n match {
      case that:VarNode =>
        val rt= returnType
        returnType=that.returnType
        b || rt != returnType
      case _ => b
    }
  }
}
case class TypeKey(name:String) extends NodeKey
case class TypeNode(name:String, override val parent:ContainerNode)
   extends Node(true, parent) with HasModifiers {
  override def displayName = name
  override def key = TypeKey(name)
}

case class ImportNode(name:String, override val parent:ContainerNode)
   extends Node(true, parent) with HasModifiers {
  override def displayName = name
  override def key = MethodKey(name)
}

object ModelBuilder extends HasLogger {

  def buildTree(comp: IScalaPresentationCompiler,src:SourceFile):RootNode={
    import comp._
    import scala.reflect.internal.Flags._
    def setPos(n: Node, t: Tree) = {
      if (t.pos.isDefined) {
        n.end = t.pos.end
        n.start = t.pos.start
      }
    }
    def updateTree(parent: ContainerNode, t: Tree): Unit = {
      t match {
        case Template(_, _, _) => t.children.foreach(x => updateTree(parent, x))
        case PackageDef(pid, stats) => {
          val ch = PackageNode(pid.toString(), parent)
          setPos(ch, t)
          parent.addChild(ch)
          t.children.foreach(x => updateTree(parent, x))
        }
        case ClassDef(mods, name, tpars, templ) => {
          //logger.info("ClassDef "+mods+", "+name)//+", "+tpars+", "+templ )
          val ch = ClassNode(name.toString(), parent)
          setPos(ch, t)
          parent.addChild(ch)
          ch.setFlags(mods.flags)
          t.children.foreach(x => updateTree(ch, x))
        }
        case `noSelfType` =>
        case ValDef(mods, name, tpt, rsh) => {
          val ch =  if ((mods.flags & MUTABLE) == 0)
              ValNode(name.toString(), parent, tpt.toString())
            else
              VarNode(name.toString(), parent, tpt.toString())
          setPos(ch, t)
          ch.setFlags(mods.flags)
          parent.addChild(ch)
        }
        //        case Select(qualifier: Tree, name: Name) => logger.info("Select "+qualifier+", "+name)
        //        case TypeDef(mods: Modifiers, name: TypeName, tparams: List[TypeDef], rhs: Tree) => logger.info("TypeDef "+name)
        //        case AppliedTypeTree(tpt, args) =>logger.info("AppliedTypeTree "+ tpt+", "+args)
        //        case Apply(fun, args) => logger.info("Apply "+ fun)
        case DefDef(mods, name, tparamss, vparamss, tpt, rsh) =>
          if ("<init>" != name.toString) {
            def typeList = {
              vparamss.map { x => x.map { v => v.tpt.toString() } }
            }
            val ch =  MethodNode(name.toString(), parent, typeList)
            //logger.info("DefDef " + name + ", modes=" + mods + ", tpt=" + tpt.hasSymbolField + ", " + rsh)
            ch.returnType = if (tpt.hasSymbolField) Some(tpt.toString) else None
            setPos(ch, t)
            ch.setFlags(mods.flags)
            parent.addChild(ch)
          }

        case ModuleDef(mods, name, _) => {
          //logger.info("ModuleDef "+name)
          val ch =  ObjectNode(name.toString(), parent)
          setPos(ch, t)
          ch.setFlags(mods.flags)
          parent.addChild(ch)
          t.children.foreach(x => updateTree(ch, x))

        }
        case DocDef(comment: DocComment, definition: Tree) => updateTree(parent, definition)
        case TypeDef(mods: Modifiers, name: TypeName, tparams: List[TypeDef], rhs:Tree) =>
          val ch =  TypeNode(name.toString(), parent)
          setPos(ch, t)
          ch.setFlags(mods.flags)
          if(!ch.isParam)
            parent.addChild(ch)
        case Import(expr: Tree, selectors) =>
          logger.info("Import "+expr+"! "+selectors)
        case _ => logger.info("_"+t.getClass)
      }
    }
    val rootNode = new RootNode
    val t = comp.parseTree(src)
    updateTree(rootNode, t)
    rootNode
  }

}



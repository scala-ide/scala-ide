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

/**
 * @author ailinykh
 */



trait HasReturnType { self: Node =>
  private var rt: Option[String] = None
  def returnType = rt
  def returnType_=(rt: Option[String]) = {
    if (this.rt != rt)
      dirty = true
    this.rt = rt
  }
}
trait HasModifiers { self: Node =>
  import scala.reflect.internal.Flags._
  private var flags = 0L
  def setFlags(f: Long) = {
    if (flags != f)
      dirty = true
    flags = f
  }
  def isPrivate = (flags & PRIVATE) != 0
  def isProtected = (flags & PROTECTED) != 0
  def isTrait = (flags & TRAIT) != 0
}
abstract class NodeKey

abstract class Node(val isLeaf: Boolean) extends HasModifiers {
  def name: String
  def parent: ContainerNode
  var visited = false
  private var _start: Int = 0
  private var _end: Int = 0
  var dirty = false
  //def isDirty = dirty
  def start = _start
  def end = _end
  def start_=(pos: Int) = {
    _start = pos
    // dirty = true
  }
  def end_=(pos: Int) = {
    _end = pos
    //dirty = true
  }

  def displayName: String
  def key: NodeKey
  def op(f: Node => Unit) = f(this)
  def clearDirtyFlags() = dirty = false
  //def collect(f:Node => Boolean):List[Node]= if(f(this)) List(this) else List()
}

abstract class ContainerNode(parent: ContainerNode) extends Node(false) {
  var structDirty = false
  protected var children: Map[NodeKey, Node] = collection.immutable.ListMap[NodeKey, Node]()
  def getChildren = children
  def addChild(ch: Node) = {
    children += (ch.key -> ch)
    structDirty = true
  }
  def removeChild(key: NodeKey) = {
    children -= key
    structDirty = true
  }
  override def op(f: Node => Unit) = {
    f(this)
    children.values.foreach(n => n.op(f))
  }
  override def clearDirtyFlags() = {
    dirty = false
    structDirty = false
  }
}

case class ClassKey(name: String) extends NodeKey
case class ClassNode(name: String, parent: ContainerNode) extends ContainerNode(parent) {
  override def displayName = name
  override def key = ClassKey(name)
}
case class ObjectKey(name: String) extends NodeKey
case class ObjectNode(name: String, parent: ContainerNode) extends ContainerNode(parent) {
  override def displayName =  name
  override def key = ObjectKey(name)
}

case class TraitKey(name: String) extends NodeKey
case class TraitNode(name: String, parent: ContainerNode) extends ContainerNode(parent) {
  override def displayName =  name
  override def key = ObjectKey(name)
}

case class PackageKey(name: String) extends NodeKey
case class PackageNode(name: String, parent: ContainerNode) extends Node(true) {
  override def displayName =  name
  override def key = PackageKey(name)
}

case class RootNode() extends ContainerNode(null) {
  override def parent = null
  override def name = "ROOT"
  override def displayName = name
  override def key = null
}

case class MethodKey(name: String, pList: List[List[String]] = List()) extends NodeKey
case class MethodNode(name: String, parent: ContainerNode, val argTypes: List[List[String]] = List())
    extends Node(true) with HasModifiers with HasReturnType {
  override def displayName = name
  override def key = MethodKey(name, argTypes)
}

//case class FieldKey(name: String) extends NodeKey
case class ValNode(name: String, parent: ContainerNode, rt: String)
    extends Node(true) with HasModifiers with HasReturnType {

  override def displayName = name
  override def key = MethodKey(name)
}

case class VarNode(name: String, parent: ContainerNode, rt: String)
    extends Node(true) with HasModifiers with HasReturnType {
  override def displayName = name
  override def key = MethodKey(name)
}

//case class ModelChangedEvent(updatedNodes:Iterable[Node], structureModifiedNodes:Iterable[Node])

class ModelBuilder(comp: IScalaPresentationCompiler) extends HasLogger /*(settings: Settings) extends Global(settings, new ConsoleReporter(settings))*/ {
  import comp._

  def updateTree(rootNode: RootNode, src: SourceFile): (Iterable[Node], Iterable[Node]) = {
    import scala.reflect.internal.Flags._
    rootNode.op(n => n.clearDirtyFlags)
    rootNode.op(n => n.visited = false)
    def setPos(n: Node, t: Tree) = {
      if (t.pos.isDefined) {
        n.end = t.pos.end
        n.start = t.pos.start
      }
      n.visited = true
    }
    def getOrAdd(parent: ContainerNode, key: NodeKey, f: => Node) = {
      parent.getChildren.get(key) match {
        case Some(node) => node
        case None => {
          val ch = f
          parent.addChild(ch)
          ch
        }
      }
    }
    def updateTree(parent: ContainerNode, t: Tree): Unit = {
      t match {
        case Template(_, _, _) => t.children.foreach(x => updateTree(parent, x))
        case PackageDef(pid, stats) => {
          val ch = getOrAdd(parent, PackageKey(pid.toString()), PackageNode(pid.toString(), parent))
          setPos(ch, t)
          t.children.foreach(x => updateTree(parent, x))
        }
        case ClassDef(mods, name, tpars, templ) => {
          //logger.info("ClassDef "+mods+", "+name)//+", "+tpars+", "+templ )
          val ch = getOrAdd(parent, ClassKey(name.toString()), ClassNode(name.toString(), parent))
          setPos(ch, t);
          ch.setFlags(mods.flags)
          t.children.foreach(x => updateTree(ch.asInstanceOf[ContainerNode], x))
        }
        case `noSelfType` =>
        case ValDef(mods, name, tpt, rsh) => {
          val ch = getOrAdd(
            parent,
            MethodKey(name.toString()),
            if ((mods.flags & MUTABLE) == 0)
              ValNode(name.toString(), parent, tpt.toString())
            else
              VarNode(name.toString(), parent, tpt.toString()))
          setPos(ch, t)
          ch.setFlags(mods.flags)
          ch.asInstanceOf[HasModifiers].setFlags(mods.flags)
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
            val ch = getOrAdd(parent, MethodKey(name.toString(), typeList), MethodNode(name.toString(), parent, typeList))
            //logger.info("DefDef " + name + ", modes=" + mods + ", tpt=" + tpt.hasSymbolField + ", " + rsh)
            ch.asInstanceOf[HasReturnType].returnType = if (tpt.hasSymbolField) Some(tpt.toString) else None
            setPos(ch, t)
            ch.setFlags(mods.flags)
          }

        case ModuleDef(_, name, _) => {
          val ch = getOrAdd(parent, ObjectKey(name.toString()), ObjectNode(name.toString(), parent))
          setPos(ch, t)
          t.children.foreach(x => updateTree(ch.asInstanceOf[ContainerNode], x))

        }
        case DocDef(comment: DocComment, definition: Tree) => updateTree(parent, definition)
        case _ => //logger.info("_"+t.getClass)
      }
    }
    val t = comp.parseTree(src)
    //comp.askParsedEntered(src, false).get.left.get
    updateTree(rootNode, t)
    val ml = new scala.collection.mutable.MutableList[Node]()
    rootNode.op(n => {
      if (!n.visited) ml += n
    })

    ml.foreach { x => if (x.parent != null) x.parent.removeChild(x.key) }
    ml.clear()
    rootNode.op {
      case cn: ContainerNode => if (cn.structDirty) ml += cn
      case _ =>
    }
    val refreshList = new scala.collection.mutable.MutableList[Node]()
    rootNode.op { n => if (n.dirty) refreshList += n }
    (ml, refreshList)
  }

  //////////////////////
  def printTree(ident: Int, t: Tree): Unit = {
    def printSymbol(): String = {
      if (t.symbol eq null) "NS<" + t.summaryString
      else
        t.symbol.nameString

    }
    for (i <- 0 to ident) {
      print(" ")
    }

    println(t.getClass.getSimpleName + "(" + printSymbol + "){")

    t.children.foreach(x => printTree(ident + 2, x))
    for (i <- 0 to ident) {
      print(" ")
    }
    println("}")

    /*
    t match {
      case Template(_, _, _) => t.children.foreach(x => printTree(ident + 2, x))
      case PackageDef(pid, stats) => {
        println(pid)
        t.children.foreach(x => printTree(ident + 2, x))
      }
      case ClassDef(mods, name, tpars, templ) => {

        println("Class(" + name + ", mods=" + mods)
        t.children.foreach(x => printTree(ident + 2, x))
      }
      case ValDef(mods, name, tpt, rsh) => {

        println("Val(" + name + ", tpt=" + tpt + ", mods=" + mods)
        t.children.foreach(x => printTree(ident + 2, x))
      }
      case DefDef(mods, name, _, _, _, _) => {
        println("Def(" + name+", flags = "+mods)
        t.children.foreach(x => printTree(ident + 2, x))
      }
      case ModuleDef(mods, name, _) => {
        println("Object(" + name+" mods="+mods)
        t.children.foreach(x => printTree(ident + 2, x))
      }
      case _ => println()
    }
    */
    //println(t.symbol +": ")

  }
}



package org.scalaide.ui.internal.editor.outline
/**
 * ModelBuilder is the "heart" of Outline View. It maps scala source into a tree of nodes (model). Each node
 * represent a scala entity, such as Class, Trait, Method and so on.
 * This tree is rendered by TreeView later. Parsed tree is used to build the model. Which means not all
 * type information is available.
 * The model is built on the reconciler thread. Every time new model is generated it overwrites the previous
 * one. There are two reasons why the model is mutable.
 *
 * 1. Despite of good model builder performance, applying model to TreeView could be expansive. For large file
 * tens of thousands nodes are generated. Each node is translated to UI widget and all widget manipulation
 * is done on UI thread, which may slow down the application.
 *
 * 2. The SWT TreeView has a state- user may expand/close some nodes. If we replace model, that state is lost.
 *
 * Using mutable model allows to update only nodes are modified.
 */
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
  private[outline] var flags = 0L
  def setFlags(f: Long) = {
    flags = f
  }
  def isPrivate = (flags & PRIVATE) != 0
  def isProtected = (flags & PROTECTED) != 0
  def isTrait = (flags & TRAIT) != 0
  def isParam = (flags & PARAM) != 0
  def isSynthetic = (flags & SYNTHETIC) != 0
  def isImplicit = (flags & IMPLICIT) != 0
}
sealed abstract class NodeKey

sealed abstract class Node(val isLeaf: Boolean, val parent: ContainerNode) extends HasModifiers {
  def name: String
  var start: Int = 0
  var end: Int = 0
  def displayName = name
  def key: NodeKey
  /**
   * makes this node equal to source. Returns true if any changes are visible.
   * For example, if user just added a new line, node position (start, end) is different now. But this change is not visible, so
   * no updates should be generated.
   *
   * @param src a node to make equal to
   * @return true if any changes are visible, false otherwise
   */
  def update(src: Node): Boolean = {
    start = src.start
    end = src.end
    val f = flags
    flags = src.flags
    f != src.flags
  }
}

sealed abstract class ContainerNode(parent: ContainerNode) extends Node(false, parent) {
  protected var _children: Map[NodeKey, Node] = new scala.collection.immutable.ListMap[NodeKey, Node]()
  def children = _children
  def children_=(ch: Map[NodeKey, Node]) = _children = ch
  def addChild(ch: Node) = {
    last = Some(ch)
    _children += (ch.key -> ch)
  }
  var last: Option[Node] = None
}

case class ClassKey(name: String) extends NodeKey
case class ClassNode(name: String, override val parent: ContainerNode) extends ContainerNode(parent) {
  override def key = ClassKey(name)
}

case class ObjectKey(name: String) extends NodeKey
case class ObjectNode(name: String, override val parent: ContainerNode) extends ContainerNode(parent) {
  override def key = ObjectKey(name)
}

case class TraitKey(name: String) extends NodeKey
case class TraitNode(name: String, override val parent: ContainerNode) extends ContainerNode(parent) {
  override def key = ObjectKey(name)
}

case class PackageKey(name: String) extends NodeKey
case class PackageNode(name: String, override val parent: ContainerNode) extends Node(true, parent) {
  override def key = PackageKey(name)
}

case class RootNode() extends ContainerNode(null) {
  override def name = "ROOT"
  override def key = null
  /**
   * iterates through whole tree and makes it equal to src.
   * It returns two sets of nodes- with structural changes (some node where added or removed) and content changes.
   *
   * @param src new generated tree
   * @return nodes with structural and content difference.
   */
  def updateAll(src: RootNode): (Iterable[Node], Iterable[Node]) = {
    val toUpdate = new MutableList[Node]
    val toRefresh = new MutableList[Node]
    def visitContainer(cn: ContainerNode, cn1: ContainerNode): Unit = {
      var children = collection.immutable.ListMap[NodeKey, Node]()
      var needUpdate = false

      cn1.children.foreach(p =>
        cn.children.get(p._1) match {
          case Some(n) =>
            if (visitNode(n, p._2))
              needUpdate = true
            children += (p._1 -> n)
          case None =>
            needUpdate = true
            children += (p._1 -> p._2)
        })
      if (children.size != cn.children.size)
        needUpdate = true
      if (needUpdate)
        toUpdate += cn
      cn.children = children
    }

    def visitNode(n: Node, n1: Node): Boolean = {
      val b = n.update(n1)
      if (b)
        toRefresh += n
      n match {
        case cn: ContainerNode =>
          n1 match {
            case cn1: ContainerNode =>
              visitContainer(cn, cn1)
            case _ =>
          }
        case _ =>
      }
      b
    }
    visitContainer(this, src)
    (toUpdate, toRefresh)
  }
}

case class MethodKey(name: String, pList: List[List[String]] = List()) extends NodeKey
case class MethodNode(name: String, override val parent: ContainerNode, val argTypes: List[List[String]] = List())
    extends ContainerNode(parent) with HasModifiers with HasReturnType {
  override def displayName = {
    val sb = new StringBuilder
    def renderArgList() = {
      argTypes.foreach(list => {
        sb.append("(")
        list.foreach { s => sb.append(s); sb.append(", ") }
        if (!list.isEmpty)
          sb.setLength(sb.length - 2)
        sb.append(")")
      })
    }
    sb.append(name)
    renderArgList
    sb.append(returnType.map(": " + _).getOrElse(""))
    sb.toString
  }
  override def key = MethodKey(name, argTypes)
  override def update(n: Node): Boolean = {
    val b = super.update(n)
    n match {
      case that: MethodNode =>
        val rt = returnType
        returnType = that.returnType
        b || rt != returnType
      case _ => b
    }
  }
}

case class ValNode(name: String, override val parent: ContainerNode, rt: Option[String])
    extends Node(true, parent) with HasModifiers with HasReturnType {
  returnType = rt
  override def displayName = name + returnType.map(": " + _).getOrElse("")
  override def key = MethodKey(name)
  override def update(n: Node): Boolean = {
    val b = super.update(n)
    n match {
      case that: ValNode =>
        val rt = returnType
        returnType = that.returnType
        b || rt != returnType
      case _ => b
    }
  }
}

case class VarNode(name: String, override val parent: ContainerNode, rt: Option[String])
    extends Node(true, parent) with HasModifiers with HasReturnType {
  returnType = rt
  override def displayName = name + returnType.map(": " + _).getOrElse("")
  override def key = MethodKey(name)
  override def update(n: Node): Boolean = {
    val b = super.update(n)
    n match {
      case that: VarNode =>
        val rt = returnType
        returnType = that.returnType
        b || rt != returnType
      case _ => b
    }
  }
}
case class TypeKey(name: String) extends NodeKey
case class TypeNode(name: String, override val parent: ContainerNode)
    extends Node(true, parent) with HasModifiers {
  override def displayName = name
  override def key = TypeKey(name)
}

case class ImportNode(name: String, override val parent: ContainerNode)
    extends Node(true, parent) with HasModifiers {
  override def key = ObjectKey(name)
}

case class ImportsNode(override val parent: ContainerNode) extends ContainerNode(parent) {
  override def name = "import declarations"
  override def key = ObjectKey(name)
}

object ModelBuilder  extends HasLogger{
  def buildTree(comp: IScalaPresentationCompiler, src: SourceFile): RootNode = {
    import comp._
    import scala.reflect.internal.Flags._
    def setPos(n: Node, t: Tree) = {
      if (t.pos.isDefined) {
        n.end = t.pos.end
        n.start = t.pos.start
      }
    }

    def updateTree(parent: ContainerNode, t: Tree): Unit = {
      def renderTuple(sb: StringBuilder, args: List[Tree]) = {
        sb.append("(")
        args.foreach(a => { renderType(sb, a); sb.append(", ") })
        if (!args.isEmpty)
          sb.setLength(sb.length - 2)
        sb.append(")")
      }
      def renderFunc(sb: StringBuilder, args: List[Tree]): Unit = {
        args match {
          case a :: b :: tail =>
            renderType(sb, a)
            sb.append(", ")
            renderFunc(sb, b :: tail)
          case a :: Nil =>
            sb.setLength(sb.length - 2)
            sb.append(") => ")
            renderType(sb, a)
          case Nil =>
        }
      }
      def renderATT(sb: StringBuilder, tt: Tree, tpt: Tree, args: List[Tree], dp: Boolean) = {
        val tuple = """scala.Tuple[0-9]+""".r
        val func = """_root_.scala.Function[0-9]+""".r
        tpt.toString match {
          case func(_*) =>
            if (args.length == 2) {
              renderType(sb, args.head, true)
              sb.append(" => ")
              renderType(sb, args.tail.head)
            } else {
              sb.append("(")
              renderFunc(sb, args)
            }
          case tuple(_*) =>
            if (dp)
              sb.append("(")
            renderTuple(sb, args)
            if (dp)
              sb.append(")")
          case "_root_.scala.<byname>" =>
            sb.append("=> ")
            renderType(sb, args.head, dp)
          case _ => sb.append(tt.toString)
        }
      }
      def renderType(sb: StringBuilder, tt: Tree, dp: Boolean = false): Unit = {
        tt match {
          case AppliedTypeTree(tpt: Tree, args: List[Tree]) => renderATT(sb, tt, tpt, args, dp)
          case _ => sb.append(tt.toString())
        }
      }
      def showType(tt: Tree): String = {
        val sb = new StringBuilder
        renderType(sb, tt)
        sb.toString()
      }
      def printTree(tt: Tree): String = {
        def printATT(tpt: Tree, args: List[Tree]) = {
          tpt.toString match {
            case "_root_.scala.Function1" => args.head.toString + "=>" + args.tail.head.toString
            case _ => tpt.toString + args.toString
          }
        }
        tt match {
          case AppliedTypeTree(tpt: Tree, args: List[Tree]) => printATT(tpt, args)
          case ValDef(mods, name, tpt, rsh) => tpt.tpe + ", ValDef, mods=" + mods + ", name=" + name + ", tpt=" + printTree(tpt) + ", rsh" + printTree(rsh)
          case Select(qualifier: Tree, name: Name) => qualifier.tpe + ", Select, qualifier=" + printTree(qualifier) + ", name=" + name.decoded
          case Ident(name: Name) => "Ident, name=" + name.decoded
          case _ => tt.getClass + "! " + tt.toString
        }
      }
      t match {
        case Template(_, _, _) => t.children.foreach(x => updateTree(parent, x))
        case PackageDef(pid, stats) => {
          val ch = PackageNode(pid.toString(), parent)
          setPos(ch, t)
          parent.addChild(ch)
          t.children.foreach(x => updateTree(parent, x))
        }

        case ClassDef(mods, name, tpars, templ) => {
          val ch = ClassNode(name.decodedName.toString(), parent)
          setPos(ch, t)
          parent.addChild(ch)
          ch.setFlags(mods.flags)
          t.children.foreach(x => updateTree(ch, x))
        }

        case `noSelfType` =>

        case ValDef(mods, name, tpt, rsh) => {
          val ch = if ((mods.flags & MUTABLE) == 0)
            ValNode(name.decodedName.toString(), parent, if (tpt.isEmpty) None else Some(showType(tpt)))
          else
            VarNode(name.decodedName.toString(), parent, if (tpt.isEmpty) None else Some(showType(tpt)))
          setPos(ch, t)
          ch.setFlags(mods.flags)
          parent.addChild(ch)
        }

        case DefDef(mods, name, tparamss, vparamss, tpt, rsh) =>
          def typeList = {
            vparamss.map { x =>
              {
                if (!x.isEmpty) {
                  val h = x.head
                  val prefix: String = if ((h.mods.flags & IMPLICIT) != 0) "implicit " else ""
                  prefix + h.name + ": " + showType(h.tpt) :: x.tail.map(v => v.name + ": " + showType(v.tpt))
                } else
                  List()
              }
            }

          }
          val ch = MethodNode(name.decodedName.toString(), parent, typeList)
          ch.returnType = if (!tpt.isEmpty) Some(showType(tpt)) else None
          setPos(ch, t)
          ch.setFlags(mods.flags)
          parent.addChild(ch)
          updateTree(ch, rsh)

        case ModuleDef(mods, name, _) => {
          val ch = ObjectNode(name.decodedName.toString(), parent)
          setPos(ch, t)
          ch.setFlags(mods.flags)
          parent.addChild(ch)
          t.children.foreach(x => updateTree(ch, x))
        }

        case DocDef(comment: DocComment, definition: Tree) => updateTree(parent, definition)

        case Block(stats: List[Tree], expr: Tree) => {
          stats.foreach(updateTree(parent, _))
        }

        case TypeDef(mods: Modifiers, name: TypeName, tparams: List[TypeDef], rhs: Tree) =>
          val ch = TypeNode(name.decodedName.toString(), parent)
          setPos(ch, t)
          ch.setFlags(mods.flags)
          if (!ch.isParam)
            parent.addChild(ch)

        case Import(expr: Tree, selectors) =>
          def printImport = {
            val sb = new StringBuilder
            sb.append(expr.toString())
            if (selectors.size == 1)
              sb.append("." + selectors.head.name)
            else {
              sb.append(".{")
              selectors.foreach(s => sb.append(s.name + ","))
              sb.setLength(sb.length - 1)
              sb.append("}")
            }
            sb.toString
          }
          parent.last match {
            case Some(p: ImportsNode) => {
              val in = ImportNode(printImport, p)
              p.addChild(in)
              setPos(in, t)
            }
            case _ => {
              val ip = ImportsNode(parent)
              parent.addChild(ip)
              val in = ImportNode(printImport, ip)
              ip.addChild(in)
              setPos(in, t)
            }
          }

        case _ => //logger.info("_"+t.getClass+", p="+ t.toString)
      }
    }
    val rootNode = new RootNode
    val t = comp.parseTree(src)
    updateTree(rootNode, t)
    rootNode
  }

}



package org.scalaide.ui.internal.editor.outline
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

sealed abstract class Node(val parent: ContainerNode) extends HasModifiers {
  def name: String
  var start: Int = 0
  var end: Int = 0
  def key: NodeKey
  def isLeaf = true
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

sealed abstract class ContainerNode(parent: ContainerNode) extends Node(parent) {
  protected var _children: Map[NodeKey, Node] = new scala.collection.immutable.ListMap[NodeKey, Node]()
  def children = _children
  def children_=(ch: Map[NodeKey, Node]) = _children = ch
  def addChild(ch: Node) = {
    last = Some(ch)
    _children += (ch.key -> ch)
  }
  var last: Option[Node] = None
  override def isLeaf = children.size == 0
}

case class ClassKey(name: String) extends NodeKey
case class ClassNode(name: String, override val parent: ContainerNode, var typePar: String) extends ContainerNode(parent) {
  override def key = ClassKey(name)
  override def update(src: Node): Boolean = {
    val b = super.update(src)
    src match {
      case c: ClassNode =>
        val r = typePar != c.typePar
        typePar = c.typePar
        r || b
      case _ => b
    }
  }
}

case class ObjectKey(name: String) extends NodeKey
case class ObjectNode(name: String, override val parent: ContainerNode) extends ContainerNode(parent) {
  override def key = ObjectKey(name)
}

case class PackageKey(name: String) extends NodeKey
case class PackageNode(name: String, override val parent: ContainerNode) extends Node(parent) {
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
case class MethodNode(name: String, override val parent: ContainerNode, var typePar: String, val argTypes: List[List[String]] = List())
    extends ContainerNode(parent) with HasModifiers with HasReturnType {
  override def key = MethodKey(name, argTypes)
  override def update(n: Node): Boolean = {
    val b = super.update(n)
    n match {
      case that: MethodNode =>
        val rt = returnType
        returnType = that.returnType
        val tp = typePar
        typePar = that.typePar
        b || rt != returnType || tp != typePar
      case _ => b
    }
  }
}

case class ValKey(name:String) extends NodeKey
case class ValNode(name: String, override val parent: ContainerNode, rt: Option[String])
    extends Node(parent) with HasModifiers with HasReturnType {
  returnType = rt
  override def key = ValKey(name)
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

case class VarKey(name:String) extends NodeKey
case class VarNode(name: String, override val parent: ContainerNode, rt: Option[String])
    extends Node(parent) with HasModifiers with HasReturnType {
  returnType = rt
  override def key = VarKey(name)
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
    extends Node(parent) with HasModifiers {
  override def key = TypeKey(name)
}

case class ImportNode(name: String, override val parent: ContainerNode)
    extends Node(parent) with HasModifiers {
  override def key = ObjectKey(name)
}

case class ImportsNode(override val parent: ContainerNode) extends ContainerNode(parent) {
  override def name = "import declarations"
  override def key = ObjectKey(name)
}

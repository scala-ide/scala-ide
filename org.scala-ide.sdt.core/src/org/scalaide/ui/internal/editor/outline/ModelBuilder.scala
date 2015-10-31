package org.scalaide.ui.internal.editor.outline

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
import scala.reflect.internal.util.OffsetPosition

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
object ModelBuilder extends HasLogger {
  private val tuple = """scala.Tuple[0-9]+""".r
  private val func = """_root_.scala.Function[0-9]+""".r
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
      def renderATT(sb: StringBuilder, tt: Tree, tpt: Tree, args: List[Tree], dp: Boolean): Unit = {
        tpt.toString match {
          case func(_*) =>
            if (args.length == 2) {
              renderType(sb, args.head, List(), true)
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
            renderType(sb, args.head, List(), dp)
          case "_root_.scala.<repeated>" =>
            renderType(sb, args.head, List(), dp)
            sb.append("*")
          case _ =>
            renderType(sb, tpt, args)
        }
      }
      def renderType(sb: StringBuilder, tt: Tree, args: List[Tree] = List(), dp: Boolean = false): Unit = {
        tt match {
          case AppliedTypeTree(tpt: Tree, args: List[Tree]) => renderATT(sb, tt, tpt, args, dp)
          case _ =>
            sb.append(tt.toString().split("\\.").reverse.head)
            if (!args.isEmpty) {
              sb.append("[")
              args.foreach(a => { renderType(sb, a, List(), dp); sb.append(", ") })
              sb.setLength(sb.length - 2)
              sb.append("]")
            }
        }
      }
      def showType(tt: Tree): String = {
        val sb = new StringBuilder
        renderType(sb, tt)
        sb.toString() //.split("\\.").reverse.head
      }
      def showTypeList(tl: List[TypeDef]): String = {
        val sb = new StringBuilder
        renderTypeList(sb, tl)
        sb.toString()
      }
      def renderTypeList(sb: StringBuilder, tl: List[TypeDef]): Unit = {
        if (!tl.isEmpty) {
          sb.append("[")
          tl.foreach(td => { sb.append(td.name); renderTypeList(sb, td.tparams); sb.append(", ") })
          sb.setLength(sb.length - 2)
          sb.append("]")
        }
      }
      t match {
        case Template(_, _, _) => t.children.foreach(x => updateTree(parent, x))
        case PackageDef(pid, stats) => {
          if (pid.pos.isDefined && pid.pos.start != pid.pos.end) {
            val ch = PackageNode(pid.toString(), parent)
            setPos(ch, t)
            parent.addChild(ch)
            //t.children.foreach(x => updateTree(parent, x))
          }
          t.children.foreach(x => updateTree(parent, x))
        }

        case ClassDef(mods, name, tpars, templ) => {
          val ch = ClassNode(name.decodedName.toString(), parent, showTypeList(tpars))
          setPos(ch, t)
          parent.addChild(ch)
          ch.setFlags(mods.flags)
          t.children.foreach(x => updateTree(ch, x))
        }

        case `noSelfType` =>

        case ValDef(mods, name, tpt, rsh) =>
          if ((mods.flags & SYNTHETIC) == 0) {
            val ch = if ((mods.flags & MUTABLE) == 0)
              ValNode(name.decodedName.toString(), parent, if (tpt.isEmpty) None else Some(showType(tpt)))
            else
              VarNode(name.decodedName.toString(), parent, if (tpt.isEmpty) None else Some(showType(tpt)))
            setPos(ch, t)
            ch.setFlags(mods.flags)
            parent.addChild(ch)

          }

        case DefDef(mods, name, tparamss, vparamss, tpt, rsh) =>
          def argList = {
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
          //logger.info("DefDef "+name+" -- tparamss="+ tparamss.map(td => td.name))
          if (t.pos.isOpaqueRange) {
            val ch = MethodNode(name.decodedName.toString(), parent, showTypeList(tparamss), argList)
            ch.returnType = if (!tpt.isEmpty) Some(showType(tpt)) else None
            setPos(ch, t)
            ch.setFlags(mods.flags)
            parent.addChild(ch)
            updateTree(ch, rsh)
          }
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
              selectors.foreach(s => sb.append(s.name + ", "))
              sb.setLength(sb.length - 2)
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
              setPos(ip, t)
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



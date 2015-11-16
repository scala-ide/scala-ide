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
import scala.reflect.internal.util.RangePosition

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
  private val tuple = """Tuple[0-9]+""".r
  private val func = """Function[0-9]+""".r
  def buildTree(comp: IScalaPresentationCompiler, src: SourceFile): RootNode = {
    import comp._
    import scala.reflect.internal.Flags._
    def setPos(n: Node, s: Int, e: Int) = {
      n.end = e
      n.start = s
    }

    def updateTree(parent: ContainerNode, t: Tree): Unit = {
      def renderArgs[A](sb: StringBuilder, args: List[A])(f: A => Unit) = {
        args match {
          case head :: tail =>
            f(head)
            tail.foreach { a => sb.append(", "); f(a) }
          case _ =>
        }
      }

      def renderTuple(sb: StringBuilder, args: List[Tree]) = {
        sb.append("(")
        renderArgs(sb, args)(renderType(sb, _))
        sb.append(")")
      }

      def renderFunc(sb: StringBuilder, args: List[Tree]): Unit = {
        if (args.size == 2) {
          renderType(sb, args.head, List(), true)
          sb.append(" => ")
          renderType(sb, args.last)
        } else {
          sb.append("(")
          renderArgs(sb, args.dropRight(1))(renderType(sb, _))
          sb.append(") => ")
          renderType(sb, args.last)
        }
      }

      def renderATT(sb: StringBuilder, t: Tree, args: List[Tree], needParenthesis: Boolean): Unit = {
        t match {
          case AppliedTypeTree(tpt: Tree, args1: List[Tree]) => renderATT(sb, tpt, args1, needParenthesis)
          case Ident(name) =>
            renderType(sb, t, args)
          case Select(qualifier: Tree, name: Name) =>
            name.toString match {
              case tuple(_*) =>
                if (needParenthesis)
                  sb.append("(")
                renderTuple(sb, args)
                if (needParenthesis)
                  sb.append(")")

              case func(_*) =>
                if (needParenthesis) sb.append("(")
                renderFunc(sb, args)
                if (needParenthesis) sb.append(")")

              case "<byname>" =>
                sb.append("=> ")
                renderType(sb, args.head, List(), false)

              case "<repeated>" =>
                renderType(sb, args.head, List(), false)
                sb.append("*")

              case _ => sb.append(showName(name, t))
            }
          case _ => logger.error("renderATT. Unknown Tree type " + t.getClass)
        }
      }

      def renderType(sb: StringBuilder, tt: Tree, args: List[Tree] = List(), needParenthesis: Boolean = false): Unit = {
        tt match {
          case AppliedTypeTree(tpt: Tree, args: List[Tree]) => renderATT(sb, tpt, args, needParenthesis)

          case Ident(name) =>
            sb.append(showName(name, tt))
            if (!args.isEmpty) {
              sb.append("[")
              renderArgs(sb, args)(renderType(sb, _, Nil, needParenthesis = false))
              sb.append("]")
            }

          case Select(qualifier: Tree, name: Name) =>
            sb.append(showName(name, qualifier))

          case SingletonTypeTree(ref) =>
            renderType(sb, ref)
            sb.append(".type")

          case CompoundTypeTree(templ: Template) =>

          case This(qual: TypeName) =>

          case _ => logger.error("renderType. Unknown Tree type " + tt.getClass)
        }
      }

      def showType(tt: Tree): String = {
        val sb = new StringBuilder
        renderType(sb, tt)
        sb.toString()
      }

      def showTypeList(tl: List[TypeDef]): String = {
        val sb = new StringBuilder
        renderTypeList(sb, tl)
        sb.toString()
      }

      def renderTypeList(sb: StringBuilder, tl: List[TypeDef]): Unit = {
        if (!tl.isEmpty) {
          sb.append("[")
          renderArgs(sb, tl) { td => sb.append(td.name); renderTypeList(sb, td.tparams) }
          sb.append("]")
        }
      }

      def nameLen(name: Name, t: Tree) = {
        if (src.content(t.pos.point) == '`')
          name.decoded.length() + 2
        else
          name.decoded.length()
      }

      def showName(name: Name, t: Tree) = {
        t match {
          case t: DefDef if t.name == nme.CONSTRUCTOR =>
            "this"
          case _ =>
            if (src.content(t.pos.point) == '`')
              "`" + name.decoded + "`"
            else
              name.decoded.split("\\.").reverse.head
        }
      }

      def renderQualifier(sb: StringBuilder, t: Tree): Unit = {
        t match {
          case Ident(name) =>
            sb.append(name)
          case Select(qualifier: Tree, name: Name) =>
            renderQualifier(sb, qualifier)
            sb.append(".")
            sb.append(name)
          case _ => logger.error("Unknown Qualifier tree " + t.getClass)
        }
      }

      t match {
        case Template(_, _, _) => t.children.foreach(x => updateTree(parent, x))
        case PackageDef(pid, stats) => {
          if (pid.pos.isDefined && pid.pos.start != pid.pos.end) {
            val sb = new StringBuilder
            renderQualifier(sb, pid)
            val ch = PackageNode(sb.toString, parent)
            setPos(ch, pid.pos.start, pid.pos.end)
            parent.addChild(ch)
          }

          stats.foreach(x => updateTree(parent, x))
        }

        case ClassDef(mods, name, tpars, templ) => {
          if (name.toString() != "$anon") {
            val ch = ClassNode(showName(name, t), parent, showTypeList(tpars))
            setPos(ch, t.pos.point, t.pos.point + nameLen(name, t))
            parent.addChild(ch)
            ch.setFlags(mods.flags)
            t.children.foreach(x => updateTree(ch, x))
          }
        }

        case `noSelfType` =>

        case ValDef(mods, name, tpt, rsh) =>
          if ((mods.flags & SYNTHETIC) == 0) {
            val ch = if ((mods.flags & MUTABLE) == 0)
              ValNode(showName(name, t), parent, if (tpt.isEmpty) None else Some(showType(tpt)))
            else
              VarNode(showName(name, t), parent, if (tpt.isEmpty) None else Some(showType(tpt)))
            setPos(ch, t.pos.point, t.pos.point + nameLen(name, t))
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
          if (t.pos.isOpaqueRange) {
            val ch = MethodNode(showName(name, t), parent, showTypeList(tparamss), argList)
            ch.returnType = if (!tpt.isEmpty) Some(showType(tpt)) else None
            setPos(ch, t.pos.point, t.pos.point + nameLen(name, t))
            ch.setFlags(mods.flags)
            parent.addChild(ch)
            updateTree(ch, rsh)
          }

        case ModuleDef(mods, name, _) => {
          val ch = ObjectNode(showName(name, t), parent)
          setPos(ch, t.pos.point, t.pos.point + nameLen(name, t))
          ch.setFlags(mods.flags)
          parent.addChild(ch)
          t.children.foreach(x => updateTree(ch, x))
        }

        case DocDef(comment: DocComment, definition: Tree) => updateTree(parent, definition)

        case Block(stats: List[Tree], expr: Tree) => {
          stats.foreach(updateTree(parent, _))
        }

        case TypeDef(mods: Modifiers, name: TypeName, tparams: List[TypeDef], rhs: Tree) =>
          val ch = TypeNode(showName(name, t) + showTypeList(tparams), parent)
          setPos(ch, t.pos.point, t.pos.point + nameLen(name, t))
          ch.setFlags(mods.flags)
          if (!ch.isParam)
            parent.addChild(ch)

        case Import(expr: Tree, selectors) =>
          def printImport = {
            val sb = new StringBuilder
            def renderSelector(is: ImportSelector) = {
              if (is.name == is.rename || is.rename == null)
                sb.append(is.name)
              else
                sb.append(is.name + " => " + is.rename)
            }

            renderQualifier(sb, expr)
            if (selectors.size == 1) {
              val s = selectors.head
              sb.append(".")
              val needParenthesis = s.name != s.rename && s.rename != null
              if (needParenthesis)
                sb.append("{")
              renderSelector(s)
              if (needParenthesis)
                sb.append("}")
            } else {
              sb.append(".{")
              renderArgs(sb, selectors)(renderSelector(_))
              sb.append("}")
            }
            sb.toString
          }
          parent.last match {
            case Some(p: ImportsNode) => {
              val in = ImportNode(printImport, p)
              p.addChild(in)
              setPos(in, t.pos.point, t.pos.end)
            }
            case _ => {
              val ip = ImportsNode(parent)
              setPos(ip, t.pos.point, t.pos.end)
              parent.addChild(ip)
              val in = ImportNode(printImport, ip)
              ip.addChild(in)
              setPos(in, t.pos.point, t.pos.end)
            }
          }
        case _ =>
      }
    }
    val rootNode = new RootNode
    val t = comp.parseTree(src)
    updateTree(rootNode, t)
    rootNode
  }

}

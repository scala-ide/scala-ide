package org.scalaide.spy

import org.eclipse.jface.action.Action
import org.eclipse.jface.resource.ImageDescriptor
import org.eclipse.jface.resource.JFaceResources
import org.eclipse.jface.text.ITextSelection
import org.eclipse.jface.viewers.ISelection
import org.eclipse.swt.SWT
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Text
import org.eclipse.ui.ISelectionListener
import org.eclipse.ui.IWorkbenchPart
import org.eclipse.ui.PlatformUI
import org.eclipse.ui.part.ViewPart
import org.eclipse.ui.plugin.AbstractUIPlugin
import org.eclipse.ui.texteditor.ITextEditor

import scala.reflect.internal.util.SourceFile
import scala.tools.eclipse.InteractiveCompilationUnit
import scala.tools.eclipse.ScalaPresentationCompiler
import scala.tools.eclipse.logging.HasLogger
import scala.tools.eclipse.ui.InteractiveCompilationUnitEditor

class SpyView extends ViewPart with HasLogger {
  private var textArea: Text = _

  def setFocus() {
    textArea.setFocus()
  }

  def createPartControl(parent: Composite) {
    textArea = new Text(parent, SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL)
    textArea.setFont(JFaceResources.getTextFont()) // fixed width font

    createActions()
    createToolbar()
    getSite.getWorkbenchWindow().getSelectionService().addPostSelectionListener(listener)
  }

  override def dispose() {
    super.dispose()
    getSite.getWorkbenchWindow().getSelectionService().removePostSelectionListener(listener)
  }

  private def updateView(selection: ITextSelection, part: IWorkbenchPart) {
    textArea.setText("Offset: \t%s".format(selection.getOffset().toString))
    textArea.append("\nLength: \t%s".format(selection.getLength().toString))

    part match {
      case icuEditor: InteractiveCompilationUnitEditor =>
        val cu = icuEditor.getInteractiveCompilationUnit
        cu.doWithSourceFile { (source, compiler) =>
          import compiler._

          typedTreeAtSelection(compiler)(source, selection) match {
            case Left(tree) =>
              val buf = new StringBuffer
              buf.append("\n\n============\n\nTree: \t\t" + tree.productPrefix)
              buf.append("\ntree.pos: \t%s".format(tree.pos))

              compiler.askOption { () =>
                buf.append("\ntree.tpe: \t%s".format(tree.tpe))
                buf.append("\n\nsymbol: \t\t%s".format(tree.symbol))
                for (sym <- Option(tree.symbol) if sym ne NoSymbol)
                  buf.append("\nsymbol.info: \t%s".format(tree.symbol.info))

                buf.append("\n\nUnits: %s".format(compiler.compilationUnits.map(_.workspaceFile).mkString("", "\n", "")))
              }

              textArea.append(buf.toString)
            case Right(ex) => logger.debug(ex)
          }
        }

      case editor => ()
    }
    textArea.setSelection(0, 0)
  }

  object listener extends ISelectionListener {
    override def selectionChanged(sourcePart: IWorkbenchPart, selection: ISelection) {
      selection match {
        case textSelection: ITextSelection =>
          updateView(textSelection, sourcePart)
        case _ =>
      }
    }
  }

  private def typedTreeAtSelection(compiler: ScalaPresentationCompiler)(source: SourceFile, selection: ISelection): Either[compiler.Tree, Throwable] = {
    import compiler._
    selection match {
      case textSel: ITextSelection =>
        val (offset, length) = (textSel.getOffset(), textSel.getLength())

        compiler.withResponse[Tree] { response =>
          compiler.askTypeAt(rangePos(source, offset, offset, offset + length), response)
        }.get

      case _ => Right(new Exception("unkown selection"))
    }
  }

  private def doWithCompilationUnit(part: IWorkbenchPart)(f: InteractiveCompilationUnit => Unit): Unit =
    part match {
      case icuEditor: InteractiveCompilationUnitEditor =>
        f(icuEditor.getInteractiveCompilationUnit)
      case _ => ()
    }

  def createToolbar() = {
    val mgr = getViewSite().getActionBars().getToolBarManager();
    mgr.add(browseAction)
  }

  var browseAction: Action = _

  def createActions() {
    browseAction = new Action {
      override def run() {
        val editor = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getActiveEditor()
        doWithCompilationUnit(editor) { unit =>
          unit.doWithSourceFile { (source, compiler) =>
            import compiler._

            typedTreeAtSelection(compiler)(source, editor.asInstanceOf[ITextEditor].getSelectionProvider().getSelection()) match {
              case Left(tree) =>
                import treeBrowsers._

                // inlined `treeBrowser.browse` because we don't want to block waiting for the frame to
                // the frame to close
                val tm = new ASTTreeModel(tree)

                val frame = new BrowserFrame()
                frame.setTreeModel(tm)

                // throw-away lock, since we don't need to wait for the frame
                frame.createFrame(new scala.concurrent.Lock())
              case Right(ex) =>
                eclipseLog.warn("Could not retrieve typed tree", ex)
            }
          }
        }
      }
    }

    browseAction.setImageDescriptor(Images.TREE_ICON_DESCRIPTOR)
  }
}

object Images {
  val PluginId = "org.scala-ide.sdt.spy"
  final val TREE_ICON = "tree.icon"

  val TREE_ICON_DESCRIPTOR: ImageDescriptor = AbstractUIPlugin.imageDescriptorFromPlugin(PluginId, "icons/tree.png")
}
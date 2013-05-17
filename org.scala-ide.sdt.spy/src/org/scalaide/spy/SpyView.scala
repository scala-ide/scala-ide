package org.scalaide.spy

import org.eclipse.jface.text.ITextSelection
import org.eclipse.jface.viewers.ISelection
import org.eclipse.swt.SWT
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Text
import org.eclipse.ui.ISelectionListener
import org.eclipse.ui.IWorkbenchPart
import org.eclipse.ui.part.ViewPart
import scala.tools.eclipse.InteractiveCompilationUnit
import scala.tools.eclipse.ui.InteractiveCompilationUnitEditor
import org.eclipse.swt.graphics.Font
import org.eclipse.jface.resource.JFaceResources
import scala.tools.eclipse.logging.HasLogger

class SpyView extends ViewPart with HasLogger {
  private var textArea: Text = _

  def setFocus() {
    textArea.setFocus()
  }

  def createPartControl(parent: Composite) {
    textArea = new Text(parent, SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL)
    textArea.setFont(JFaceResources.getTextFont()) // fixed width font

    getSite.getWorkbenchWindow().getSelectionService().addPostSelectionListener(listener)
  }

  override def dispose() {
    super.dispose()
    getSite.getWorkbenchWindow().getSelectionService().removePostSelectionListener(listener)
  }

  private def updateView(offset: Int, length: Int, part: IWorkbenchPart) {
    textArea.setText("Offset: \t%s".format(offset.toString))
    textArea.append("\nLength: \t%s".format(length.toString))

    part match {
      case icuEditor: InteractiveCompilationUnitEditor =>
        val cu = icuEditor.getInteractiveCompilationUnit
        cu.doWithSourceFile { (source, compiler) =>
          import compiler._

          val response = new Response[Tree]
          compiler.askTypeAt(rangePos(source, offset, offset, offset + length), response)
          response.get match {
            case Left(tree) =>
              textArea.append("\n\n============\n\nTree: \t\t" + tree.productPrefix)
              textArea.append("\ntree.pos: \t%s".format(tree.pos))
              textArea.append("\ntree.tpe: \t%s".format(tree.tpe))
              textArea.append("\n\nsymbol: \t\t%s".format(tree.symbol))
              for (sym <- Option(tree.symbol) if sym ne NoSymbol)
                textArea.append("\nsymbol.info: \t%s".format(tree.symbol.info))

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
          updateView(textSelection.getOffset(), textSelection.getLength(), sourcePart)
        case _ =>
      }
    }
  }
}
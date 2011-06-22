package scala.tools.eclipse.ui

import org.eclipse.swt.custom.StyledText
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.SWT

object CommandField {
  /** Common interface for command evaluator.*/
  trait Evaluator {
    def eval(command: String): Unit
  }
}

/** An input field that evaluate its content when `ENTER` is hit. The entered
 *  input is passed as it is to the evaluator. By default the evaluator does
 *  nothing (@see class NullEvaluator), but different evaluation strategies can
 *  be plugged via the `setEvaluator` method.
 *
 *  It also provides history of the entered inputs. History is accessible
 *  via ARROW-UP/DOWN keyboards' keys.
 */
class CommandField(parent: Composite, style: Int) extends StyledText(parent, style) {

  import CommandField.Evaluator

  /** Listener for keyboards event.
   *  Listened Key events are:
   *  	ENTER <-> SWT.CR
   *  ARROW_UP <-> SWT.ARROW_UP
   *  ARROW_DOWN <-> SWT.ARROW_DOWN
   */
  private class InputFieldListener extends org.eclipse.swt.events.KeyAdapter {
    import collection.mutable.ArrayBuffer
    private val history = new ArrayBuffer[String]
    private var pos: Int = _

    private def appendHistory(expr: String) {
      history += expr
      // every time a new command is pushed in the history, the 
      // currently tracked history position (used for history navigation
      // via ARROW_UP/DOWN keys) is resetted.
      resetHistoryPos()
    }

    def resetHistoryPos() {
      pos = history.length
    }

    override def keyReleased(e: org.eclipse.swt.events.KeyEvent) {
      if (e.keyCode == SWT.CR) evaluate(getText)
      else if (e.keyCode == SWT.ARROW_UP) showPreviousExprFromHistory()
      else if (e.keyCode == SWT.ARROW_DOWN) showNextExprFromHistory()
    }

    private def evaluate(command: String) {
      if (command.nonEmpty) {
        appendHistory(command)
        evaluator.eval(command)
        setText("")
      }
    }

    private def showPreviousExprFromHistory() {
      if (pos > 0) pos -= 1
      updateTextWithCurrentHistory()
    }

    private def showNextExprFromHistory() {
      if (pos < history.length) pos += 1
      updateTextWithCurrentHistory()
    }

    private def updateTextWithCurrentHistory() {
      def currentHistoryToken: String =
        if (history.isEmpty || pos >= history.length) ""
        else history(pos)

      val text = currentHistoryToken
      setText(text)
      setCaretOffset(text.length)
    }
    
    private[CommandField] def reset() {
      resetHistoryPos()
      updateTextWithCurrentHistory()
    }
  }

  class NullEvaluator extends Evaluator {
    override def eval(command: String) {}
  }

  private val inputFieldListener = new InputFieldListener
  addKeyListener(inputFieldListener)

  private var evaluator: Evaluator = new NullEvaluator()

  /** Allows to plug a different evaluation strategy for the typed command. */
  def setEvaluator(_evaluator: Evaluator) { evaluator = _evaluator }

  def clearText() {
    inputFieldListener.reset()
  }
}
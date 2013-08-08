package scala.tools.eclipse.ui

import org.eclipse.swt.custom.StyledText
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.SWT
import org.eclipse.swt.graphics.Color
import org.eclipse.swt.custom.VerifyKeyListener

object CommandField {
  /** Common interface for command evaluator.*/
  trait Evaluator {
    def eval(command: String): Unit
  }

  class NullEvaluator extends Evaluator {
    override def eval(command: String) {}
  }
}

/**
 * An input field that evaluate its content when `CTRL+ENTER` is hit. The entered
 *  input is passed as it is to the evaluator. By default the evaluator does
 *  nothing (@see class NullEvaluator), but different evaluation strategies can
 *  be plugged via the `setEvaluator` method.
 *
 *  It also provides history of the entered inputs. History is accessible
 *  via CTRL + ARROW-UP/DOWN keyboards' keys.
 */
class CommandField(parent: Composite, style: Int) extends StyledText(parent, style) {

  import CommandField.Evaluator

  protected case class MaskedKeyCode(code: Int, mask: Int) {
    def apply(e: org.eclipse.swt.events.KeyEvent): Boolean =
      e.keyCode == code && (e.stateMask & mask) == mask
  }

  protected val evaluateKey = MaskedKeyCode(SWT.CR, SWT.CTRL)
  protected val historyUpKey = MaskedKeyCode(SWT.ARROW_UP, SWT.CTRL)
  protected val historyDownKey = MaskedKeyCode(SWT.ARROW_DOWN, SWT.CTRL)

  private class InputFieldListener extends org.eclipse.swt.events.KeyAdapter with VerifyKeyListener {
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
      if (evaluateKey(e)) evaluate(getText)
      else if (historyUpKey(e)) showPreviousExprFromHistory()
      else if (historyDownKey(e)) showNextExprFromHistory()
    }

    override def verifyKey(e: org.eclipse.swt.events.VerifyEvent) {
      if (evaluateKey(e))
        e.doit = false
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

  /** Handles the display of a help text message that should describe the kind
   * of input that `CommandField` expects.
   * When the input field gains the focus, the help text is automatically hidden.
   * The help text message is re-displayed when the field is empty and it lose the
   * focus.
   *
   *  Clients of `CommandField` can change the default help message as follows:
   *
   *   val inputField = new CommandField(panel, SWT.BORDER | SWT.SINGLE) {
   *                        override protected def helpText = "<type an expression>"
   *                    }
   */
  private class FieldHelpText(textWidget: StyledText, helpText: String) {
    private lazy val codeBgColor = new Color(parent.getDisplay, 150, 150, 150) // gray
    private val defaultBgColor = textWidget.getForeground()

    private var helpTextDisplayed = false

    maybeShowHelpText()

    import org.eclipse.swt.events.FocusListener
    import org.eclipse.swt.events.FocusEvent
    textWidget.addFocusListener(new FocusListener {
      override def focusGained(e: FocusEvent) {
        if (helpTextDisplayed) {
          textWidget.setForeground(defaultBgColor)
          textWidget.setText("")
          helpTextDisplayed = false
        }
      }
      override def focusLost(e: FocusEvent) {
        maybeShowHelpText()
      }
    })

    private def maybeShowHelpText() {
      if (textWidget.getText().isEmpty) {
        textWidget.setForeground(codeBgColor)
        textWidget.setText(helpText)
        helpTextDisplayed = true
      }
    }

    def dispose() {
      codeBgColor.dispose()
      defaultBgColor.dispose()
    }
  }

  protected def helpText = "<type a command>"
  private val fieldHelp = new FieldHelpText(this, helpText)

  private val inputFieldListener = new InputFieldListener
  private var evaluator: Evaluator = new CommandField.NullEvaluator()

  addKeyListener(inputFieldListener)
  addVerifyKeyListener(inputFieldListener)

  /** Allows to plug a different evaluation strategy for the typed command. */
  def setEvaluator(_evaluator: Evaluator) { evaluator = _evaluator }

  def clear() {
    inputFieldListener.reset()
  }

  override def dispose() {
    fieldHelp.dispose()
    super.dispose()
  }
}
package scala.tools.eclipse.debug.evaluation

import org.eclipse.ui.part.ViewPart
import org.eclipse.ui.IPropertyListener
import org.eclipse.swt.graphics.Image
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.graphics.Color
import org.eclipse.swt.SWT
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.custom.StyledText
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.widgets.Caret
import org.eclipse.jface.resource.JFaceResources
import org.eclipse.jdt.ui.PreferenceConstants
import scala.tools.eclipse.ui.CommandField
import org.eclipse.swt.widgets.Label
import org.eclipse.jface.action.Action
import org.eclipse.ui.internal.console.ConsolePluginImages
import org.eclipse.ui.internal.console.IInternalConsoleConstants
import org.eclipse.ui.console.IConsoleConstants
import scala.tools.eclipse.debug.model.ScalaThread
import scala.tools.eclipse.debug.ScalaDebugger
import org.eclipse.swt.custom.StyleRange
import scala.tools.eclipse.logging.HasLogger
import scala.collection.JavaConverters._
import scala.tools.eclipse.launching.ScalaLaunchDelegate
import scala.tools.eclipse.ScalaProject
import scala.tools.eclipse.debug.model.ScalaStackFrame
import org.eclipse.jdt.internal.ui.JavaPlugin
import scala.tools.eclipse.ScalaPlugin
import scalariform.lexer.ScalaLexer
import scala.tools.eclipse.properties.syntaxcolouring.ScalariformToSyntaxClass
import org.eclipse.ui.ISelectionListener
import org.eclipse.ui.IViewSite
import org.eclipse.ui.PlatformUI
import org.eclipse.jface.viewers.IStructuredSelection

// TODO: it would be nice if a console could get notifications whenever a breakpoint stops and when a thread dies, so that the user doesn't have to manually select the frame.
class EvaluationConsoleView extends ViewPart with ISelectionListener with HasLogger {

  private var textWidget: StyledText = null
  private var codeBgColor: Color = null
  private var codeFgColor: Color = null
  private var errorFgColor: Color = null
  private var currentEvaluationEngine: Option[ScalaEvaluationEngine] = None

  override def init(partSite: IViewSite) = {
    super.init(partSite)

    if (!ScalaPlugin.plugin.headlessMode) {
      // TODO: really ugly. Need to keep track of current selection per window.
      PlatformUI.getWorkbench.getWorkbenchWindows.apply(0).getSelectionService.addSelectionListener("org.eclipse.debug.ui.DebugView", this)
    }
  }

  override def selectionChanged(part: org.eclipse.ui.IWorkbenchPart, selection: org.eclipse.jface.viewers.ISelection) {
    def bindStackFrame(evalEngine: ScalaEvaluationEngine, stackFrame: ScalaStackFrame, scalaProject: ScalaProject): Unit = {
      val bindings = ScalaEvaluationEngine.yieldStackFrameBindings(evalEngine, Option(stackFrame), scalaProject)
      for (b <- bindings)
        evalEngine.bind(b.name, b.ivalue, true)(b.tpe)
    }

    def getScalaLaunchDelegate(thread: ScalaThread): Option[ScalaLaunchDelegate] = {
      val launch = thread.getDebugTarget.getLaunch
      val launchDelegate = launch.getLaunchConfiguration().getPreferredDelegate(Set(launch.getLaunchMode()).asJava)
      launchDelegate.getDelegate() match {
        case sld: ScalaLaunchDelegate => Some(sld)
        case _ => None
      }
    }

    def makeEvalEngine(stackFrame: ScalaStackFrame): Option[ScalaEvaluationEngine] = {
      getScalaLaunchDelegate(stackFrame.thread) match {
        case Some(sld) =>
          val evalEngine = {
            currentEvaluationEngine match {
              case Some(e) if !e.isStale && e.thread.threadRef.uniqueID() == stackFrame.thread.threadRef.uniqueID() => e
              case _ => new ScalaEvaluationEngine(sld.classpath, stackFrame.thread.getDebugTarget, stackFrame.thread)
            }
          }
          // TODO: unbind previous stack frame
          bindStackFrame(evalEngine, stackFrame, sld.scalaProject)
          Some(evalEngine)
        case _ => None
      }
    }

    currentEvaluationEngine = selection match {
      case structuredSelection: IStructuredSelection =>
        structuredSelection.getFirstElement() match {
          case scalaThread: ScalaThread =>
            makeEvalEngine(scalaThread.getTopScalaStackFrame)
          case scalaStackFrame: ScalaStackFrame =>
            makeEvalEngine(scalaStackFrame)
          case _ => None
        }
      case _ => None
    }
  }

  def createPartControl(parent: Composite): Unit = {
    codeBgColor = new Color(parent.getDisplay, 230, 230, 230) // light gray
    codeFgColor = new Color(parent.getDisplay, 64, 0, 128) // eggplant
    errorFgColor = new Color(parent.getDisplay, 128, 0, 64) // maroon

    val panel = new Composite(parent, SWT.NONE)
    panel.setLayout(new GridLayout(2, false)) //two columns grid

    // 1st row
    textWidget = new StyledText(panel, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL)
    textWidget.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1)) // span two columns
    textWidget.setEditable(false)
    textWidget.setCaret(new Caret(textWidget, SWT.NONE))

    val editorFont = JFaceResources.getFont(PreferenceConstants.EDITOR_TEXT_FONT)
    textWidget.setFont(editorFont) // java editor font

    // 2nd row
    val inputLabel = new Label(panel, SWT.NULL)
    inputLabel.setText("Evaluate:")

    val inputField = new CommandField(panel, SWT.BORDER | SWT.SINGLE) {
      override protected def helpText = "<type an expression>"
      setEvaluator(new scala.tools.eclipse.ui.CommandField.Evaluator {
        override def eval(command: String) {
          evaluate(command)
        }
      })
    }
    inputField.setFont(editorFont)
    inputField.setLayoutData(new GridData(GridData.FILL_HORIZONTAL))

    val toolbarManager = getViewSite.getActionBars.getToolBarManager
    toolbarManager.add(clearConsoleAction)

    setPartName("Scala Evaluate Expression")
  }

  private def evaluate(expression: String): Unit = {
    displayCode(expression)
    currentEvaluationEngine match {
      case Some(evalEngine) => evalEngine.execute(expression, true, Nil) match {
        case Some(result) =>
          displayOutput(result)
        case _ => displayError("Failed to evaluate expression.")
      }
      case _ => displayError("Unable to evaluate expression.")
    }
  }

  /**
   * Display the string with code formatting
   */
  private def displayCode(text: String) {
    if (textWidget.getCharCount != 0) // don't insert a newline if this is the first line of code to be displayed
      displayOutput("\n")
    appendText("\n", codeFgColor, codeBgColor, SWT.NORMAL, insertNewline = false)
    val colorManager = JavaPlugin.getDefault.getJavaTextTools.getColorManager
    val prefStore = ScalaPlugin.plugin.getPreferenceStore
    for (token <- ScalaLexer.rawTokenise(text, forgiveErrors = true)) {
      val textAttribute = ScalariformToSyntaxClass(token).getTextAttribute(prefStore)
      val bgColor = Option(textAttribute.getBackground) getOrElse codeBgColor
      appendText(token.text, textAttribute.getForeground, bgColor, textAttribute.getStyle, insertNewline = false)
    }
    appendText("\n\n", codeFgColor, codeBgColor, SWT.NORMAL, insertNewline = false)
  }

  private def displayOutput(text: String) {
    appendText(text, null, null, SWT.NORMAL)
  }

  private def displayError(text: String) {
    appendText(text, errorFgColor, null, SWT.NORMAL)
  }

  private def appendText(text: String, fgColor: Color, bgColor: Color, fontStyle: Int, insertNewline: Boolean = false) {
    val lastOffset = textWidget.getCharCount
    val oldLastLine = textWidget.getLineCount

    val outputStr =
      if (insertNewline) "\n" + text.stripLineEnd + "\n\n"
      else text

    textWidget.append(outputStr)
    textWidget.setStyleRange(new StyleRange(lastOffset, outputStr.length, fgColor, null, fontStyle))

    val lastLine = textWidget.getLineCount
    if (bgColor != null)
      textWidget.setLineBackground(oldLastLine - 1, lastLine - oldLastLine, bgColor)
    textWidget.setTopIndex(textWidget.getLineCount - 1)
    textWidget.setStyleRange(new StyleRange(lastOffset, outputStr.length, fgColor, bgColor, fontStyle))

    clearConsoleAction.setEnabled(true)
  }

  private object clearConsoleAction extends Action("Clear Output") {
    setToolTipText("Clear Output")
    setImageDescriptor(ConsolePluginImages.getImageDescriptor(IInternalConsoleConstants.IMG_ELCL_CLEAR));
    setDisabledImageDescriptor(ConsolePluginImages.getImageDescriptor(IInternalConsoleConstants.IMG_DLCL_CLEAR));
    setHoverImageDescriptor(ConsolePluginImages.getImageDescriptor(IConsoleConstants.IMG_LCL_CLEAR));

    override def run() {
      textWidget.setText("")
      setEnabled(false)
    }
  }

  override def setFocus() = {}
}
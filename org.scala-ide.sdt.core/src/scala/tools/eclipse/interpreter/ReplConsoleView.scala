package scala.tools.eclipse
package interpreter

import org.eclipse.jface.action.Separator
import org.eclipse.jface.action.Action
import org.eclipse.jdt.ui.PreferenceConstants
import org.eclipse.jface.resource.JFaceResources
import org.eclipse.swt.graphics.Color
import org.eclipse.swt.custom.StyleRange
import org.eclipse.swt.SWT
import org.eclipse.swt.widgets.Composite
import org.eclipse.ui.part.ViewPart
import org.eclipse.swt.custom.StyledText
import org.eclipse.swt.widgets.{Label, Caret}
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.layout.GridLayout
import scala.tools.eclipse.ui.CommandField
import org.eclipse.debug.internal.ui.IInternalDebugUIConstants
import org.eclipse.debug.internal.ui.DebugPluginImages
import org.eclipse.ui.internal.console.IInternalConsoleConstants
import org.eclipse.ui.console.IConsoleConstants
import org.eclipse.ui.internal.console.ConsolePluginImages
import org.eclipse.jface.action.IAction
import org.eclipse.jdt.internal.ui.JavaPlugin
import scala.tools.eclipse.properties.syntaxcolouring.ScalariformToSyntaxClass
import scalariform.lexer.ScalaLexer
import org.eclipse.swt.widgets.List
import org.eclipse.swt.widgets.Button
import org.eclipse.swt.events.SelectionListener
import org.eclipse.swt.events.SelectionEvent
import org.eclipse.ui.IWorkbenchPage
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.resources.IResourceChangeListener
import org.eclipse.core.resources.IResourceChangeEvent
import org.eclipse.core.resources.IProject
import org.eclipse.core.resources.IResourceDeltaVisitor
import org.eclipse.core.resources.IResourceDelta
import scala.tools.eclipse.util.SWTUtils
import scala.tools.nsc.Settings

class ReplConsoleView extends ViewPart {

  private var textWidget: StyledText = null
  private var codeBgColor: Color = null
  private var codeFgColor: Color = null
  private var errorFgColor: Color = null

  private var projectName: String = ""
  private var scalaProject: ScalaProject = null
  private var isStopped = true
  private var inputField: CommandField = null
  private var projectList: List = null
  private var view = this // gets set to null when disposed

  private lazy val repl = new EclipseRepl(
    // lazy so "project chooser UI" doesn't instantiate
    new EclipseRepl.Client {
      def run(f: => Unit) = SWTUtils.asyncExec(if (view != null) f)
      import EclipseRepl._
      import scala.tools.nsc.interpreter.Results._

      override def done(exec: Exec, result: Result, output: String) {run{
        if (exec ne ReplConsoleView.HideBareExit) {
          view.displayCode(exec)
          result match {
            case Success => view.displayOutput(output)
            case Error => view.displayError(output)
            case Incomplete => view.displayError(
              (if (output.isEmpty) "" else output+"\n")
                + ReplConsoleView.WarnIncomplete )
          }
        }}}
      override def failed(req: Any, thrown: Throwable, output: String) {run{
        val b = new java.io.StringWriter
        val p = new java.io.PrintWriter(b)
        p.println("exception with: "+req)
        if (!output.isEmpty) p.println(output)
        p.println(thrown.getMessage)
        thrown.printStackTrace(p)
        view.displayError(b.toString)
        if (req.isInstanceOf[Settings]) setStopped
        }}
  })
   
  def interpret(text:String) {
    if (isStopped)
      setStarted
    repl.exec(text)
  }
    
  private object stopReplAction extends Action("Terminate") {
    setToolTipText("Erase History and Terminate") 
    
    import IInternalDebugUIConstants._
    setImageDescriptor(DebugPluginImages.getImageDescriptor(IMG_LCL_TERMINATE))
    setDisabledImageDescriptor(DebugPluginImages.getImageDescriptor(IMG_DLCL_TERMINATE))
    setHoverImageDescriptor(DebugPluginImages.getImageDescriptor(IMG_LCL_TERMINATE))
    
    override def run() {
      repl.drop()
      // TODO: uncomment to fix #1000816
      //repl.exec(ReplConsoleView.HideBareExit)
      setStopped
    }
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
  
  private object relaunchAction extends Action("Relaunch Interpreter") {
    setToolTipText("Relaunch Interpreter and Replay History")
    
    import IInternalDebugUIConstants._    
    setImageDescriptor(DebugPluginImages.getImageDescriptor(IMG_ELCL_TERMINATE_AND_RELAUNCH))
    setDisabledImageDescriptor(DebugPluginImages.getImageDescriptor(IMG_DLCL_TERMINATE_AND_RELAUNCH))
    setHoverImageDescriptor(DebugPluginImages.getImageDescriptor(IMG_ELCL_TERMINATE_AND_RELAUNCH))
    
    override def run() {
      clearConsoleAction.run
      setStarted
    }  
  }
  
  object replayAction extends Action("Replay Interpreter History") {
    setToolTipText("Reset Interpreter and Replay All Commands")
    
    import IInternalDebugUIConstants._    
    setImageDescriptor(DebugPluginImages.getImageDescriptor(IMG_ELCL_RESTART))
    setDisabledImageDescriptor(DebugPluginImages.getImageDescriptor(IMG_DLCL_RESTART))
    setHoverImageDescriptor(DebugPluginImages.getImageDescriptor(IMG_ELCL_RESTART))
    override def run() {
      // NOTE: change in behavior - interpreter always reset
      displayError("\n------ Resetting Interpreter and Replaying Command History ------\n")
      setStarted
    }
  }  
  
  object refreshOnRebuildAction extends Action("Replay History on Project Rebuild", IAction.AS_CHECK_BOX) with BuildSuccessListener {
    setToolTipText("Replay History on Project Rebuild")
    
    setImageDescriptor(ScalaImages.REFRESH_REPL_TOOLBAR)
    setHoverImageDescriptor(ScalaImages.REFRESH_REPL_TOOLBAR)
    
    override def run() {
      if (isChecked) scalaProject addBuildSuccessListener this
      else scalaProject removeBuildSuccessListener this
    }
    
    def buildSuccessful() {
      util.SWTUtils asyncExec {
        if (!isStopped) {
          displayError("\n------ Project Rebuilt, Replaying Command History ------\n")
          setStarted
        }
      }
    }
  }
  
  private def setStarted {
    val settings = ScalaPlugin.defaultScalaSettings()
    scalaProject.initializeCompilerSettings(settings, _ => true)
    // TODO ? move into ScalaPlugin.getScalaProject or ScalaProject.classpath
    var cp = settings.classpath.value
    for { opt <- Seq( ScalaPlugin.plugin.swingClasses,
                      ScalaPlugin.plugin.libClasses )
          p <- opt ; s = p.toOSString }
      if(!cp.contains(s))
        cp = s + java.io.File.pathSeparator + cp
    settings.classpath.value = cp
    // end to do ? move
    repl.init(settings)
    isStopped = false

    stopReplAction.setEnabled(true)

    setContentDescription("Scala Interpreter (Project: " + projectName + ")")
  }

  private def setStopped {
    repl.stop()
    isStopped = true

    stopReplAction.setEnabled(false)
    
    setContentDescription("<terminated> " + getContentDescription)
  }

  override def createPartControl(parent: Composite) {
    // if the view has no secondary id, display UI to choose a project
    projectName = getViewSite.getSecondaryId
    if (projectName == null) {
      createProjectChooserPartControl(parent)
    } else {
      createInterpreterPartControl(parent)
    }
  }

  /**
   * Check if the delta is about a project availability change.
   */
  class ResourceDeltaVisitor extends IResourceDeltaVisitor {
    var isProjectChange = false
    override def visit(delta: IResourceDelta): Boolean = {
      delta.getResource() match {
        case project: IProject =>
          // the project has been opened, closed, added or removed
          isProjectChange |= ((delta.getFlags() & IResourceDelta.OPEN) | (delta.getKind() & (IResourceDelta.ADDED | IResourceDelta.REMOVED))) != 0

          false // we only care about projects, no need to go inside them
        case _ =>
          true
      }
    }
  }

  /**
   * resource change listener to refresh the project list when
   * projects are opened or closed
   */
  val resourceChangeListener = new IResourceChangeListener() {
    def resourceChanged(event: IResourceChangeEvent) {
      val resourceDeltaVisitor = new ResourceDeltaVisitor()
      if (event.getDelta() != null) {
        event.getDelta().accept(resourceDeltaVisitor)
        if (resourceDeltaVisitor.isProjectChange) {
          SWTUtils.asyncExec {
            refreshProjectList()
          }
        }
      }
    }
  }

  /**
   * Create the project chooser UI
   */
  def createProjectChooserPartControl(parent: Composite) {
    val panel = new Composite(parent, SWT.NONE)
    panel.setLayout(new GridLayout(1, false))

    // text
    val label = new Label(panel, SWT.NONE)
    label.setText("Please select the scala project to be used by the interpreter:")

    // list widget
    projectList = new List(panel, SWT.SINGLE | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL)
    projectList.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL | GridData.FILL_VERTICAL))

    // ok button
    val okButton = new Button(panel, SWT.PUSH)
    okButton.setText("OK")
    okButton.setEnabled(false)

    // scala projects
    refreshProjectList()
    ResourcesPlugin.getWorkspace().addResourceChangeListener(resourceChangeListener)

    // listeners
    projectList.addSelectionListener(new SelectionListener() {
      override def widgetSelected(e: SelectionEvent) {
        okButton.setEnabled(true)
      }

      override def widgetDefaultSelected(e: SelectionEvent) {
        projectSelected(projectList.getSelection()(0))
      }
    })

    okButton.addSelectionListener(new SelectionListener() {
      override def widgetSelected(e: SelectionEvent) {
        projectSelected(projectList.getSelection()(0))
      }

      override def widgetDefaultSelected(e: SelectionEvent) {
      }
    })
  }

  /**
   * Reset the project list to the current set of open projects
   */
  def refreshProjectList() {
    val scalaProjectNames = for (project <- ResourcesPlugin.getWorkspace().getRoot().getProjects()
        if project.isOpen && project.hasNature(org.eclipse.jdt.core.JavaCore.NATURE_ID))
      yield project.getName()
    projectList.setItems(scalaProjectNames)
  }

  /**
   * A project has been selected, close the current view and open the right one.
   */
  def projectSelected(selectedProjectName: String) {
    val workbenchPage = getViewSite().getPage()
    workbenchPage.hideView(this)

    val project = ResourcesPlugin.getWorkspace().getRoot().getProject(selectedProjectName)
    ReplConsoleView.makeActive(project, workbenchPage)
  }
    
  /**
   * Create the interpreter UI
   */
  private def createInterpreterPartControl(parent: Composite) {
    
    codeBgColor = new Color(parent.getDisplay, 230, 230, 230)   // light gray
    codeFgColor = new Color(parent.getDisplay, 64, 0, 128)      // eggplant
    errorFgColor = new Color(parent.getDisplay, 128, 0, 64)     // maroon
    
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
    
    inputField = new CommandField(panel, SWT.BORDER | SWT.SINGLE) {
      override protected def helpText = "<type an expression>" 
      setEvaluator(new scala.tools.eclipse.ui.CommandField.Evaluator {
        override def eval(command: String) {
          interpret(command)
        }
      })
    }
    inputField.setFont(editorFont)
    inputField.setLayoutData(new GridData(GridData.FILL_HORIZONTAL))
     
    val toolbarManager = getViewSite.getActionBars.getToolBarManager
    toolbarManager.add(replayAction)
    toolbarManager.add(new Separator)
    toolbarManager.add(stopReplAction)
    toolbarManager.add(relaunchAction)
    toolbarManager.add(new Separator)
    toolbarManager.add(clearConsoleAction)
    toolbarManager.add(new Separator)
    toolbarManager.add(refreshOnRebuildAction)
    
    setPartName("Scala Interpreter (" + projectName + ")")
    
    // Register the interpreter for the project
    scalaProject= ScalaPlugin.plugin.getScalaProject(ResourcesPlugin.getWorkspace().getRoot().getProject(projectName))
    stopReplAction.run()
    setStarted
  }

  override def setFocus() { }

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
  
  override def dispose() {
    view = null
    if (projectName == null) {
      // elements of the project chooser view
      ResourcesPlugin.getWorkspace().removeResourceChangeListener(resourceChangeListener)
    } else {
      // elements of the interpreter view
      codeBgColor.dispose
      codeFgColor.dispose
      errorFgColor.dispose
    
      repl.quit()
      
      scalaProject removeBuildSuccessListener refreshOnRebuildAction
    }
  }
}

object ReplConsoleView
{
  private def show(mode: Int, project: IProject, page: IWorkbenchPage): ReplConsoleView = {
    if (! project.isOpen)
      throw new org.eclipse.ui.PartInitException("project is not open ("+project.getName+")");
    ScalaPlugin.plugin.getScalaProject(project) // creates if given project isn't already
    val viewPart = page.showView("org.scala-ide.sdt.core.consoleView", project.getName, mode)
    viewPart.asInstanceOf[interpreter.ReplConsoleView]
  }

  def makeVisible(project: IProject, page: IWorkbenchPage): ReplConsoleView =
    show(IWorkbenchPage.VIEW_VISIBLE, project, page)

  def makeActive(project: IProject, page: IWorkbenchPage): ReplConsoleView =
    show(IWorkbenchPage.VIEW_ACTIVATE, project, page)

  private val WarnIncomplete = "incomplete statements not supported yet, sorry, you'll have to retype..."
  private val HideBareExit = """def exit = println("close view to exit")"""
}

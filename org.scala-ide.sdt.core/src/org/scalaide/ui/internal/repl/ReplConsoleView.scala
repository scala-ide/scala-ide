package org.scalaide.ui.internal.repl

import org.scalaide.core.internal.project.BuildSuccessListener
import org.scalaide.ui.internal.ScalaImages
import org.scalaide.core.ScalaPlugin
import org.scalaide.core.internal.project.ScalaProject
import org.scalaide.ui.syntax.ScalariformToSyntaxClass
import scala.tools.nsc.Settings
import org.eclipse.core.resources.IProject
import org.eclipse.core.resources.IResourceChangeEvent
import org.eclipse.core.resources.IResourceChangeListener
import org.eclipse.core.resources.IResourceDelta
import org.eclipse.core.resources.IResourceDeltaVisitor
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.debug.internal.ui.DebugPluginImages
import org.eclipse.debug.internal.ui.IInternalDebugUIConstants
import org.eclipse.jdt.internal.ui.JavaPlugin
import org.eclipse.jdt.ui.PreferenceConstants
import org.eclipse.jface.action.Action
import org.eclipse.jface.action.IAction
import org.eclipse.jface.action.Separator
import org.eclipse.jface.resource.JFaceResources
import org.eclipse.swt.SWT
import org.eclipse.swt.custom.StyleRange
import org.eclipse.swt.custom.StyledText
import org.eclipse.swt.events.SelectionEvent
import org.eclipse.swt.events.SelectionListener
import org.eclipse.swt.graphics.Color
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.widgets.Button
import org.eclipse.swt.widgets.Caret
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Label
import org.eclipse.swt.widgets.List
import org.eclipse.ui.IWorkbenchPage
import org.eclipse.ui.console.IConsoleConstants
import org.eclipse.ui.internal.console.ConsolePluginImages
import org.eclipse.ui.internal.console.IInternalConsoleConstants
import org.eclipse.ui.part.ViewPart
import org.scalaide.core.internal.repl.EclipseRepl.Exec
import scalariform.lexer.ScalaLexer
import org.scalaide.core.internal.repl.EclipseRepl
import org.scalaide.util.internal.ui.DisplayThread
import org.scalaide.core.internal.project.ScalaInstallation

class ReplConsoleView extends ViewPart with InterpreterConsoleView {

  private var projectName: String = ""
  private var scalaProject: ScalaProject = null
  private var isStopped = true
  private var projectList: List = null
  private var view = this // gets set to null when disposed

  private lazy val repl = new EclipseRepl(
    // lazy so "project chooser UI" doesn't instantiate
    new EclipseRepl.Client {
      def run(f: => Unit) = DisplayThread.asyncExec(if (view != null) f)
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

  override def evaluate(text:String) {
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

  protected object clearConsoleAction extends Action("Clear Output") {
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
      DisplayThread.asyncExec {
        if (!isStopped) {
          displayError("\n------ Project Rebuilt, Replaying Command History ------\n")
          setStarted
        }
      }
    }
  }

  private def setStarted() {
    val settings = ScalaPlugin.defaultScalaSettings()
    scalaProject.initializeCompilerSettings(settings, _ => true)
    // TODO ? move into ScalaPlugin.getScalaProject or ScalaProject.classpath
    var cp = settings.classpath.value
    for {
      s <- (ScalaInstallation.platformInstallation.extraJars.map(_.classJar) :+ ScalaInstallation.platformInstallation.library.classJar).map(_.toOSString())
    }
      if(!cp.contains(s))
        cp = s + java.io.File.pathSeparator + cp
    settings.classpath.value = cp
    // end to do ? move
    repl.init(settings)
    isStopped = false

    stopReplAction.setEnabled(true)

    setContentDescription("Scala Interpreter (Project: " + projectName + ")")
  }

  private def setStopped() {
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
          DisplayThread.asyncExec {
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
  override def createInterpreterPartControl(parent: Composite) {
    super.createInterpreterPartControl(parent)
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

  override def dispose() {
    super.dispose()
    view = null
    if (projectName == null) {
      // elements of the project chooser view
      ResourcesPlugin.getWorkspace().removeResourceChangeListener(resourceChangeListener)
    } else {
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
    viewPart.asInstanceOf[ReplConsoleView]
  }

  def makeVisible(project: IProject, page: IWorkbenchPage): ReplConsoleView =
    show(IWorkbenchPage.VIEW_VISIBLE, project, page)

  def makeActive(project: IProject, page: IWorkbenchPage): ReplConsoleView =
    show(IWorkbenchPage.VIEW_ACTIVATE, project, page)

  private val WarnIncomplete = "incomplete statements not supported yet, sorry, you'll have to retype..."
  private val HideBareExit = """def exit = println("close view to exit")"""
}

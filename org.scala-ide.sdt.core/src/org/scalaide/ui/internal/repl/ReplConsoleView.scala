/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.ui.internal.repl

import org.scalaide.ui.ScalaImages
import org.scalaide.core.IScalaPlugin
import org.scalaide.core.IScalaProject
import scala.tools.nsc.Settings
import org.eclipse.core.resources.IProject
import org.eclipse.core.resources.IResourceChangeEvent
import org.eclipse.core.resources.IResourceChangeListener
import org.eclipse.core.resources.IResourceDelta
import org.eclipse.core.resources.IResourceDeltaVisitor
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.debug.internal.ui.DebugPluginImages
import org.eclipse.debug.internal.ui.IInternalDebugUIConstants
import org.eclipse.jface.action.Action
import org.eclipse.jface.action.IAction
import org.eclipse.jface.action.Separator
import org.eclipse.swt.SWT
import org.eclipse.swt.events.SelectionEvent
import org.eclipse.swt.events.SelectionListener
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.widgets.Button
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Label
import org.eclipse.swt.widgets.List
import org.eclipse.ui.IWorkbenchPage
import org.eclipse.ui.console.IConsoleConstants
import org.eclipse.ui.internal.console.ConsolePluginImages
import org.eclipse.ui.internal.console.IInternalConsoleConstants
import org.eclipse.ui.part.ViewPart
import org.scalaide.core.internal.repl.EclipseRepl.Exec
import org.scalaide.core.internal.repl.EclipseRepl
import org.scalaide.util.ui.DisplayThread
import org.scalaide.core.internal.project.ScalaInstallation.platformInstallation
import scala.collection.mutable.Subscriber
import scala.collection.mutable.Publisher
import org.scalaide.core.BuildSuccess
import org.scalaide.core.IScalaProjectEvent
import org.scalaide.core.internal.compiler.ScalaPresentationCompiler

class ReplConsoleView extends ViewPart with InterpreterConsoleView {

  private var projectName: String = ""
  private var scalaProject: IScalaProject = null
  private var isStopped = true
  private var projectList: List = null
  private var view = this // gets set to null when disposed

  private lazy val repl = new EclipseRepl(
    // lazy so "project chooser UI" doesn't instantiate
    new EclipseRepl.Client {
      def run(f: => Unit) = DisplayThread.asyncExec(if (view != null) f)
      import EclipseRepl._
      import scala.tools.nsc.interpreter.Results._

      override def done(exec: Exec, result: Result, output: String): Unit = {
        run {
          if (exec ne ReplConsoleView.HideBareExit) {
            view.displayCode(exec)
            result match {
              case Success => view.displayOutput(output)
              case Error => view.displayError(output)
              case Incomplete => view.displayError(
                (if (output.isEmpty) "" else output + "\n")
                  + ReplConsoleView.WarnIncomplete)
            }
          }
        }
      }
      override def failed(req: Any, thrown: Throwable, output: String): Unit = {
        run {
          val b = new java.io.StringWriter
          val p = new java.io.PrintWriter(b)
          p.println("exception with: " + req)
          if (!output.isEmpty) p.println(output)
          p.println(thrown.getMessage)
          thrown.printStackTrace(p)
          view.displayError(b.toString)
          if (req.isInstanceOf[Settings]) setStopped
        }
      }
    })

  override def evaluate(text: String): Unit = {
    if (isStopped) {
      setStarted
    }
    repl.exec(text)
  }

  private object stopReplAction extends Action("Terminate") {
    setToolTipText("Erase History and Terminate")

    import IInternalDebugUIConstants._
    setImageDescriptor(DebugPluginImages.getImageDescriptor(IMG_LCL_TERMINATE))
    setDisabledImageDescriptor(DebugPluginImages.getImageDescriptor(IMG_DLCL_TERMINATE))
    setHoverImageDescriptor(DebugPluginImages.getImageDescriptor(IMG_LCL_TERMINATE))

    override def run(): Unit = {
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

    override def run(): Unit = {
      resultsTextWidget.setText("")
      setEnabled(false)
    }
  }

  private object relaunchAction extends Action("Relaunch Interpreter") {
    setToolTipText("Relaunch Interpreter and Replay History")

    import IInternalDebugUIConstants._
    setImageDescriptor(DebugPluginImages.getImageDescriptor(IMG_ELCL_TERMINATE_AND_RELAUNCH))
    setDisabledImageDescriptor(DebugPluginImages.getImageDescriptor(IMG_DLCL_TERMINATE_AND_RELAUNCH))
    setHoverImageDescriptor(DebugPluginImages.getImageDescriptor(IMG_ELCL_TERMINATE_AND_RELAUNCH))

    override def run(): Unit = {
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
    override def run(): Unit = {
      // NOTE: change in behavior - interpreter always reset
      displayError("\n------ Resetting Interpreter and Replaying Command History ------\n")
      setStarted
    }
  }

  object refreshOnRebuildAction extends Action("Replay History on Project Rebuild", IAction.AS_CHECK_BOX) with Subscriber[IScalaProjectEvent, Publisher[IScalaProjectEvent]] {
    setToolTipText("Replay History on Project Rebuild")

    setImageDescriptor(ScalaImages.REFRESH_REPL_TOOLBAR)
    setHoverImageDescriptor(ScalaImages.REFRESH_REPL_TOOLBAR)

    override def run(): Unit = {
      if (isChecked) scalaProject.subscribe(this)
      else scalaProject.removeSubscription(this)
    }

    def notify(pub:Publisher[IScalaProjectEvent], event:IScalaProjectEvent): Unit = {
      event match { case e: BuildSuccess =>
        DisplayThread.asyncExec {
          if (!isStopped) {
            displayError("\n------ Project Rebuilt, Replaying Command History ------\n")
            setStarted
          }
        }
      }
    }
  }

  private def setStarted(): Unit = {
    val settings = ScalaPresentationCompiler.defaultScalaSettings()
    scalaProject.initializeCompilerSettings(settings, _ => true)
    // TODO ? move into ScalaPlugin.getScalaProject or ScalaProject.classpath
    var cp = settings.classpath.value
    val extraJars = platformInstallation.extraJars
    val classJar = platformInstallation.library.classJar
    for {
      path <- extraJars.map(_.classJar) :+ classJar
      pathString = path.toOSString()
      if !cp.contains(pathString)
    } cp = pathString + java.io.File.pathSeparator + cp
    settings.classpath.value = cp
    // end to do ? move
    repl.init(settings)
    isStopped = false

    stopReplAction.setEnabled(true)

    setContentDescription("Scala Interpreter (Project: " + projectName + ")")
  }

  private def setStopped(): Unit = {
    repl.stop()
    isStopped = true

    stopReplAction.setEnabled(false)

    setContentDescription("<terminated> " + getContentDescription)
  }

  override def createPartControl(parent: Composite): Unit = {
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
    def resourceChanged(event: IResourceChangeEvent): Unit = {
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
  def createProjectChooserPartControl(parent: Composite): Unit = {
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
      override def widgetSelected(e: SelectionEvent): Unit = {
        okButton.setEnabled(true)
      }

      override def widgetDefaultSelected(e: SelectionEvent): Unit = {
        projectSelected(projectList.getSelection()(0))
      }
    })

    okButton.addSelectionListener(new SelectionListener() {
      override def widgetSelected(e: SelectionEvent): Unit = {
        projectSelected(projectList.getSelection()(0))
      }

      override def widgetDefaultSelected(e: SelectionEvent): Unit = {
      }
    })
  }

  /**
   * Reset the project list to the current set of open projects
   */
  def refreshProjectList(): Unit = {
    val scalaProjectNames = for {
      project <- ResourcesPlugin.getWorkspace().getRoot().getProjects()
      if project.isOpen && project.hasNature(org.eclipse.jdt.core.JavaCore.NATURE_ID)
    } yield project.getName()
    projectList.setItems(scalaProjectNames: _*)
  }

  /**
   * A project has been selected, close the current view and open the right one.
   */
  def projectSelected(selectedProjectName: String): Unit = {
    val workbenchPage = getViewSite().getPage()
    workbenchPage.hideView(this)

    val project = ResourcesPlugin.getWorkspace().getRoot().getProject(selectedProjectName)
    ReplConsoleView.makeActive(project, workbenchPage)
  }

  /**
   * Create the interpreter UI
   */
  override def createInterpreterPartControl(parent: Composite): Unit = {
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
    scalaProject = IScalaPlugin().getScalaProject(ResourcesPlugin.getWorkspace().getRoot().getProject(projectName))
    stopReplAction.run()
    setStarted
  }

  override def setFocus(): Unit = {}

  override def dispose(): Unit = {
    super.dispose()
    view = null
    if (projectName == null) {
      // elements of the project chooser view
      ResourcesPlugin.getWorkspace().removeResourceChangeListener(resourceChangeListener)
    } else {
      repl.quit()

      scalaProject.removeSubscription(refreshOnRebuildAction)
    }
  }
}

object ReplConsoleView {
  private def show(mode: Int, project: IProject, page: IWorkbenchPage): ReplConsoleView = {
    if (!project.isOpen)
      throw new org.eclipse.ui.PartInitException("project is not open ("+project.getName+")");
    IScalaPlugin().getScalaProject(project) // creates if given project isn't already
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

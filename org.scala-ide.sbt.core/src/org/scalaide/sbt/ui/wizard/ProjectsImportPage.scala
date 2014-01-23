package org.scalaide.sbt.ui.wizard

import java.io.File
import java.lang.reflect.InvocationTargetException
import scala.collection.immutable.Seq
import scala.concurrent.Await
import scala.concurrent.Promise
import org.eclipse.core.resources.IProject
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.runtime.CoreException
import org.eclipse.core.runtime.IPath
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.OperationCanceledException
import org.eclipse.core.runtime.Path
import org.eclipse.core.runtime.Status
import org.eclipse.core.runtime.SubProgressMonitor
import org.eclipse.jface.dialogs.Dialog
import org.eclipse.jface.dialogs.ErrorDialog
import org.eclipse.jface.dialogs.IMessageProvider
import org.eclipse.jface.layout.PixelConverter
import org.eclipse.jface.operation.IRunnableWithProgress
import org.eclipse.jface.viewers.CheckStateChangedEvent
import org.eclipse.jface.viewers.CheckboxTreeViewer
import org.eclipse.jface.viewers.IColorProvider
import org.eclipse.jface.viewers.IStructuredSelection
import org.eclipse.jface.viewers.ITreeContentProvider
import org.eclipse.jface.viewers.LabelProvider
import org.eclipse.jface.viewers.StructuredViewer
import org.eclipse.jface.viewers.Viewer
import org.eclipse.jface.viewers.ViewerComparator
import org.eclipse.osgi.util.NLS
import org.eclipse.swt.SWT
import org.eclipse.swt.events.TraverseEvent
import org.eclipse.swt.graphics.Color
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.widgets.Button
import org.eclipse.swt.widgets.Combo
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.DirectoryDialog
import org.eclipse.swt.widgets.Event
import org.eclipse.swt.widgets.Group
import org.eclipse.swt.widgets.Label
import org.eclipse.ui.PlatformUI
import org.eclipse.ui.dialogs.WizardDataTransferPage
import org.eclipse.ui.dialogs.WorkingSetGroup
import org.eclipse.ui.internal.ide.IDEWorkbenchMessages
import org.eclipse.ui.internal.ide.IDEWorkbenchPlugin
import org.eclipse.ui.internal.wizards.datatransfer.DataTransferMessages
import org.scalaide.sbt.core.SbtClientProvider
import org.scalaide.sbt.ui.actions.withWorkspaceModifyOperation
import sbt.protocol.MinimalBuildStructure
import sbt.protocol.ProjectReference
import org.eclipse.jface.window.IShellProvider

object ProjectsImportPage {

  /** */
  class ProjectRecord(ref: ProjectReference) {
    /** Location of the sbt build file. */
    lazy val buildFile = new File(ref.build.getRawPath())

    /** project's name */
    def name: String = ref.name

    /** true if the project cannot be safely imported in Eclipse, e.g., name collision with an already imported project */
    @volatile
    var hasConflicts: Boolean = false
  }

  /** Used by the `CheckboxTreeViewer` to gray out `ProjectRecord`s that cannot be imported in the workspace. */
  private final class ProjectLabelProvider(shellProvider: ProjectsImportPage) extends LabelProvider with IColorProvider {
    override def getText(element: AnyRef): String =
      element.asInstanceOf[ProjectRecord].name

    override def getBackground(element: AnyRef): Color = null

    override def getForeground(element: AnyRef): Color = element match {
      case record: ProjectRecord if record.hasConflicts =>
        shellProvider.getShell.getDisplay().getSystemColor(SWT.COLOR_GRAY)
      case _ => null
    }
  }

  /** Holds information about the current state of the UI.
    * @param selectedProjects
    */
  private class Model {

    private lazy val workspaceProjects: Seq[IProject] = IDEWorkbenchPlugin.getPluginWorkspace().getRoot().getProjects().toList

    /** Working set groups where there `selectedProjects` should be imported (if any).*/
    @volatile
    var workingSetGroup: Option[WorkingSetGroup] = None

    /** Projects selected by the user that will be imported in the workspace. Note that
      * the intersection of `selectedProjects` and `workspaceProjects` should be empty.
      */
    @volatile
    var selectedProjects: Seq[ProjectRecord] = Seq.empty

    @volatile
    private var createdProjects: Seq[IProject] = Seq.empty // TODO: Logic for creating a project still needs to be implemented

    def addCreatedProject(project: IProject): Unit = { createdProjects = createdProjects :+ project }

    /** Projects that can be imported in the workspace without conflicts. */
    def importableProjects(): Seq[ProjectRecord] = {
      for {
        project <- selectedProjects
        hasConflicts = isProjectInWorkspace(project.name)
      } project.hasConflicts = hasConflicts

      selectedProjects.filterNot(_.hasConflicts)
    }

    /** Determine if the project with the given name is in the current workspace.
      *
      * @param projectName The project name to check
      * @return true if the project with the given name is in this workspace
      */
    private def isProjectInWorkspace(projectName: String): Boolean = workspaceProjects.exists(_.getName == projectName)
  }
}

/* This class is heavily inspired by `org.eclipse.ui.wizards.datatransfer.ExternalProjectImportWizard` */
class ProjectsImportPage(currentSelection: IStructuredSelection) extends WizardDataTransferPage("sbtProjectsImportPage") {

  setTitle("Import Sbt project")
  setDescription("Select the root directory of the Sbt project you want to import.")
  setPageComplete(false)

  import org.scalaide.sbt.ui.events.Implicits._
  import org.scalaide.sbt.ui.events.EventListenerAdapters._
  import ProjectsImportPage._

  private val model = new Model

  // UI elements
  private var directoryPathField: Combo = _
  private var browseDirectoriesButton: Button = _
  private var projectsList: CheckboxTreeViewer = _
  private var copyCheckbox: Button = _

  override def createControl(parent: Composite): Unit = {

    initializeDialogUnits(parent)

    val workArea = new Composite(parent, SWT.NONE)
    setControl(workArea)

    workArea.setLayout(new GridLayout())
    workArea.setLayoutData(new GridData(GridData.FILL_BOTH
      | GridData.GRAB_HORIZONTAL | GridData.GRAB_VERTICAL))

    createProjectsRoot(workArea)
    createProjectsList(workArea)
    createWorkingSetGroup(workArea)
    Dialog.applyDialogFont(workArea)
  }

  private def createProjectsRoot(workArea: Composite): Unit = {
    // project specification group
    val projectGroup = new Composite(workArea, SWT.NONE)
    val layout = new GridLayout()
    layout.numColumns = 3
    layout.makeColumnsEqualWidth = false
    layout.marginWidth = 0
    projectGroup.setLayout(layout)
    projectGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL))

    // new project from directory radio button
    val projectsLabel = new Label(projectGroup, SWT.BORDER)
    projectsLabel.setText(DataTransferMessages.WizardProjectsImportPage_RootSelectTitle)

    // project location entry combo
    this.directoryPathField = new Combo(projectGroup, SWT.BORDER)

    val directoryPathData = new GridData(GridData.HORIZONTAL_ALIGN_FILL | GridData.GRAB_HORIZONTAL)
    directoryPathData.widthHint = new PixelConverter(directoryPathField).convertWidthInCharsToPixels(25)
    directoryPathField.setLayoutData(directoryPathData)

    // browse button
    browseDirectoriesButton = new Button(projectGroup, SWT.PUSH)
    browseDirectoriesButton.setText(DataTransferMessages.DataTransfer_browse)
    setButtonLayoutData(browseDirectoriesButton)

    browseDirectoriesButton.addSelectionListener(onWidgetSelected { _ =>
      handleLocationDirectoryButtonPressed()
    })

    directoryPathField.addTraverseListener((e: TraverseEvent) => {
      if (e.detail == SWT.TRAVERSE_RETURN) {
        e.doit = false
        updateProjectsList(directoryPathField.getText().trim())
      }
      else ()
    })

    directoryPathField.addSelectionListener(onWidgetSelected { _ =>
      updateProjectsList(directoryPathField.getText().trim())
    })
  }

  private def handleLocationDirectoryButtonPressed(): Unit = {
    def workbenchLocation: IPath = IDEWorkbenchPlugin.getPluginWorkspace().getRoot().getLocation()

    val dialog = new DirectoryDialog(directoryPathField.getShell(), SWT.SHEET)
    dialog.setMessage(DataTransferMessages.WizardProjectsImportPage_SelectDialogTitle)

    var dirName = directoryPathField.getText().trim()

    if (dirName.isEmpty)
      dialog.setFilterPath(workbenchLocation.toOSString())
    else {
      val path = new File(dirName)
      if (path.exists())
        dialog.setFilterPath(new Path(dirName).toOSString())
    }

    for (selectedDirectory <- Option(dialog.open())) {
      directoryPathField.setText(selectedDirectory)
      updateProjectsList(selectedDirectory)
    }
  }

  /** Create the checkbox list for the found projects. */
  private def createProjectsList(workArea: Composite): Unit = {

    val title = new Label(workArea, SWT.NONE)
    title.setText(DataTransferMessages.WizardProjectsImportPage_ProjectsListTitle)

    val listComposite = new Composite(workArea, SWT.NONE)
    val layout = new GridLayout()
    layout.numColumns = 2
    layout.marginWidth = 0
    layout.makeColumnsEqualWidth = false
    listComposite.setLayout(layout)

    listComposite.setLayoutData(new GridData(GridData.GRAB_HORIZONTAL
      | GridData.GRAB_VERTICAL | GridData.FILL_BOTH))

    projectsList = new CheckboxTreeViewer(listComposite, SWT.BORDER)
    val gridData = new GridData(SWT.FILL, SWT.FILL, true, true)
    gridData.widthHint = new PixelConverter(projectsList.getControl()).convertWidthInCharsToPixels(25)
    gridData.heightHint = new PixelConverter(projectsList.getControl()).convertHeightInCharsToPixels(10)
    projectsList.getControl().setLayoutData(gridData)
    projectsList.setContentProvider(new ITreeContentProvider() {
      override def getChildren(parentElement: AnyRef): Array[AnyRef] = null
      override def getElements(inputElement: AnyRef): Array[AnyRef] = model.importableProjects().toArray
      override def hasChildren(element: AnyRef): Boolean = false
      override def getParent(element: AnyRef): AnyRef = null
      override def dispose(): Unit = ()
      override def inputChanged(viewer: Viewer, oldInput: AnyRef, newInput: AnyRef): Unit = ()
    })

    projectsList.setLabelProvider(new ProjectLabelProvider(this))

    projectsList.addCheckStateListener((event: CheckStateChangedEvent) => {
      val element = event.getElement().asInstanceOf[ProjectRecord]
      if (element.hasConflicts)
        projectsList.setChecked(element, false)
      setPageComplete(projectsList.getCheckedElements().nonEmpty)
    })

    projectsList.setInput(this)
    projectsList.setComparator(new ViewerComparator())
    createSelectionButtons(listComposite)
  }

  /** Create the selection buttons in the listComposite.*/
  private def createSelectionButtons(listComposite: Composite): Unit = {
    val buttonsComposite = new Composite(listComposite, SWT.NONE)
    val layout = new GridLayout()
    layout.marginWidth = 0
    layout.marginHeight = 0
    buttonsComposite.setLayout(layout)

    buttonsComposite.setLayoutData(new GridData(
      GridData.VERTICAL_ALIGN_BEGINNING))

    val selectAll = new Button(buttonsComposite, SWT.PUSH)
    selectAll.setText(DataTransferMessages.DataTransfer_selectAll)
    selectAll.addSelectionListener(onWidgetSelected { _ =>
      for (project <- model.selectedProjects)
        projectsList.setChecked(project, !project.hasConflicts)
      setPageComplete(projectsList.getCheckedElements().nonEmpty)
    })
    Dialog.applyDialogFont(selectAll)
    setButtonLayoutData(selectAll)

    val deselectAll = new Button(buttonsComposite, SWT.PUSH)
    deselectAll.setText(DataTransferMessages.DataTransfer_deselectAll)
    deselectAll.addSelectionListener(onWidgetSelected { _ =>
      projectsList.setCheckedElements(Array.empty)
      setPageComplete(false)
    })
    Dialog.applyDialogFont(deselectAll)
    setButtonLayoutData(deselectAll)

    val refresh = new Button(buttonsComposite, SWT.PUSH)
    refresh.setText(DataTransferMessages.DataTransfer_refresh)
    refresh.addSelectionListener(onWidgetSelected { _ =>
      updateProjectsList(directoryPathField.getText().trim())
    })
    Dialog.applyDialogFont(refresh)
    setButtonLayoutData(refresh)
  }

  private def createWorkingSetGroup(workArea: Composite): Unit = {
    val workingSetIds = Array("org.eclipse.ui.resourceWorkingSetPage",
      "org.eclipse.jdt.ui.JavaWorkingSetPage")
    model.workingSetGroup = Some(new WorkingSetGroup(workArea, currentSelection, workingSetIds))
  }

  private def updateProjectsList(path: String): Unit = {
    // on an empty path empty selectedProjects
    if (path == null || path.isEmpty) {
      setMessage(DataTransferMessages.WizardProjectsImportPage_ImportProjectsDescription)
      model.selectedProjects = Seq.empty
      (projectsList: StructuredViewer).refresh(true)
      projectsList.setCheckedElements(model.selectedProjects.toArray)
      setPageComplete(projectsList.getCheckedElements().nonEmpty)
      return
    }

    val directory = new File(path)

    try {
      getContainer().run( /*fork*/ true, /*cancellable*/ true, new IRunnableWithProgress() {
        override def run(monitor: IProgressMonitor): Unit = {
          monitor.beginTask(DataTransferMessages.WizardProjectsImportPage_SearchingMessage, 100)
          monitor.worked(10)
          if (directory.isDirectory()) {
            monitor.worked(50)
            monitor.subTask(DataTransferMessages.WizardProjectsImportPage_ProcessingMessage)
            val projects = collectProjectsReferencesFromDirectory(directory, monitor)
            model.selectedProjects = projects.map(ref => new ProjectRecord(ref))
          }
          else monitor.worked(60)
          monitor.done()
        }
      })
    }
    catch {
      case e: InvocationTargetException => IDEWorkbenchPlugin.log(e.getMessage(), e)
      case e: InterruptedException      => () //FIXME: Should at least set current thread status interrupted?
    }

    (projectsList: StructuredViewer).refresh(true)
    val importableProjects = model.importableProjects()
    var displayWarning = false

    for (project <- importableProjects) {
      if (project.hasConflicts) {
        displayWarning = true
        projectsList.setGrayed(project, true)
      }
      else projectsList.setChecked(project, true)
    }

    if (displayWarning)
      setMessage(DataTransferMessages.WizardProjectsImportPage_projectsInWorkspace, IMessageProvider.WARNING)
    else
      setMessage(DataTransferMessages.WizardProjectsImportPage_ImportProjectsDescription)

    setPageComplete(projectsList.getCheckedElements().nonEmpty)
    if (model.selectedProjects.isEmpty)
      setMessage(DataTransferMessages.WizardProjectsImportPage_noProjectsToImport, IMessageProvider.WARNING)

  }

  private def collectProjectsReferencesFromDirectory(directory: File, monitor: IProgressMonitor): Seq[ProjectReference] = {
    if (!monitor.isCanceled()) {
      monitor.subTask(NLS.bind(DataTransferMessages.WizardProjectsImportPage_CheckingMessage, directory.getPath()))
      val promise = Promise[Seq[ProjectReference]]

      import scala.concurrent.ExecutionContext.Implicits.global
      for (client <- SbtClientProvider.sbtClientFor(directory)) {
        // FIXME: How to unregister this listener?
        // FIXME: Likely not a good idea to have the listener registered here. Furthermore, it looks like making this call twice 
        //        can freeze the UI as no event is sent back and the call to Await.result is definitely not helping. 
        client.watchBuild {
          case build: MinimalBuildStructure =>
            promise.success(build.projects.toList)
        }
      }
      Await.result(promise.future, scala.concurrent.duration.Duration.Inf)
    }
    else Seq.empty
  }

  override def handleEvent(event: Event): Unit = ()
  override def allowNewContainerName(): Boolean = true

  override def setVisible(visible: Boolean): Unit = {
    super.setVisible(visible)
    this.directoryPathField.setFocus()
  }

  // TODO: Create the Eclipse projects 
  def createProjects(): Boolean = {
//    val selected = projectsList.getCheckedElements().asInstanceOf[Array[ProjectRecord]]
//
//    val op = withWorkspaceModifyOperation { monitor =>
//      try {
//        monitor.beginTask("", selected.length)
//        if (monitor.isCanceled()) throw new OperationCanceledException()
//        selected.foreach(createExistingProject(_, new SubProgressMonitor(monitor, 1)))
//      }
//      finally monitor.done()
//    }
//
//    try getContainer().run( /*fork*/ true, /*cancellable*/ true, op)
//    catch {
//      case _: InterruptedException => return false
//      case e: InvocationTargetException =>
//        val t = e.getTargetException()
//        val message = DataTransferMessages.WizardExternalProjectImportPage_errorMessage;
//        val status = t match {
//          case t: CoreException => t.getStatus()
//          case t                => new Status(IStatus.ERROR, IDEWorkbenchPlugin.IDE_WORKBENCH, 1, message, t)
//        }
//        ErrorDialog.openError(getShell(), message, null, status)
//        return false
//    }
//
    true
  }
//
//  private def createExistingProject(record: ProjectRecord, monitor: IProgressMonitor): Unit = {
//    def addToWorkingSets(project: IProject): Unit = {
//      lazy val workingSetManager = PlatformUI.getWorkbench().getWorkingSetManager()
//
//      for {
//        workingGroup <- model.workingSetGroup
//        selectedWorkingSets <- Option(workingGroup.getSelectedWorkingSets)
//        if selectedWorkingSets.nonEmpty
//      } workingSetManager.addToWorkingSets(project, selectedWorkingSets)
//    }
//
//    val projectName = record.name
//    val workspace = ResourcesPlugin.getWorkspace()
//    val project = workspace.getRoot().getProject(projectName)
//    model.addCreatedProject(project)
//    addToWorkingSets(project)
//  }
}
/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.ui.internal.repl

import org.eclipse.core.resources.IResource
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.runtime.CoreException
import org.eclipse.debug.core.ILaunchConfiguration
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy
import org.eclipse.debug.ui.AbstractLaunchConfigurationTabGroup
import org.eclipse.debug.ui.CommonTab
import org.eclipse.debug.ui.EnvironmentTab
import org.eclipse.debug.ui.ILaunchConfigurationDialog
import org.eclipse.debug.ui.ILaunchConfigurationTab
import org.eclipse.debug.ui.sourcelookup.SourceLookupTab
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.JavaCore
import org.eclipse.jdt.core.JavaModelException
import org.eclipse.jdt.debug.ui.launchConfigurations.JavaArgumentsTab
import org.eclipse.jdt.debug.ui.launchConfigurations.JavaClasspathTab
import org.eclipse.jdt.debug.ui.launchConfigurations.JavaJRETab
import org.eclipse.jdt.debug.ui.launchConfigurations.JavaLaunchTab
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants
import org.eclipse.jdt.ui.JavaElementLabelProvider
import org.eclipse.jface.window.Window
import org.eclipse.swt.SWT
import org.eclipse.swt.layout.FillLayout
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.widgets.Button
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Group
import org.eclipse.swt.widgets.Text
import org.eclipse.ui.dialogs.ElementListSelectionDialog
import org.scalaide.core.internal.project.ScalaProject
import org.scalaide.core.internal.repl.InterpreterLaunchConstants
import org.scalaide.logging.HasLogger
import org.scalaide.util.eclipse.SWTUtils

/**
 * This defines the configuration UI for a scala interpeter launch configuration.
 */
class InterpreterTabGroup extends AbstractLaunchConfigurationTabGroup {
  override def createTabs(dialog: ILaunchConfigurationDialog, mode: String) = {
    setTabs(Array[ILaunchConfigurationTab](
      new InterpreterMainTab(),
      new JavaArgumentsTab(),
      new JavaJRETab(),
      new JavaClasspathTab(),
      new SourceLookupTab(),
      new EnvironmentTab(),
      new CommonTab()))
  }
}

/** This class allows selection of scala projects.*/
class InterpreterMainTab extends JavaLaunchTab with HasLogger {
  import SWTUtils.noArgFnToSelectionAdapter
  import SWTUtils.noArgFnToModifyListener

  protected var seedScriptText: Text = _
  /**
   * @see ILaunchConfigurationTab#isValid(ILaunchConfiguration)
   */
  override def isValid(launchConfig: ILaunchConfiguration): Boolean = {
    setErrorMessage(null)
    setMessage(null)
    val name = fProjText.getText().trim()
    if (name.length() > 0) {
      val workspace = ResourcesPlugin.getWorkspace()
      val status = workspace.validateName(name, IResource.PROJECT)
      if (status.isOK()) {
        val project = ResourcesPlugin.getWorkspace().getRoot().getProject(name)
        if (!project.exists()) {
          setErrorMessage("Project does not exist")
          return false
        }
        if (!project.isOpen()) {
          setErrorMessage("Project is not open")
          return false
        }
      } else {
        setErrorMessage("Name does not exist on workspace.")
        return false
      }
    }
    //TODO - Validate seed script is ok?
    return true
  }

  /* (non-Javadoc)
   * @see org.eclipse.debug.ui.ILaunchConfigurationTab#createControl(org.eclipse.swt.widgets.Composite)
   */
  override def createControl(parent: Composite): Unit = {
    val comp = new Composite(parent, SWT.NONE)
    comp.setLayout(new GridLayout(1, false))
    comp.setFont(parent.getFont)
    val gd = new GridData(GridData.FILL_BOTH)
    gd.horizontalSpan = 1
    comp.setLayoutData(gd)
    comp.getLayout().asInstanceOf[GridLayout].verticalSpacing = 0
    createProjectEditor(comp)
    createSeedScriptEditor(comp)
    setControl(comp)
  }

  override def getName() = "Main"

  var fProjText: Text = _
  var fProjButton: Button = _

  /**
   * Creates the widgets for specifying a main type.
   *
   * @param parent the parent composite
   */
  protected def createProjectEditor(parent: Composite): Unit = {
    val font = parent.getFont()
    val group = new Group(parent, SWT.NONE)
    group.setText("Project Setup")
    var gd = new GridData(GridData.FILL_HORIZONTAL)
    group.setLayoutData(gd)
    val layout = new GridLayout()
    layout.numColumns = 2
    group.setLayout(layout)
    group.setFont(font)
    fProjText = new Text(group, SWT.SINGLE | SWT.BORDER)
    gd = new GridData(GridData.FILL_HORIZONTAL)
    fProjText.setLayoutData(gd)
    fProjText.setFont(font)
    fProjText.addModifyListener(() => updateLaunchConfigurationDialog())
    fProjButton = createPushButton(group, "Scala Project", null)
    fProjButton.addSelectionListener(() => handleProjectButtonSelected())
  }

  protected def createSeedScriptEditor(parent: Composite): Unit = {
    val group = new Group(parent, SWT.NONE)
    group.setText("Interpreter Seed Script")
    group.setLayoutData(new GridData(GridData.FILL_HORIZONTAL | GridData.FILL_VERTICAL))
    group.setLayout(new FillLayout)
    seedScriptText = new Text(group, SWT.MULTI | SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL)
  }

  private def handleProjectButtonSelected(): Unit = {
    val project = chooseScalaProject()
    if (project == null) {
      return
    }
    val projectName = project.getElementName()
    fProjText.setText(projectName)
  }

  /**
   * chooses a project for the type of java launch config that it is
   * @return
   */
  private def chooseScalaProject(): IJavaProject = {

    val labelProvider = new JavaElementLabelProvider(JavaElementLabelProvider.SHOW_DEFAULT)
    val dialog = new ElementListSelectionDialog(getShell(), labelProvider)
    dialog.setTitle("Select a Scala Project")
    dialog.setMessage("")
    try {
      val scalaProjects = JavaCore.create(getWorkspaceRoot).getJavaProjects.filter(ScalaProject.isScalaProject).toArray[Object]
      dialog.setElements(scalaProjects)
    } catch {
      case jme: JavaModelException =>
        //TODO - Log
        eclipseLog.error("Java model exception", jme)
    }
    val scalaProject = getScalaProject()
    if (scalaProject != null) {
      dialog.setInitialSelections((scalaProject :: Nil).toArray)
    }
    if (dialog.open() == Window.OK) {
      return dialog.getFirstResult().asInstanceOf[IJavaProject]
    }
    return null
  }

  /**
   * Convenience method to get access to the java model.
   */
  private def getJavaModel() = JavaCore.create(getWorkspaceRoot())

  /**
   * Return the IJavaProject corresponding to the project name in the project name
   * text field, or null if the text does not match a project name.
   */
  protected def getScalaProject(): IJavaProject = {
    val projectName = fProjText.getText().trim()
    if (projectName.length() < 1) {
      return null
    }
    getJavaModel().getJavaProject(projectName)
  }

  /**
   * Convenience method to get the workspace root.
   */
  protected def getWorkspaceRoot() = ResourcesPlugin.getWorkspace().getRoot()

  /* (non-Javadoc)
   * @see org.eclipse.debug.ui.ILaunchConfigurationTab#initializeFrom(org.eclipse.debug.core.ILaunchConfiguration)
   */
  override def initializeFrom(config: ILaunchConfiguration): Unit = {
    updateProjectFromConfig(config)
    updateSeedScriptFromConfig(config)
    super.initializeFrom(config)
  }

  /**
   * updates the project text field form the configuration
   * @param config the configuration we are editing
   */
  private def updateProjectFromConfig(config: ILaunchConfiguration): Unit = {
    var projectName = ""
    try {
      projectName = config.getAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, "")
    } catch {
      case ce: CoreException =>
        setErrorMessage(ce.getStatus().getMessage())
    }
    if (fProjText != null) {
      fProjText.setText(projectName)
    }
  }

  private def updateSeedScriptFromConfig(config: ILaunchConfiguration): Unit = {
    if (seedScriptText != null) {
      seedScriptText.setText(config.getAttribute(InterpreterLaunchConstants.SEED_SCRIPT, ""))
    }
  }

  /**
   * Maps the config to associated java resource
   *
   */
  protected def mapResources(): Unit = {
    try {
      //CONTEXTLAUNCHING
      val javaProject = getScalaProject()
      if (javaProject != null && javaProject.exists() && javaProject.isOpen()) {
        //TODO - What do we do here...
      }
    } catch {
      case ce: CoreException =>
        setErrorMessage(ce.getStatus().getMessage())
    }
  }

  /* (non-Javadoc)
   * @see org.eclipse.debug.ui.ILaunchConfigurationTab#performApply(org.eclipse.debug.core.ILaunchConfigurationWorkingCopy)
   */
  override def performApply(config: ILaunchConfigurationWorkingCopy): Unit = {
    config.setAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, fProjText.getText().trim())
    config.setAttribute(InterpreterLaunchConstants.SEED_SCRIPT, seedScriptText.getText.trim())
    mapResources()
  }

  /* (non-Javadoc)
   * @see org.eclipse.debug.ui.ILaunchConfigurationTab#setDefaults(org.eclipse.debug.core.ILaunchConfigurationWorkingCopy)
   */
  override def setDefaults(config: ILaunchConfigurationWorkingCopy): Unit = {
    val javaElement = getContext()
    if (javaElement != null) {
      initializeJavaProject(javaElement, config)
    } else {
      config.setAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, "")
      config.setAttribute(InterpreterLaunchConstants.SEED_SCRIPT, "")
    }
  }
}

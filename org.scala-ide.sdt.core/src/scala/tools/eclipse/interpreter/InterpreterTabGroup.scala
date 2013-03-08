/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.interpreter

import org.eclipse.debug.ui._
import org.eclipse.debug.ui.sourcelookup._
import org.eclipse.jdt.debug.ui.launchConfigurations._
import org.eclipse.jdt.ui._
import org.eclipse.jdt.core._
import org.eclipse.jdt.debug.ui.launchConfigurations._
import org.eclipse.debug.core._
import org.eclipse.swt.widgets._
import org.eclipse.swt.layout._
import org.eclipse.swt.events._
import org.eclipse.swt._
import org.eclipse.core.resources._
import org.eclipse.jface.window._
import org.eclipse.ui.dialogs._
import org.eclipse.core.runtime._
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants
import scala.tools.eclipse.ScalaPlugin
import scala.tools.eclipse.logging.HasLogger

/**
 * This defines the configuration UI for a scala interpeter launch configuration.
 */
class InterpreterTabGroup extends AbstractLaunchConfigurationTabGroup {
  override def createTabs(dialog : ILaunchConfigurationDialog, mode : String) = {
    setTabs(Array[ILaunchConfigurationTab](
      new InterpreterMainTab(),
      new JavaArgumentsTab(),
      new JavaJRETab(),
      new JavaClasspathTab(),
      new SourceLookupTab(),
      new EnvironmentTab(),
      new CommonTab()
    ))
  }
}



/** This class allows selection of scala projects.*/
class InterpreterMainTab extends JavaLaunchTab with HasLogger {
  protected var seedScriptText : Text = _
   /**
    * @see ILaunchConfigurationTab#isValid(ILaunchConfiguration)
    */
  override def isValid(launchConfig : ILaunchConfiguration) : Boolean =  {
    setErrorMessage(null)
    setMessage(null)
    val name = fProjText.getText().trim()
    if (name.length() > 0) {
      val workspace = ResourcesPlugin.getWorkspace()
      val status = workspace.validateName(name, IResource.PROJECT)
      if (status.isOK()) {
        val project= ResourcesPlugin.getWorkspace().getRoot().getProject(name)
        if (!project.exists()) {
          setErrorMessage("Project does not exist")
          return false
        }
        if (!project.isOpen()) {
          setErrorMessage("Project is not open");
          return false
        }
      } else {
        setErrorMessage("Name does not exist on workspace.");
        return false;
      }
    }
        //TODO - Validate seed script is ok?
    return true;
  }

   /* (non-Javadoc)
   * @see org.eclipse.debug.ui.ILaunchConfigurationTab#createControl(org.eclipse.swt.widgets.Composite)
   */
  override def createControl(parent : Composite ) {
    val comp =new Composite(parent, SWT.NONE);
    comp.setLayout(new GridLayout(1, false));
    comp.setFont(parent.getFont);
    val gd = new GridData(GridData.FILL_BOTH);
    gd.horizontalSpan = 1;
    comp.setLayoutData(gd);
    comp.getLayout().asInstanceOf[GridLayout].verticalSpacing = 0;
    createProjectEditor(comp);
    //createVerticalSpacer(comp, 1);
    //createMainTypeEditor(comp, LauncherMessages.JavaMainTab_Main_cla_ss__4);
    createSeedScriptEditor(comp)
    setControl(comp);
  }

  override def getName() = "Main"

  var fProjText : Text = _
  var fProjButton : Button = _

    /**
   * Creates the widgets for specifying a main type.
   *
   * @param parent the parent composite
   */
  protected def createProjectEditor(parent : Composite) {
    val font= parent.getFont()
    val group= new Group(parent, SWT.NONE)
    group.setText("Project Setup");
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
    fProjText.addModifyListener(new ModifyListener() {
      def modifyText(e : ModifyEvent) {
        updateLaunchConfigurationDialog()
      }
    })
    fProjButton = createPushButton(group, "Scala Project", null);
    fProjButton.addSelectionListener(new SelectionAdapter() {
      override def widgetSelected(e : SelectionEvent) {
        handleProjectButtonSelected()
      }
    })
  }

  protected def createSeedScriptEditor(parent : Composite) {
    val group = new Group(parent, SWT.NONE)
    group.setText("Interpreter Seed Script")
    group.setLayoutData(new GridData(GridData.FILL_HORIZONTAL | GridData.FILL_VERTICAL))
    group.setLayout(new FillLayout)
    seedScriptText = new Text(group, SWT.MULTI | SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL)
  }

  private def handleProjectButtonSelected() {
    val project = chooseScalaProject();
    if (project == null) {
      return;
    }
    val projectName = project.getElementName();
    fProjText.setText(projectName);
  }

  /**
   * chooses a project for the type of java launch config that it is
   * @return
   */
  private def chooseScalaProject() : IJavaProject = {

    val labelProvider= new JavaElementLabelProvider(JavaElementLabelProvider.SHOW_DEFAULT);
    val dialog = new ElementListSelectionDialog(getShell(), labelProvider);
    dialog.setTitle("Select a Scala Project");
    dialog.setMessage("");
    try {
      val scalaProjects = JavaCore.create(getWorkspaceRoot).getJavaProjects.filter(ScalaPlugin.plugin.isScalaProject).toArray[Object]
      dialog.setElements(scalaProjects)
    }
    catch {
      case jme : JavaModelException =>
        //TODO - Log
        eclipseLog.error("Java model exception", jme);
    }
    val scalaProject= getScalaProject();
    if (scalaProject != null) {
      dialog.setInitialSelections((scalaProject :: Nil).toArray);
    }
    if (dialog.open() == Window.OK) {
      return dialog.getFirstResult().asInstanceOf[IJavaProject];
    }
    return null;
  }

 /**
   * Convenience method to get access to the java model.
   */
  private def getJavaModel() = JavaCore.create(getWorkspaceRoot())

  /**
   * Return the IJavaProject corresponding to the project name in the project name
   * text field, or null if the text does not match a project name.
   */
  protected def getScalaProject() : IJavaProject = {
    val projectName = fProjText.getText().trim();
    if (projectName.length() < 1) {
      return null;
    }
    getJavaModel().getJavaProject(projectName);
  }

  /**
   * Convenience method to get the workspace root.
   */
  protected def getWorkspaceRoot() = ResourcesPlugin.getWorkspace().getRoot();

 /* (non-Javadoc)
   * @see org.eclipse.debug.ui.ILaunchConfigurationTab#initializeFrom(org.eclipse.debug.core.ILaunchConfiguration)
   */
  override def initializeFrom(config : ILaunchConfiguration) {
    updateProjectFromConfig(config);
    updateSeedScriptFromConfig(config)
    super.initializeFrom(config);
  }

  /**
   * updates the project text field form the configuration
   * @param config the configuration we are editing
   */
  private def updateProjectFromConfig(config : ILaunchConfiguration) {
    var projectName = "";
    try {
      projectName = config.getAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, "");
    }
    catch {
      case  ce : CoreException =>
        setErrorMessage(ce.getStatus().getMessage())
    }
    if(fProjText != null) {
      fProjText.setText(projectName)
    }
  }

  private def updateSeedScriptFromConfig(config : ILaunchConfiguration) {
    if(seedScriptText != null) {
      seedScriptText.setText(config.getAttribute(InterpreterLaunchConstants.SEED_SCRIPT,""))
    }
  }

  /**
   * Maps the config to associated java resource
   *
   * @param config
   */
  protected def mapResources( config : ILaunchConfigurationWorkingCopy)  {
    try {
      //CONTEXTLAUNCHING
      val javaProject = getScalaProject()
      if (javaProject != null && javaProject.exists() && javaProject.isOpen()) {
        //TODO - What do we do here...
      }
    } catch {
      case  ce : CoreException =>
        setErrorMessage(ce.getStatus().getMessage())
    }
  }


  /* (non-Javadoc)
   * @see org.eclipse.debug.ui.ILaunchConfigurationTab#performApply(org.eclipse.debug.core.ILaunchConfigurationWorkingCopy)
   */
  override def performApply(config : ILaunchConfigurationWorkingCopy)  {
    config.setAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, fProjText.getText().trim())
    config.setAttribute(InterpreterLaunchConstants.SEED_SCRIPT, seedScriptText.getText.trim())
    mapResources(config)
  }

  /* (non-Javadoc)
   * @see org.eclipse.debug.ui.ILaunchConfigurationTab#setDefaults(org.eclipse.debug.core.ILaunchConfigurationWorkingCopy)
   */
  override def setDefaults(config : ILaunchConfigurationWorkingCopy ) {
    val javaElement = getContext()
    if (javaElement != null) {
      initializeJavaProject(javaElement, config)
    } else {
      config.setAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, "")
      config.setAttribute(InterpreterLaunchConstants.SEED_SCRIPT,"")
    }
  }
}

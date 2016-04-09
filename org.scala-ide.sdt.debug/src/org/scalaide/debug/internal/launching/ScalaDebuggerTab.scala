package org.scalaide.debug.internal.launching

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.util.Properties
import scala.util.Try

import org.eclipse.core.runtime.CoreException
import org.eclipse.debug.core.ILaunchConfiguration
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy
import org.eclipse.debug.internal.ui.SWTFactory
import org.eclipse.debug.ui.AbstractLaunchConfigurationTab
import org.eclipse.jface.dialogs.Dialog
import org.eclipse.pde.internal.ui.IHelpContextIds
import org.eclipse.swt.SWT
import org.eclipse.swt.events.ModifyEvent
import org.eclipse.swt.events.ModifyListener
import org.eclipse.swt.events.SelectionAdapter
import org.eclipse.swt.events.SelectionEvent
import org.eclipse.swt.graphics.Image
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.widgets.Button
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Text
import org.eclipse.ui.PlatformUI
import org.scalaide.ui.ScalaImages

private class GeneralSettingsBlock(val tab: ScalaDebuggerTab) {
  import ScalaDebuggerConfiguration._

  private var globalAsyncDebuggerSwitchButton: Button = _
  private var stepOutExcludePackages: Text = _
  val SettingsArea = "General Settings"
  val StepMessageOutExcludePackages = "Define all package/class names where Step Out Message should NOT stop in"
  val StepOutSettings = "Step Out Message Settings"
  val AsyncDebuggerGlobalSwitch = "Enable Async Stack Trace functionality"
  val DataDelimiter = Properties.lineSeparator

  def createControl(parent: Composite): Unit = {
    val generalGroup = SWTFactory.createGroup(parent, SettingsArea, 1, 1, GridData.FILL_HORIZONTAL)
    globalAsyncDebuggerSwitchButton = SWTFactory.createCheckButton(generalGroup, AsyncDebuggerGlobalSwitch, null, false, 1)
    globalAsyncDebuggerSwitchButton.addSelectionListener(new SelectionAdapter() {
      override def widgetSelected(e: SelectionEvent): Unit = {
        tab.scheduleUpdateJob()
      }
    })
    generalGroup.pack()

    val stepOutGroup = SWTFactory.createGroup(parent, StepOutSettings, 1, 1, GridData.FILL_HORIZONTAL)
    SWTFactory.createLabel(stepOutGroup, StepMessageOutExcludePackages, 1)
    stepOutExcludePackages = SWTFactory.createText(stepOutGroup,
      SWT.MULTI | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL, 1, 100, 40, GridData.FILL_BOTH)
    stepOutExcludePackages.addModifyListener(new ModifyListener() {
      override def modifyText(me: ModifyEvent): Unit = {
        tab.scheduleUpdateJob()
      }
    })
    stepOutGroup.pack()
  }

  def setDefaults(configuration: ILaunchConfigurationWorkingCopy): Unit = {}

  /**
   * Initializes the state of UI components from the configuration attributes
   * @param configuration the configuration to get attributes from
   * @throws CoreException if an error occurs getting an attribute
   */
  def initializeFrom(configuration: ILaunchConfiguration): Unit = {
    globalAsyncDebuggerSwitchButton.setSelection(configuration.getAttribute(LaunchWithAsyncDebugger, false))
    stepOutExcludePackages.setText(configuration.getAttribute(StepOutExcludePkgsOrClasses,
      List.empty[String].asJava).asScala.mkString(DataDelimiter))
  }

  /**
   * Sets attributes on the configuration based on the current state of the UI elements
   * @param configuration configuration to modify
   */
  def performApply(configuration: ILaunchConfigurationWorkingCopy): Unit = {
    if (globalAsyncDebuggerSwitchButton.getSelection()) {
      configuration.setAttribute(LaunchWithAsyncDebugger, true)
    } else {
      configuration.removeAttribute(LaunchWithAsyncDebugger)
    }
    configuration.setAttribute(StepOutExcludePkgsOrClasses,
      stepOutExcludePackages.getText.split(DataDelimiter).filter { _.nonEmpty }.toList.asJava)
  }

  /**
   * @return a string error message or <code>null</code> if the block contents are valid
   */
  def validate: String = null
}

class ScalaDebuggerTab extends AbstractLaunchConfigurationTab {
  val ScalaDebuggerTabTitle = "Scala Debugger"

  private val generalSettingsBlock: GeneralSettingsBlock = new GeneralSettingsBlock(this)
  private val image: Image = ScalaImages.DESC_SETTINGS_OBJ.createImage()

  override def createControl(parent: Composite): Unit = {
    val container = new Composite(parent, SWT.NONE)
    container.setLayout(new GridLayout())
    container.setLayoutData(new GridData(GridData.FILL_BOTH))

    generalSettingsBlock.createControl(container)

    Dialog.applyDialogFont(container)
    setControl(container)
    PlatformUI.getWorkbench().getHelpSystem().setHelp(getControl(), IHelpContextIds.LAUNCHER_CONFIGURATION)
  }

  override def setDefaults(configuration: ILaunchConfigurationWorkingCopy): Unit = {
    generalSettingsBlock.setDefaults(configuration)
  }

  override def initializeFrom(configuration: ILaunchConfiguration): Unit = Try {
    generalSettingsBlock.initializeFrom(configuration)
  } recover {
    case eatQuietly: CoreException =>
    case rethrow => throw rethrow
  }

  override def performApply(configuration: ILaunchConfigurationWorkingCopy): Unit = {
    generalSettingsBlock.performApply(configuration)
  }

  override def getId: String = "org.scalaide.debug.ui.scalaDebuggerTab"
  override def getName: String = ScalaDebuggerTabTitle
  override def getImage: Image = image

  override def dispose(): Unit =
    if (image != null)
      image.dispose()

  def validateTab(): Unit = {
    setErrorMessage(generalSettingsBlock.validate)
  }

  override def isValid(config: ILaunchConfiguration): Boolean = getErrorMessage() == null

  override def activated(workingCopy: ILaunchConfigurationWorkingCopy): Unit = {}
  override def deactivated(workingCopy: ILaunchConfigurationWorkingCopy): Unit = {}

  override def scheduleUpdateJob(): Unit = {
    validateTab()
    super.scheduleUpdateJob()
  }
}

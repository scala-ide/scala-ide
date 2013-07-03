/*
 * Copyright 2005-2010 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.eclipse.properties

import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.core.resources.IProject
import org.eclipse.core.runtime.preferences.IEclipsePreferences
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jface.preference.PreferencePage
import org.eclipse.jface.preference.IPersistentPreferenceStore
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.ui.IWorkbench
import org.eclipse.ui.IWorkbenchPreferencePage
import org.eclipse.ui.dialogs.PropertyPage
import org.eclipse.swt.SWT
import org.eclipse.swt.events.ModifyEvent
import org.eclipse.swt.events.ModifyListener
import org.eclipse.swt.events.SelectionAdapter
import org.eclipse.swt.events.SelectionEvent
import org.eclipse.swt.events.SelectionListener
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.layout.RowLayout
import org.eclipse.swt.widgets.Button
import org.eclipse.swt.widgets.Combo
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Group
import org.eclipse.swt.widgets.Label
import org.eclipse.swt.widgets.Control
import org.eclipse.swt.widgets.TabFolder
import org.eclipse.swt.widgets.TabItem
import org.eclipse.swt.widgets.Text
import scala.tools.nsc.Settings
import scala.tools.eclipse.ScalaPlugin
import scala.tools.eclipse.SettingConverterUtil
import scala.tools.eclipse.util.SWTUtils
import org.eclipse.jface.fieldassist.FieldDecorationRegistry
import org.eclipse.jface.fieldassist.ControlDecoration
import org.eclipse.swt.events.VerifyEvent
import scala.tools.nsc.CompilerCommand
import org.eclipse.jface.fieldassist._
import org.eclipse.jface.bindings.keys.KeyStroke
import scala.tools.eclipse.logging.HasLogger
import scala.tools.eclipse.buildmanager.ProjectsCleanJob

trait ScalaPluginPreferencePage extends HasLogger {
  self: PreferencePage with EclipseSettings =>

  val eclipseBoxes: List[EclipseSetting.EclipseBox]

  def isChanged: Boolean = eclipseBoxes.exists(_.eSettings.exists(_.isChanged))

  override def performDefaults() {
    eclipseBoxes.foreach(_.eSettings.foreach(_.reset()))
  }

  def save(userBoxes: List[IDESettings.Box], store: IPreferenceStore): Unit = {
    for (b <- userBoxes) {
      for (setting <- b.userSettings) {
        val name = SettingConverterUtil.convertNameToProperty(setting.name)
        val isDefault = setting match {
          case bs: Settings#BooleanSetting     =>
            // use the store default if it is defined: e.i. it is not a sbt/scalac preference
            bs.value == store.getDefaultBoolean(name)
          case is: Settings#IntSetting         => is.value == is.default
          case ss: Settings#StringSetting      => ss.value == ss.default
          case ms: Settings#MultiStringSetting => ms.value == Nil
          case cs: Settings#ChoiceSetting      => cs.value == cs.default
        }
        if (isDefault)
          store.setToDefault(name)
        else {
          val value = setting match {
            case ms: Settings#MultiStringSetting => ms.value.mkString(",")
            case setting                         => setting.value.toString
          }
          store.setValue(name, value)
        }
      }
    }

    store match {
      case savable: IPersistentPreferenceStore => savable.save()
    }
  }

  // There seems to be a bug in the compiler that appears in runtime (#2296)
  // So updateApply is going to forward to real updateApplyButton
  def updateApply(): Unit
}

/** Provides a property page to allow Scala compiler settings to be changed.
 */
class CompilerSettings extends PropertyPage with IWorkbenchPreferencePage with EclipseSettings
  with ScalaPluginPreferencePage {
  //TODO - Use setValid to enable/disable apply button so we can only click the button when a property/preference
  // has changed from the saved value

  protected var isWorkbenchPage = false

  override def init(workbench: IWorkbench) {
    isWorkbenchPage = true
  }

  lazy val preferenceStore0: IPreferenceStore = {
    /** The project for which we are setting properties */
    val project = getElement() match {
      case project: IProject         => Some(project)
      case javaProject: IJavaProject => Some(javaProject.getProject())
      case other                     => None // We're a Preference page!
    }
    if (project.isEmpty)
      super.getPreferenceStore()
    else
      new PropertyStore(project.get, super.getPreferenceStore(), getPageId)
  }

  /** Returns the id of what preference page we use */
  def getPageId = ScalaPlugin.plugin.pluginId

  import EclipseSetting.toEclipseBox
  /** The settings we can change */
  lazy val userBoxes = IDESettings.shownSettings(ScalaPlugin.defaultScalaSettings()) ++ IDESettings.buildManagerSettings
  lazy val eclipseBoxes = userBoxes.map { s => toEclipseBox(s, preferenceStore0) }

  /** Pulls the preference store associated with this plugin */
  override def doGetPreferenceStore(): IPreferenceStore = {
    ScalaPlugin.prefStore
  }

  var useProjectSettingsWidget: Option[UseProjectSettingsWidget] = None
  var additionalParamsWidget: AdditionalParametersWidget = _

  def save(): Unit = {
    if (useProjectSettingsWidget.isDefined) {
      useProjectSettingsWidget.get.save
    }
    additionalParamsWidget.save()

    //This has to come later, as we need to make sure the useProjectSettingsWidget's values make it into
    //the final save.
    save(userBoxes, preferenceStore0)

    //Don't let user click "apply" again until a change
    updateApplyButton
  }

  def updateApply() {
    updateApplyButton
  }

  /** Updates the apply button with the appropriate enablement. */
  protected override def updateApplyButton(): Unit = {
    if (getApplyButton != null) {
      if (isValid) {
        getApplyButton.setEnabled(isChanged)
      } else {
        getApplyButton.setEnabled(false)
      }
    }
  }

  // Eclipse PropertyPage API
  def createContents(parent: Composite): Control = {
    val composite = {
      if (isWorkbenchPage) {
        //No Outer Composite
        val tmp = new Composite(parent, SWT.NONE)
        val layout = new GridLayout(1, false)
        tmp.setLayout(layout)
        val data = new GridData(GridData.FILL)
        data.grabExcessHorizontalSpace = true
        data.horizontalAlignment = GridData.FILL
        tmp.setLayoutData(data)
        tmp
      } else {
        //Create "Use Workspace Settings" button if on properties page...
        val outer = new Composite(parent, SWT.NONE)
        outer.setLayout(new GridLayout(1, false))
        useProjectSettingsWidget = Some(new UseProjectSettingsWidget())
        useProjectSettingsWidget.get.addTo(outer)
        val tmp = new Group(outer, SWT.SHADOW_ETCHED_IN)
        tmp.setText("Project Compiler Settings")
        val layout = new GridLayout(1, false)
        tmp.setLayout(layout)
        val data = new GridData(GridData.FILL)
        data.grabExcessHorizontalSpace = true
        data.horizontalAlignment = GridData.FILL
        tmp.setLayoutData(data)
        tmp
      }
    }

    val tabFolder = new TabFolder(composite, SWT.TOP)

    eclipseBoxes.foreach(eBox => {
      val group = new Group(tabFolder, SWT.SHADOW_ETCHED_IN)
      group.setText(eBox.name)
      val layout = new GridLayout(3, false)
      group.setLayout(layout)
      val data = new GridData(GridData.FILL)
      data.grabExcessHorizontalSpace = true
      data.horizontalAlignment = GridData.FILL
      group.setLayoutData(data)
      eBox.eSettings.foreach(_.addTo(group))

      val tabItem = new TabItem(tabFolder, SWT.NONE)
      tabItem.setText(eBox.name)
      tabItem.setControl(group)
    })

    additionalParamsWidget = (new AdditionalParametersWidget).addTo(composite)

    //Make sure we check enablement of compiler settings here...
    useProjectSettingsWidget match {
      case Some(widget) => widget.handleToggle
      case None         =>
    }

    tabFolder.pack()
    composite
  }

  /** We override this so we can update the status of the apply button after all components have been added */
  override def createControl(parent: Composite): Unit = {
    super.createControl(parent)
    updateApplyButton
  }

  /** Check who needs to rebuild with new compiler flags */
  private def buildIfNecessary() = {
    val projects: Seq[IProject] = getElement() match {
      case project: IProject =>
        //Make sure project is rebuilt
        Seq(project)
      case javaProject: IJavaProject =>
        //Make sure project is rebuilt
        Seq(javaProject.getProject())
      case other =>
        // rebuild all Scala projects that use global settings
        val plugin = ScalaPlugin.plugin

        for {
          p <- (plugin.workspaceRoot.getProjects())
          scalaProject <- plugin.asScalaProject(p)
          if !scalaProject.usesProjectSettings
        } yield scalaProject.underlying
    }

    ProjectsCleanJob(projects).schedule()
  }

  // Eclipse PropertyPage API
  override def performOk = try {
    eclipseBoxes.foreach(_.eSettings.foreach(_.apply()))
    save()
    buildIfNecessary()
    true
  } catch {
    case ex: Throwable => eclipseLog.error(ex); false
  }

  //Make sure apply button isn't available until it should be
  override def isChanged: Boolean = {
    useProjectSettingsWidget match {
      case Some(widget) =>
        if (widget.isChanged) {
          return true
        } else {
          // Make sure we don't check the settings of the GUI if they're all disabled
          // and the "use Project settings" is disabled
          if (!widget.isUseEnabled)
            return false
        }
      case None => //don't need to check
    }

    logger.info(eclipseBoxes.exists { box =>
      logger.info(box.eSettings.find(_.isChanged).toString)
      box.eSettings.exists(_.isChanged)
    }.toString)

    //check all our other settings
    additionalParamsWidget.isChanged || super.isChanged
  }

  override def performDefaults() {
    super.performDefaults
    additionalParamsWidget.reset
  }

  /** This widget should only be used on property pages. */
  class UseProjectSettingsWidget {
    import SettingConverterUtil._

    // TODO - Does this belong here?  For now it's the only place we can really check...
    if (!preferenceStore0.contains(USE_PROJECT_SETTINGS_PREFERENCE)) {
      preferenceStore0.setDefault(USE_PROJECT_SETTINGS_PREFERENCE, false)
    }

    var control: Button = _
    def layout = new GridData()

    /** Pulls our current value from the preference store */
    private def getValue = preferenceStore0.getBoolean(USE_PROJECT_SETTINGS_PREFERENCE)

    /** Adds our widget to the Property Page */
    def addTo(page: Composite) = {
      //Create Check Box
      control = new Button(page, SWT.CHECK)
      control.setText("Use Project Settings")
      control.setSelection(getValue)
      control.redraw
      control.addSelectionListener(new SelectionListener() {
        override def widgetDefaultSelected(e: SelectionEvent) {}
        override def widgetSelected(e: SelectionEvent) { handleToggle }
      })
    }

    /** Toggles the use of a property page */
    def handleToggle() {
      val selected = control.getSelection
      eclipseBoxes.foreach(_.eSettings.foreach(_.setEnabled(selected)))
      additionalParamsWidget.setEnabled(selected)
      updateApplyButton
    }

    def isChanged = getValue != control.getSelection

    def isUseEnabled = preferenceStore0.getBoolean(USE_PROJECT_SETTINGS_PREFERENCE)

    /** Saves our value into the preference store*/
    def save() {
      preferenceStore0.setValue(USE_PROJECT_SETTINGS_PREFERENCE, control.getSelection)
    }
  }

  // LUC_B: it would be nice to have this widget behave like the other 'EclipseSettings', to avoid unnecessary custom code
  class AdditionalParametersWidget {
    import scala.tools.eclipse.util.SWTUtils._

    var additionalParametersControl: Text = _
    var additionalCompParams = originalValue
    def originalValue = preferenceStore0.getString(CompilerSettings.ADDITIONAL_PARAMS)

    def addTo(parent: Composite): this.type = {
      val additionalGroup = new Composite(parent, SWT.NONE)
      additionalGroup.setLayout(new GridLayout(2, false))

      val grabAllHorizontal = new GridData(GridData.FILL_HORIZONTAL)
      grabAllHorizontal.grabExcessHorizontalSpace = true
      grabAllHorizontal.horizontalIndent = errorIndicator.getImage().getBounds().width + 5
      grabAllHorizontal.horizontalAlignment = GridData.FILL
      additionalGroup.setLayoutData(grabAllHorizontal)

      val txt = new Label(additionalGroup, SWT.BORDER)
      txt.setText("Additional command line parameters:")

      additionalParametersControl = new Text(additionalGroup, SWT.SINGLE | SWT.BORDER)
      additionalParametersControl.setText(additionalCompParams)
      additionalParametersControl.setLayoutData(grabAllHorizontal)

      additionalParametersControl.addModifyListener { (event: ModifyEvent) =>
        var errors = new StringBuffer
        val settings = ScalaPlugin.defaultScalaSettings(e => errors append ("\n" + e))
        val result = settings.processArgumentString(additionalParametersControl.getText())
        if (result._2.nonEmpty || errors.length() > 0) {
          errorDecoration.setDescriptionText(errors.toString)
          setValid(false)
          errorDecoration.show()
        } else {
          setValid(true)
          errorDecoration.hide()
          additionalCompParams = additionalParametersControl.getText()
        }

        updateApplyButton()
      }

      val settings = ScalaPlugin.defaultScalaSettings()
      val proposals = settings.visibleSettings.map(_.name)

      val provider = new IContentProposalProvider {
        def getProposals(contents: String, pos: Int): Array[IContentProposal] = {
          val prefix = if (pos == 0 || pos > contents.length || contents(pos - 1) == ' ') "" else {
            val words = contents.substring(0, pos).split(" ")
            words.last.trim
          }

          (for (p <- proposals.filter(_.startsWith(prefix)))
            yield new ContentProposal(p.substring(prefix.length()), p, null)).toArray[IContentProposal]
        }
      }

      val proposal = new ContentProposalAdapter(additionalParametersControl,
        new TextContentAdapter,
        provider,
        KeyStroke.getInstance("Ctrl+Space"),
        Array('-'))

      proposal.setFilterStyle(ContentProposalAdapter.FILTER_NONE)
      additionalGroup.pack()
      this
    }

    lazy val errorIndicator = FieldDecorationRegistry.getDefault().getFieldDecoration(FieldDecorationRegistry.DEC_ERROR)

    lazy val errorDecoration: ControlDecoration = {
      val decoration = new ControlDecoration(additionalParametersControl, SWT.TOP | SWT.LEFT)
      decoration.setImage(errorIndicator.getImage())
      decoration.setDescriptionText(errorIndicator.getDescription())
      decoration
    }

    def isChanged: Boolean =
      originalValue != additionalCompParams

    def save() {
      preferenceStore0.setValue(CompilerSettings.ADDITIONAL_PARAMS, additionalCompParams)
    }

    def reset() {
      additionalParametersControl.setText(preferenceStore0.getDefaultString(CompilerSettings.ADDITIONAL_PARAMS))
    }

    def setEnabled(value: Boolean) {
      additionalParametersControl.setEnabled(value)
    }
  }
}

object CompilerSettings {
  final val ADDITIONAL_PARAMS = "scala.compiler.additionalParams"
}

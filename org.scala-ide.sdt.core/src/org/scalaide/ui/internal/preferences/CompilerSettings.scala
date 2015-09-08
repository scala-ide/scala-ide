package org.scalaide.ui.internal.preferences

import java.util.concurrent.atomic.AtomicBoolean

import scala.collection.mutable.Publisher
import scala.collection.mutable.Subscriber
import scala.tools.nsc.Settings
import scala.tools.nsc.settings.Final
import scala.tools.nsc.settings.ScalaVersion
import scala.tools.nsc.settings.SpecificScalaVersion

import org.eclipse.core.resources.IProject
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.internal.ui.preferences.PreferencesMessages
import org.eclipse.jface.bindings.keys.KeyStroke
import org.eclipse.jface.dialogs.MessageDialog
import org.eclipse.jface.fieldassist.ContentProposal
import org.eclipse.jface.fieldassist.ContentProposalAdapter
import org.eclipse.jface.fieldassist.ControlDecoration
import org.eclipse.jface.fieldassist.FieldDecorationRegistry
import org.eclipse.jface.fieldassist.IContentProposal
import org.eclipse.jface.fieldassist.IContentProposalProvider
import org.eclipse.jface.fieldassist.TextContentAdapter
import org.eclipse.jface.preference.ComboFieldEditor
import org.eclipse.jface.preference.FieldEditor
import org.eclipse.jface.preference.IPersistentPreferenceStore
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.preference.PreferencePage
import org.eclipse.jface.preference.StringFieldEditor
import org.eclipse.jface.util.IPropertyChangeListener
import org.eclipse.jface.util.PropertyChangeEvent
import org.eclipse.swt.SWT
import org.eclipse.swt.events.ModifyEvent
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Control
import org.eclipse.swt.widgets.Group
import org.eclipse.swt.widgets.TabFolder
import org.eclipse.swt.widgets.TabItem
import org.eclipse.swt.widgets.Text
import org.eclipse.ui.IWorkbench
import org.eclipse.ui.IWorkbenchPreferencePage
import org.eclipse.ui.dialogs.PropertyPage
import org.scalaide.core.IScalaProjectEvent
import org.scalaide.core.ScalaInstallationChange
import org.scalaide.core.SdtConstants
import org.scalaide.core.internal.ScalaPlugin
import org.scalaide.core.internal.builder.ProjectsCleanJob
import org.scalaide.core.internal.compiler.ScalaPresentationCompiler
import org.scalaide.core.internal.project.ScalaInstallation
import org.scalaide.core.internal.project.ScalaInstallationChoice
import org.scalaide.logging.HasLogger
import org.scalaide.ui.internal.project.ScalaInstallationUIProviders
import org.scalaide.util.Utils
import org.scalaide.util.eclipse.EclipseUtils
import org.scalaide.util.eclipse.SWTUtils
import org.scalaide.util.internal.SettingConverterUtil
import org.scalaide.util.ui.DisplayThread

trait ScalaPluginPreferencePage extends HasLogger {
  self: PreferencePage with EclipseSettings =>

  val eclipseBoxes: List[EclipseSetting.EclipseBox]

  def isChanged: Boolean = eclipseBoxes.exists(_.eSettings.exists(_.isChanged))

  override def performDefaults(): Unit = {
    eclipseBoxes.foreach(_.eSettings.foreach(_.reset()))
  }

  def save(userBoxes: List[IDESettings.Box], store: IPreferenceStore): Unit = {
    import SettingConverterUtil._
    for (b <- userBoxes) {
      for (setting <- b.userSettings) {
        val name = SettingConverterUtil.convertNameToProperty(setting.name)
        val isDefault = setting match {
          case bswd: ScalaPluginSettings.BooleanSettingWithDefault =>
            bswd.value == bswd.default
          case bs: Settings#BooleanSetting =>
            // use the store default if it is defined: e.i. it is not a sbt/scalac preference
            bs.value == store.getDefaultBoolean(name)
          case is: Settings#IntSetting => is.value == is.default
          case ss: Settings#StringSetting => ss.value == ss.default
          case ms: Settings#MultiStringSetting => ms.value == Nil
          case cs: Settings#ChoiceSetting => cs.value == cs.default
        }
        if (!store.getBoolean(USE_PROJECT_SETTINGS_PREFERENCE) && isDefault)
          store.setToDefault(name)
        else {
          val value = setting match {
            case ms: Settings#MultiStringSetting => ms.value.mkString(",")
            case setting => setting.value.toString
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

/**
 * Provides a property page to allow Scala compiler settings to be changed.
 */
class CompilerSettings extends PropertyPage with IWorkbenchPreferencePage with EclipseSettings
    with ScalaPluginPreferencePage
    with Subscriber[IScalaProjectEvent, Publisher[IScalaProjectEvent]] {

  //TODO - Use setValid to enable/disable apply button so we can only click the button when a property/preference
  // has changed from the saved value

  protected var isWorkbenchPage = false
  getConcernedProject() flatMap (ScalaPlugin().asScalaProject(_)) foreach (_.subscribe(this))

  override def init(workbench: IWorkbench): Unit = {
    isWorkbenchPage = true
  }

  override def notify(pub: Publisher[IScalaProjectEvent], event: IScalaProjectEvent): Unit = {
    event match { case e: ScalaInstallationChange => save() }
  }
  override def dispose() = {
    getConcernedProject() flatMap (ScalaPlugin().asScalaProject(_)) foreach (_.removeSubscriptions())
    super.dispose()
  }

  def getConcernedProject(): Option[IProject] = getElement() match {
    case project: IProject => Some(project)
    case javaProject: IJavaProject => Some(javaProject.getProject())
    case other => None // We're a Preference page!
  }

  lazy val preferenceStore0: IPreferenceStore = {
    /** The project for which we are setting properties */
    val project = getConcernedProject()
    project.map { p =>
      ScalaPlugin().getScalaProject(p).projectSpecificStorage
    } getOrElse (
      super.getPreferenceStore())
  }

  /** Returns the id of what preference page we use */
  @deprecated("This class is only instantiated through extension points, making this hard to call. Use PAGE_ID instead.", "4.0")
  def getPageId = SdtConstants.PluginId

  import EclipseSetting.toEclipseBox
  /** The settings we can change */
  lazy val userBoxes = IDESettings.shownSettings(ScalaPresentationCompiler.defaultScalaSettings()) ++ IDESettings.buildManagerSettings :+
    ScopesSettings.buildScopesSettings(this.getConcernedProject())
  lazy val eclipseBoxes = userBoxes.map { s => toEclipseBox(s, preferenceStore0) }

  /** Pulls the preference store associated with this plugin */
  override def doGetPreferenceStore(): IPreferenceStore = {
    ScalaPlugin().getPreferenceStore()
  }

  var useProjectSettingsWidget: Option[UseProjectSettingsWidget] = None
  var additionalParamsWidget: AdditionalParametersWidget = _
  var dslWidget: Option[DesiredInstallationWidget] = None

  def save(): Unit = {
    import org.scalaide.util.eclipse.SWTUtils.fnToPropertyChangeListener

    val project = getConcernedProject()
    val scalaProject = project flatMap (ScalaPlugin().asScalaProject(_))
    scalaProject foreach (p => preferenceStore0.removePropertyChangeListener(p.compilerSettingsListener))
    val wasProjectSettingsChanged = new AtomicBoolean(false)
    val wasDesiredInstallationChanged = new AtomicBoolean(false)
    val wereAdditionalParamsChanged = new AtomicBoolean(false)

    val classpathChangesListener: IPropertyChangeListener = { (event: PropertyChangeEvent) =>
      event.getProperty() match {
        case SettingConverterUtil.USE_PROJECT_SETTINGS_PREFERENCE => wasProjectSettingsChanged.set(true)
        case CompilerSettings.ADDITIONAL_PARAMS => wereAdditionalParamsChanged.set(true)
        case SettingConverterUtil.SCALA_DESIRED_INSTALLATION => wasDesiredInstallationChanged.set(true)
        case _ =>
      }
    }
    preferenceStore0.addPropertyChangeListener(classpathChangesListener)

    val additionalSourceLevelParameter = ScalaPresentationCompiler.defaultScalaSettings().splitParams(additionalParamsWidget.additionalParametersControl.getText()) find { s => s.startsWith("-Xsource") }
    val sourceLevelString = additionalSourceLevelParameter flatMap ("""-Xsource:(\d\.\d+(?:\.\d)*)""".r unapplySeq (_)) flatMap (_.headOption)

    useProjectSettingsWidget.foreach(_.store())
    additionalParamsWidget.save()
    dslWidget foreach (_.store())

    //This has to come later, as we need to make sure the useProjectSettingsWidget's values make it into
    //the final save.
    save(userBoxes, preferenceStore0)

    if (wasDesiredInstallationChanged.getAndSet(false)) scalaProject foreach (_.setDesiredInstallation()) // this triggers a classpath check on its own
    else {
      // this occurs if the user has manually added the -Xsource:2.xx to the compiler parameters but NOT
      // OR set a scala Installation
      // => we deduce the correct sourceLevel Value and execute it
      if (sourceLevelString.isDefined)
        scalaProject foreach (_.setDesiredSourceLevel(ScalaVersion(sourceLevelString.get))) //this triggers a classpath check on its own
      else if (wasProjectSettingsChanged.getAndSet(false) || wereAdditionalParamsChanged.getAndSet(false)) scalaProject foreach (_.classpathHasChanged())
    }

    //Don't let user click "apply" again until a change
    updateApplyButton

    preferenceStore0.removePropertyChangeListener(classpathChangesListener)
    scalaProject map (p => preferenceStore0.addPropertyChangeListener(p.compilerSettingsListener))
  }

  def updateApply(): Unit = {
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
        useProjectSettingsWidget = Some(new UseProjectSettingsWidget(outer))
        val other = new Composite(outer, SWT.SHADOW_ETCHED_IN)
        other.setLayout(new GridLayout(1, false))
        if (ScalaPlugin().scalaVersion >= SpecificScalaVersion(2, 11, 0, Final)) {
          dslWidget = Some(new DesiredInstallationWidget(other))
        }
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
    // set as a 2-column grid Layout, filled by label + field editor of the eclipse boxes
    val tabGridData = new GridData(GridData.FILL)
    tabGridData.horizontalSpan = 2
    tabFolder.setLayoutData(tabGridData)

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

    additionalParamsWidget = (new AdditionalParametersWidget(composite)).addTo()

    //Make sure we check enablement of compiler settings here...
    useProjectSettingsWidget.foreach(_.handleToggle())

    tabFolder.pack()
    composite
  }

  override def okToLeave(): Boolean = {
    val res = if (isChanged) {
      val title = "Setting Compiler Options"
      val message = "The Compiler Settings Property page contains unsaved modifications. Do you want to apply those modifications so that other compiler-dependent pages can take those settings into account ?"
      val buttonLabels: Array[String] = Array(
        PreferencesMessages.BuildPathsPropertyPage_unsavedchanges_button_save,
        PreferencesMessages.BuildPathsPropertyPage_unsavedchanges_button_ignore)
      val dialog: MessageDialog = new MessageDialog(getShell(), title, null, message, MessageDialog.QUESTION, buttonLabels, 0);
      val res = dialog.open();
      if (res == 0) {
        save()
        performOk() && super.okToLeave()
      } else {
        super.okToLeave()
      }
    } else super.okToLeave()
    res
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
        val plugin = ScalaPlugin()

        for {
          p <- (EclipseUtils.workspaceRoot.getProjects())
          scalaProject <- plugin.asScalaProject(p)
          if !scalaProject.usesProjectSettings
        } yield scalaProject.underlying
    }

    val result = MessageDialog.openConfirm(getShell(), "Compiler settings changed",
      "The compiler settings have changed. A full rebuild is required for " +
        "changes to take effect. Shall all projects be cleaned now?")

    if (result)
      ProjectsCleanJob(projects).schedule()
  }

  // Eclipse PropertyPage API
  override def performOk() = try {
    val wasChanged = isChanged
    eclipseBoxes.foreach(_.eSettings.foreach(_.apply()))
    save()
    if (wasChanged) buildIfNecessary()
    true
  } catch {
    case ex: Throwable => eclipseLog.error(ex); false
  }

  //Make sure apply button isn't available until it should be
  override def isChanged: Boolean = {
    useProjectSettingsWidget foreach { (widget) =>
      if (widget.isChanged) {
        return true
      } else {
        // Make sure we don't check the settings of the GUI if they're all disabled
        // and the "use Project settings" is disabled
        if (!widget.isUseEnabled)
          return false
      }
    }

    logger.info(eclipseBoxes.exists { box =>
      logger.info(box.eSettings.find(_.isChanged).toString)
      box.eSettings.exists(_.isChanged)
    }.toString)

    //check all our other settings
    (dslWidget exists { w => w.isChanged() }) || additionalParamsWidget.isChanged || super.isChanged
  }

  override def performDefaults(): Unit = {
    super.performDefaults
    additionalParamsWidget.reset
  }

  /** This widget should only be used on project property pages. */
  class UseProjectSettingsWidget(parent: Composite) extends org.scalaide.util.eclipse.SWTUtils.CheckBox(preferenceStore0, SettingConverterUtil.USE_PROJECT_SETTINGS_PREFERENCE, "Use Project Settings", parent)
      with Subscriber[IScalaProjectEvent, Publisher[IScalaProjectEvent]] {
    import SettingConverterUtil._

    // TODO - Does this belong here?  For now it's the only place we can really check...
    if (!getPreferenceStore().contains(USE_PROJECT_SETTINGS_PREFERENCE)) {
      getPreferenceStore.setDefault(USE_PROJECT_SETTINGS_PREFERENCE, false)
    }
    this += ((e) => handleToggle())
    getConcernedProject() flatMap (ScalaPlugin().asScalaProject(_)) foreach { _.subscribe(this) }

    override def notify(pub: Publisher[IScalaProjectEvent], event: IScalaProjectEvent): Unit = {
      event match {
        case e: ScalaInstallationChange =>
          DisplayThread.asyncExec(doLoad())
          DisplayThread.asyncExec(handleToggle())
        case _ => ()
      }
    }

    override def dispose() = {
      getConcernedProject() flatMap (ScalaPlugin().asScalaProject(_)) foreach { _.removeSubscription(this) }
      super.dispose()
    }

    /** Pulls our current value from the preference store */
    private def getStoreValue() = getPreferenceStore().getBoolean(getPreferenceName())

    /** Toggles the use of a property page */
    def handleToggle(): Unit = {
      val selected = Option(getBooleanValue()).getOrElse(false)
      eclipseBoxes.foreach(_.eSettings.foreach(_.setEnabled(selected)))
      additionalParamsWidget.setEnabled(selected)
      dslWidget foreach (_.setEnabled(selected))
      updateApplyButton
    }

    def isChanged = getStoreValue() != getChangeControl(parent).getSelection

    def isUseEnabled = getStoreValue()

    @deprecated("Use store()", "4.0.0")
    def save() = store()
  }

  def labeler = new ScalaInstallationUIProviders {
    def itemTitle = "Fixed Scala Installation"
  }

  def choicesOfScalaInstallations(): Array[Array[String]] = {
    (Array("Latest 2.11 bundle (dynamic)", "2.11") ::
      (Array("Latest 2.10 bundle (dynamic)", "2.10") ::
        ScalaInstallation.availableInstallations.map { si => Array(labeler.getDecoration(si), ScalaInstallationChoice(si).toString()) })).toArray
  }

  class DesiredInstallationWidget(parent: Composite) extends ComboFieldEditor(
    SettingConverterUtil.SCALA_DESIRED_INSTALLATION,
    "Scala Installation",
    choicesOfScalaInstallations(),
    parent) with Subscriber[IScalaProjectEvent, Publisher[IScalaProjectEvent]] {
    setPreferenceStore(preferenceStore0)
    getConcernedProject() flatMap (ScalaPlugin().asScalaProject(_)) foreach { _.subscribe(this) }
    load()
    // This is just here to implement a status/dirtiness check, not to get values
    // however, owing to the policy of the FieldEditor subclasses not to change values outside of the store,
    // it has to be done through value tracking
    // initialValue is only modified through backend changes (through the subscriber mechanism)
    // currentValue is only modified in this dialog, through selections
    private var initialValue = getPreferenceStore().getString(SettingConverterUtil.SCALA_DESIRED_INSTALLATION)
    private var currentValue = initialValue

    def isChanged() = !(currentValue equals initialValue)

    override def notify(pub: Publisher[IScalaProjectEvent], event: IScalaProjectEvent): Unit = {
      event match {
        case e: ScalaInstallationChange =>
          // the semantics of the initial value have changed through this backend update
          // it's very important to do this before the Load (platform checks on file IO)
          initialValue = getPreferenceStore().getString(SettingConverterUtil.SCALA_DESIRED_INSTALLATION)
          fireValueChanged(FieldEditor.VALUE, "", initialValue)
          DisplayThread.asyncExec(doLoad())
          DisplayThread.asyncExec(updateApply())
        case _ => ()
      }
    }

    override def fireValueChanged(property: String, oldValue: Object, newValue: Object): Unit = {
      import org.scalaide.util.Utils.WithAsInstanceOfOpt
      if (property == FieldEditor.VALUE) {
        val oldVal = oldValue.asInstanceOfOpt[String]
        val newVal = newValue.asInstanceOfOpt[String]
        newVal filter { nV => oldVal exists (_ != nV) } foreach { currentValue = _ }
      }
      super.fireValueChanged(property, oldValue, newValue)
      updateApplyButton()
    }

    override def dispose() = {
      getConcernedProject() flatMap (ScalaPlugin().asScalaProject(_)) foreach { _.removeSubscription(this) }
      super.dispose()
    }

    def setEnabled(value: Boolean): Unit = setEnabled(value, parent)
  }

  // LUC_B: it would be nice to have this widget behave like the other 'EclipseSettings', to avoid unnecessary custom code
  class AdditionalParametersWidget(parent: Composite) extends StringFieldEditor(CompilerSettings.ADDITIONAL_PARAMS, "Additional command line parameters:", StringFieldEditor.UNLIMITED, parent)
      with Subscriber[IScalaProjectEvent, Publisher[IScalaProjectEvent]] {
    import org.scalaide.util.eclipse.SWTUtils.fnToModifyListener
    setPreferenceStore(preferenceStore0)
    load()
    getConcernedProject() flatMap (ScalaPlugin().asScalaProject(_)) foreach { _.subscribe(this) }

    override def notify(pub: Publisher[IScalaProjectEvent], event: IScalaProjectEvent): Unit = {
      event match {
        case e: ScalaInstallationChange =>
          DisplayThread.asyncExec(doLoad())
          DisplayThread.asyncExec(updateApply())
        case _ => ()
      }
    }

    override def dispose() = {
      getConcernedProject() flatMap (ScalaPlugin().asScalaProject(_)) foreach { _.removeSubscription(this) }
      super.dispose()
    }

    val additionalParametersControl: Text = getTextControl(parent)

    var additionalCompParams = originalValue
    def originalValue = getPreferenceStore().getString(getPreferenceName())

    def addTo(): this.type = {

      additionalParametersControl.addModifyListener { (event: ModifyEvent) =>
        val errors = new StringBuffer
        val settings = ScalaPresentationCompiler.defaultScalaSettings(e => errors append ("\n" + e))
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

      val settings = ScalaPresentationCompiler.defaultScalaSettings()
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

    def save(): Unit = {
      preferenceStore0.setValue(CompilerSettings.ADDITIONAL_PARAMS, additionalCompParams)
    }

    def reset(): Unit = {
      additionalParametersControl.setText(preferenceStore0.getString(CompilerSettings.ADDITIONAL_PARAMS))
    }

    def setEnabled(value: Boolean): Unit = {
      additionalParametersControl.setEnabled(value)
    }
  }
}

object CompilerSettings {
  final val ADDITIONAL_PARAMS = "scala.compiler.additionalParams"
  val PAGE_ID = "org.scalaide.ui.preferences.compiler"
}

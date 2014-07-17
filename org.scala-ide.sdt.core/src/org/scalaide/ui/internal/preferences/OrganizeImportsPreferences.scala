package org.scalaide.ui.internal.preferences

import net.miginfocom.layout.AC
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swt.MigLayout
import org.eclipse.core.resources.IProject
import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer
import org.eclipse.core.runtime.preferences.DefaultScope
import org.eclipse.core.runtime.preferences.InstanceScope
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.internal.ui.preferences.PreferencesMessages
import org.eclipse.jface.dialogs.IInputValidator
import org.eclipse.jface.dialogs.InputDialog
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.preference.ListEditor
import org.eclipse.jface.window.Window
import org.eclipse.swt.events.SelectionEvent
import org.eclipse.swt.widgets.Button
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Control
import org.eclipse.swt.widgets.Display
import org.eclipse.swt.widgets.Label
import org.eclipse.swt.widgets.Link
import org.eclipse.swt.SWT
import org.eclipse.ui.dialogs.PreferencesUtil
import org.eclipse.ui.dialogs.PropertyPage
import org.eclipse.ui.IWorkbench
import org.eclipse.ui.IWorkbenchPreferencePage
import org.scalaide.util.internal.eclipse.SWTUtils._
import org.scalaide.util.internal.Utils
import org.scalaide.core.ScalaPlugin
import org.eclipse.jface.preference.RadioGroupFieldEditor
import org.eclipse.jface.preference.FieldEditor
import org.eclipse.jface.preference.BooleanFieldEditor
import org.eclipse.core.resources.ProjectScope



class OrganizeImportsPreferencesPage extends PropertyPage with IWorkbenchPreferencePage {
  import OrganizeImportsPreferences._

  private var isWorkbenchPage = false

  private var allEnableDisableControls: Set[Control] = Set()

  case class AnalyzerSetting(enabled: Boolean, severity: Int)

  private val fieldEditors = collection.mutable.ListBuffer[FieldEditor]()

  override def init(workbench: IWorkbench) {
    isWorkbenchPage = true
  }

  private def initUnderlyingPreferenceStore() {
    val pluginId = ScalaPlugin.plugin.pluginId
    val scalaPrefStore = ScalaPlugin.prefStore
    setPreferenceStore(getElement match {
      case project: IProject => new PropertyStore(new ProjectScope(project), pluginId)
      case project: IJavaProject => new PropertyStore(new ProjectScope(project.getProject), pluginId)
      case _ => scalaPrefStore
    })
  }

  def addNewFieldEditorWrappedInComposite[T <: FieldEditor](parent: Composite)(f: Composite => T): T = {

    val composite = new Composite(parent, SWT.NONE)

    val fieldEditor = f(composite)

    fieldEditor.setPreferenceStore(getPreferenceStore)
    fieldEditor.load

    if(isWorkbenchPage) {
      composite.setLayoutData(new CC().grow.wrap)
    } else {
      composite.setLayoutData(new CC().spanX(2).grow.wrap)
    }

    fieldEditor
  }

  def createContents(parent: Composite): Control = {

    initUnderlyingPreferenceStore() // done here to ensure that getElement will have been set

    // copied from the formatter preferences, should be extracted to somewhere common..
    val control = new Composite(parent, SWT.NONE)
    val rowConstraints = if (isWorkbenchPage)
      new AC().index(0).grow(0).index(1).grow
    else
      new AC().index(0).grow(0).index(1).grow(0).index(2).grow(0).index(3).grow
    control.setLayout(new MigLayout(new LC().insetsAll("0").fill, new AC(), rowConstraints))

    if (!isWorkbenchPage) {

      val projectSpecificButton = new Button(control, SWT.CHECK | SWT.WRAP)
      projectSpecificButton.setText("Enable project specific settings")
      projectSpecificButton.setSelection(getPreferenceStore.getBoolean(USE_PROJECT_SPECIFIC_SETTINGS_KEY))
      projectSpecificButton.addSelectionListener { () =>
        val enabled = projectSpecificButton.getSelection
        getPreferenceStore.setValue(USE_PROJECT_SPECIFIC_SETTINGS_KEY, enabled)
        allEnableDisableControls foreach { _.setEnabled(enabled) }
      }
      projectSpecificButton.setLayoutData(new CC)

      val link = new Link(control, SWT.NONE)
      link.setText("<a>" + PreferencesMessages.PropertyAndPreferencePage_useworkspacesettings_change + "</a>")
      link.addSelectionListener { () =>
        PreferencesUtil.createPreferenceDialogOn(getShell, PAGE_ID, Array(PAGE_ID), null).open()
      }
      link.setLayoutData(new CC().alignX("right").wrap)

      val horizontalLine = new Label(control, SWT.SEPARATOR | SWT.HORIZONTAL)
      horizontalLine.setLayoutData(new CC().spanX(2).grow.wrap)
    }

    fieldEditors += addNewFieldEditorWrappedInComposite(parent = control) { parent =>
      new ListEditor(groupsKey, "Define the sorting order of import statements.", parent) {

        allEnableDisableControls += getListControl(parent)
        allEnableDisableControls += getButtonBoxControl(parent)

        def createList(items: Array[String]) = items.mkString("$")

        def parseString(stringList: String) = stringList.split("\\$")

        def getNewInputObject(): String = {

          val dlg = new InputDialog(Display.getCurrent().getActiveShell(), "", "Enter a package name:", "", new IInputValidator { def isValid(text: String) = null });
          if (dlg.open() == Window.OK) {
            dlg.getValue()
          } else {
            null
          }
        }
      }
    }

    fieldEditors += addNewFieldEditorWrappedInComposite(parent = control) { parent =>
      val options = Array(
          Array("one import statement per importee", ExpandImports.toString),
          Array("collapse into single import statement", CollapseImports.toString),
          Array("preserve existing groups", PreserveExistingGroups.toString)
      )
      new RadioGroupFieldEditor(expandCollapseKey, "Multiple imports from the same package or type:", 1, options, parent, true) {
        allEnableDisableControls += getRadioBoxControl(parent)
        allEnableDisableControls ++= getRadioBoxControl(parent).getChildren
      }
    }

    fieldEditors += addNewFieldEditorWrappedInComposite(parent = control) { parent =>
      new ListEditor(wildcardsKey, "Always use wilcard imports when importing from these packages and objects:", parent) {

        getDownButton.setVisible(false)
        getUpButton.setVisible(false)

        allEnableDisableControls += getListControl(parent)
        allEnableDisableControls += getButtonBoxControl(parent)

        def createList(items: Array[String]) = items.mkString("$")

        def parseString(stringList: String) = stringList.split("\\$")

        def getNewInputObject(): String = {

          val dlg = new InputDialog(Display.getCurrent().getActiveShell(), "", "Enter a fully qualified package or type name:", "", new IInputValidator { def isValid(text: String) = null });
          if (dlg.open() == Window.OK) {
            dlg.getValue()
          } else {
            null
          }
        }
      }
    }

    fieldEditors += addNewFieldEditorWrappedInComposite(parent = control) { parent =>
      new BooleanFieldEditor(omitScalaPackage, "Omit the scala package prefix", parent) {
        allEnableDisableControls += getChangeControl(parent)
      }
    }

    if (!isWorkbenchPage) {
      val enabled = getPreferenceStore.getBoolean(USE_PROJECT_SPECIFIC_SETTINGS_KEY)
      allEnableDisableControls foreach { _.setEnabled(enabled) }
    }
    control
  }

  override def performDefaults() = {
    super.performDefaults()
    fieldEditors.foreach(_.loadDefault)
  }

  override def performOk() = {
    super.performOk()
    fieldEditors.foreach(_.store)
    InstanceScope.INSTANCE.getNode(ScalaPlugin.plugin.pluginId).flush()
    true
  }
}

object OrganizeImportsPreferences extends Enumeration {
  val PREFIX = "organizeimports"
  val USE_PROJECT_SPECIFIC_SETTINGS_KEY = PREFIX + ".useProjectSpecificSettings"
  val PAGE_ID = "scala.tools.eclipse.properties.OrganizeImportsPreferencesPage"

  val ExpandImports = Value("expand")
  val CollapseImports = Value("collapse")
  val PreserveExistingGroups = Value("preserve")

  val groupsKey         = PREFIX +".groups"
  val wildcardsKey      = PREFIX +".wildcards"
  val expandCollapseKey = PREFIX +".expandcollapse"

  val omitScalaPackage = PREFIX +".scalapackage"

  private def getPreferenceStore(project: IProject): IPreferenceStore = {
    val projectStore = new PropertyStore(new ProjectScope(project), ScalaPlugin.plugin.pluginId)
    val useProjectSettings = projectStore.getBoolean(USE_PROJECT_SPECIFIC_SETTINGS_KEY)
    val prefStore = if (useProjectSettings) projectStore else ScalaPlugin.prefStore
    prefStore
  }

  def getGroupsForProject(project: IProject) = {
    getPreferenceStore(project).getString(groupsKey).split("\\$")
  }

  def shouldOmitScalaPackage(project: IProject) = {
    getPreferenceStore(project).getBoolean(omitScalaPackage)
  }

  def getWildcardImportsForProject(project: IProject) = {
    getPreferenceStore(project).getString(wildcardsKey).split("\\$")
  }

  def getOrganizeImportStrategy(project: IProject) = {
    getPreferenceStore(project).getString(expandCollapseKey) match {
      case x if x == ExpandImports.toString => ExpandImports
      case x if x == CollapseImports.toString => CollapseImports
      case x if x == PreserveExistingGroups.toString => PreserveExistingGroups
    }
  }
}

class OrganizeImportsPreferencesInitializer extends AbstractPreferenceInitializer {

  /** Actually initializes preferences */
  def initializeDefaultPreferences() : Unit = {

    Utils.tryExecute {
      val node = DefaultScope.INSTANCE.getNode(ScalaPlugin.plugin.pluginId)
      node.put(OrganizeImportsPreferences.omitScalaPackage, "false")
      node.put(OrganizeImportsPreferences.groupsKey, "java$scala$org$com")
      node.put(OrganizeImportsPreferences.wildcardsKey, "scalaz$scalaz.Scalaz")
      node.put(OrganizeImportsPreferences.expandCollapseKey, OrganizeImportsPreferences.ExpandImports.toString)
    }
  }
}

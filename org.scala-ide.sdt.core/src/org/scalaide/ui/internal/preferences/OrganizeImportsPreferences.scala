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
import org.scalaide.core.IScalaPlugin
import org.eclipse.jface.preference.RadioGroupFieldEditor
import org.eclipse.jface.preference.FieldEditor
import org.eclipse.jface.preference.BooleanFieldEditor
import org.eclipse.core.resources.ProjectScope
import org.scalaide.core.SdtConstants

class OrganizeImportsPreferencesPage extends FieldEditors {
  import OrganizeImportsPreferences._

  override def createContents(parent: Composite): Control = {
    initUnderlyingPreferenceStore(SdtConstants.PluginId, IScalaPlugin().getPreferenceStore)
    mkMainControl(parent)(createEditors)
  }

  def createEditors(control: Composite): Unit = {
    fieldEditors += addNewFieldEditorWrappedInComposite(parent = control) { parent =>
      new ListEditor(groupsKey, "Define the sorting order of import statements.", parent) {

        allEnableDisableControls += getListControl(parent)
        allEnableDisableControls += getButtonBoxControl(parent)

        override def createList(items: Array[String]) = items.mkString("$")

        override def parseString(stringList: String) = stringList.split("\\$")

        override def getNewInputObject(): String = {

          val dlg = new InputDialog(
              Display.getCurrent().getActiveShell(),
              "",
              "Enter a package name:",
              "",
              new IInputValidator { override def isValid(text: String) = null })
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
          Array("One import statement per importee", ExpandImports.toString),
          Array("Collapse into single import statement", CollapseImports.toString),
          Array("Preserve existing groups", PreserveExistingGroups.toString),
          Array("Preserve only wildcards; one import statement per importee otherwise", PreserveWildcards.toString)
      )
      new RadioGroupFieldEditor(expandCollapseKey, "Multiple imports from the same package or type:", 1, options, parent, true) {
        allEnableDisableControls += getRadioBoxControl(parent)
        allEnableDisableControls ++= getRadioBoxControl(parent).getChildren
      }
    }

    fieldEditors += addNewFieldEditorWrappedInComposite(parent = control) { (parent =>
      new ListEditor(wildcardsKey, "Always use wilcard imports when importing from these packages and objects:", parent) {

        getDownButton.setVisible(false)
        getUpButton.setVisible(false)

        allEnableDisableControls += getListControl(parent)
        allEnableDisableControls += getButtonBoxControl(parent)

        override def createList(items: Array[String]) = items.mkString("$")

        override def parseString(stringList: String) = stringList.split("\\$")

        override def getNewInputObject(): String = {

          val dlg = new InputDialog(
              Display.getCurrent().getActiveShell(),
              "",
              "Enter a fully qualified package or type name:",
              "",
              new IInputValidator { override def isValid(text: String) = null })
          if (dlg.open() == Window.OK) {
            dlg.getValue()
          } else {
            null
          }
        }
      })
    }

    fieldEditors += addNewFieldEditorWrappedInComposite(parent = control) { parent =>
      new BooleanFieldEditor(omitScalaPackage, "Omit the scala package prefix", parent) {
        allEnableDisableControls += getChangeControl(parent)
      }
    }
  }

  override def useProjectSpecifcSettingsKey = UseProjectSpecificSettingsKey

  override def pageId = PageId
}

object OrganizeImportsPreferences extends Enumeration {
  val UseProjectSpecificSettingsKey = "organizeimports.useProjectSpecificSettings"
  val PageId = "org.scalaide.ui.preferences.editor.organizeImports"

  val ExpandImports = Value("expand")
  val CollapseImports = Value("collapse")
  val PreserveExistingGroups = Value("preserve")
  val PreserveWildcards = Value("preserveWildcards")

  val groupsKey         = "organizeimports.groups"
  val wildcardsKey      = "organizeimports.wildcards"
  val expandCollapseKey = "organizeimports.expandcollapse"

  val omitScalaPackage = "organizeimports.scalapackage"

  private def getPreferenceStore(project: IProject): IPreferenceStore = {
    val workspaceStore = IScalaPlugin().getPreferenceStore()
    val projectStore = new PropertyStore(new ProjectScope(project), SdtConstants.PluginId)
    val useProjectSettings = projectStore.getBoolean(UseProjectSpecificSettingsKey)
    val prefStore = if (useProjectSettings) projectStore else workspaceStore
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
      case x if x == PreserveWildcards.toString => PreserveWildcards
    }
  }
}

class OrganizeImportsPreferencesInitializer extends AbstractPreferenceInitializer {

  /** Actually initializes preferences */
  override def initializeDefaultPreferences(): Unit = {
    val node = DefaultScope.INSTANCE.getNode(SdtConstants.PluginId)
    node.put(OrganizeImportsPreferences.omitScalaPackage, "false")
    node.put(OrganizeImportsPreferences.groupsKey, "java$scala$org$com")
    node.put(OrganizeImportsPreferences.wildcardsKey, "scalaz$scalaz.Scalaz")
    node.put(OrganizeImportsPreferences.expandCollapseKey, OrganizeImportsPreferences.ExpandImports.toString)
  }
}

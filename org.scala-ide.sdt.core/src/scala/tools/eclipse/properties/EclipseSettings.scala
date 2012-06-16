package scala.tools.eclipse.properties

import scala.tools.nsc.Settings
import scala.tools.eclipse.{ SettingConverterUtil }
import org.eclipse.swt.widgets.{ Button, Combo, Composite, Control, Event, Group, Label, Listener, Text }
import org.eclipse.swt.layout.{ GridData, GridLayout }
import org.eclipse.swt.SWT
import org.eclipse.swt.events.{ ModifyEvent, ModifyListener, SelectionAdapter, SelectionEvent, SelectionListener }
import org.eclipse.jface.preference.{IPreferenceStore, PreferencePage}
import scala.tools.eclipse.util.Trim

trait EclipseSettings {
  self: PreferencePage with ScalaPluginPreferencePage =>

  object EclipseSetting {
    /** Function to map a Scala compiler setting to an Eclipse plugin setting */
    def apply(setting: Settings#Setting): EclipseSetting = setting match {
      case setting: Settings#BooleanSetting => new CheckBoxSetting(setting)
      case setting: Settings#IntSetting     => new IntegerSetting(setting)
      case setting: Settings#StringSetting =>
        setting.name match {
          case "-Ypresentation-log" | "-Ypresentation-replay" =>
            logger.info("file setting for " + setting.name)
            new FileSetting(setting)
          case _ =>
            logger.info("plain old string setting " + setting.name)
            new StringSetting(setting)
        }
      //    case setting : Settings#PhasesSetting  => new StringSetting(setting) // !!!
      case setting: Settings#MultiStringSetting =>
        setting.name match {
          case "-Xplugin" => new MultiFileSetting(setting)
          case _          => new MultiStringSetting(setting)
        }
      case setting: Settings#ChoiceSetting => new ComboSetting(setting)
    }

    case class EclipseBox(name: String, eSettings: List[EclipseSetting])
    def toEclipseBox(userBox: IDESettings.Box, preferenceStore: IPreferenceStore): EclipseBox = {
      val eSettings = userBox.userSettings.map { s: Settings#Setting =>
        val name = SettingConverterUtil.convertNameToProperty(s.name)
        s.tryToSetFromPropertyValue(preferenceStore.getString(name))
        EclipseSetting(s)
      }
      EclipseBox(userBox.name, eSettings)
    }
  }

  private object SelectionListenerSing extends SelectionAdapter {
    override def widgetSelected(e: SelectionEvent): Unit = updateApplyButton()
  }

  private object ModifyListenerSing extends ModifyListener {
    def modifyText(e: ModifyEvent) = updateApplyButton()
  }

  /** Represents a setting that may by changed within Eclipse.
   */
  abstract class EclipseSetting(val setting: Settings#Setting) {
    def control: Control
    val data = new GridData()
    data.horizontalAlignment = GridData.FILL

    def setEnabled(value: Boolean): Unit = {
      control.setEnabled(value)
      if (!value) {
        reset
      }
    }

    def addTo(page: Composite) {
      val label = new Label(page, SWT.NONE)
      label.setText(SettingConverterUtil.convertNameToProperty(setting.name))
      createControl(page)
      val help = new Label(page, SWT.WRAP)
      help.setText(setting.helpDescription)
    }

    /** Create the control on the page */
    def createControl(page: Composite)
    def isChanged: Boolean

    /** Reset the control to a default value */
    def reset()

    /** Apply the value of the control */
    def apply()
  }

  /** Boolean setting controlled by a checkbox.
   */
  class CheckBoxSetting(setting: Settings#BooleanSetting)
    extends EclipseSetting(setting) {
    var control: Button = _

    def createControl(page: Composite) {
      control = new Button(page, SWT.CHECK)
      control.setSelection(setting.value)
      control.addSelectionListener(
        SelectionListenerSing)
    }

    def isChanged = !setting.value.equals(control.getSelection)

    def reset() { control.setSelection(false) }

    def apply() { setting.value = control.getSelection }
  }

  /** Integer setting editable using a text field.
   */
  class IntegerSetting(setting: Settings#IntSetting)
    extends EclipseSetting(setting) {
    var control: Text = _

    def createControl(page: Composite) {
      control = new Text(page, SWT.SINGLE | SWT.BORDER)
      control.setLayoutData(data)
      control.setText(setting.value.toString)
      control.addListener(SWT.Verify, new Listener {
        def handleEvent(e: Event) { if (e.text.exists(c => c < '0' || c > '9')) e.doit = false }
      })
      control.addModifyListener(ModifyListenerSing)
    }

    def isChanged = setting.value.toString != control.getText

    def reset() { control.setText(setting.default.toString) }
    def apply() {
      setting.value = try {
        control.getText.toInt
      } catch {
        case _: NumberFormatException => setting.default
      }
    }
  }

  /** String setting editable using a text field.
   */
  class StringSetting(setting: Settings#StringSetting)
    extends EclipseSetting(setting) {
    var control: Text = _
    def createControl(page: Composite) {
      control = new Text(page, SWT.SINGLE | SWT.BORDER)
      control.setText(setting.value)
      val layout = new GridData()
      layout.widthHint = 200
      control.setLayoutData(layout)
      control.addModifyListener(ModifyListenerSing)
    }

    def isChanged = setting.value != control.getText
    def reset() { control.setText(setting.default) }
    def apply() { setting.value = control.getText }
  }

  /** Multi string setting editable using a text field.
   */
  class MultiStringSetting(setting: Settings#MultiStringSetting)
    extends EclipseSetting(setting) {
    var control: Text = _
    def createControl(page: Composite) {
      control = new Text(page, SWT.SINGLE | SWT.BORDER)
      val layout = new GridData()
      layout.widthHint = 200
      control.setLayoutData(layout)
      control.setText(setting.value.mkString(", "))
      control.addModifyListener(ModifyListenerSing)
    }

    def values: List[String] = control.getText().split(',').flatMap(Trim(_)).toList
    
    def isChanged = setting.value != values
    def reset() { control.setText("") }
    def apply() { setting.value = values }
  }

  /** Text setting selectable using a drop down combo box.
   */
  class ComboSetting(setting: Settings#ChoiceSetting)
    extends EclipseSetting(setting) {
    var control: Combo = _
    def createControl(page: Composite) {
      control = new Combo(page, SWT.DROP_DOWN | SWT.READ_ONLY)
      control.setLayoutData(data)
      setting.choices.foreach(control.add)
      control.setText(setting.value)
      control.addSelectionListener(SelectionListenerSing)
    }

    def isChanged = setting.value != control.getText
    def reset() { control.setText(setting.default) }
    def apply() { setting.value = control.getText }
  }

  /** String setting editable using a File dialog.
   *
   *  @note Temporary implementation. This one does not have a File dialog, instead
   *       it prefixes the workspace path when the filename is not an absolute path.
   */
  class FileSetting(setting: Settings#StringSetting)
    extends EclipseSetting(setting) {
    var control: Text = _
    def createControl(page: Composite) {
      control = new Text(page, SWT.SINGLE | SWT.BORDER)
      control.setText(setting.value)
      val layout = new GridData()
      layout.widthHint = 200
      control.setLayoutData(layout)
      control.setMessage("Path is relative to the workspace")
      control.addModifyListener(ModifyListenerSing)
    }

    def isChanged = setting.value != fileName(control.getText)
    def reset() { control.setText(setting.default) }
    def apply() { setting.value = fileName(control.getText) }
  }

  class MultiFileSetting(setting: Settings#MultiStringSetting) extends EclipseSetting(setting) {
    var control: Text = _
    def createControl(page: Composite) {
      control = new Text(page, SWT.SINGLE | SWT.BORDER)
      control.setText(setting.value.mkString(", "))
      var layout = data
      layout.widthHint = 200
      control.setLayoutData(layout)
      control.setMessage("Path is relative to the workspace")
      control.addModifyListener(ModifyListenerSing)
    }

    def fileNames(): List[String] = {
      control.getText().split(',').flatMap(f => Trim(f).map(fileName)).toList
    }

    override def isChanged = setting.value != fileNames()
    override def reset() { control.setText("") }
    override def apply() { setting.value = fileNames() }
  }

  /** Return an absolute path denoted by 'name'. If 'name' is already absolute,
   *  it returns 'name', otherwise it prepends the absolute path to the workspace.
   */
  def fileName(name: String) = {
    import scala.tools.eclipse.ScalaPlugin
    import java.io.File

    val f = new File(name)
    if (name.nonEmpty && !f.isAbsolute) {
      val workspacePath = ScalaPlugin.plugin.workspaceRoot.getLocation
      new File(workspacePath.toFile, name).getAbsolutePath
    } else name
  }
}

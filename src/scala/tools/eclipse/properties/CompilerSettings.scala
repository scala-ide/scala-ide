/*
 * Copyright 2005-2008 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.eclipse.properties

import org.eclipse.ui.dialogs.PropertyPage
import scala.tools.nsc.Settings
import org.eclipse.swt.widgets._
import org.eclipse.swt.layout._
import org.eclipse.swt.SWT
import org.eclipse.core.resources.{ IProject, ProjectScope }
import org.eclipse.core.runtime.preferences.{ IEclipsePreferences }
import org.eclipse.jdt.core.IJavaProject

/**
 * Provides a property page to allow Scala compiler settings to be changed.
 */   
trait ProjectSettings {

  def projectNode : IEclipsePreferences 
  val settings = new Settings(null)

  /** The settings we may have changed */
  lazy val userSettings = settings.allSettings.filter(!_.hiddenToIDE).flatMap(userSetting)

  /** Save all the current settings */
  def save() = { 
    for (setting <- userSettings) setting.save()
    projectNode.flush()
  }

  /** Function to map a Scala compiler setting to Project setting */
  def userSetting(setting : settings.Setting) : Option[UserSetting[Any]] = setting match {
  case setting : settings.BooleanSetting => Some(new BooleanUserSetting(setting))
  case setting : settings.StringSetting => Some(new StringUserSetting(setting))
  case setting : settings.ChoiceSetting => Some(new ChoiceUserSetting(setting))
  case otherSetting => None
  }

  /** 
   * Represents a setting that may by changed by user.
   *
   * Would be a lot easier if <code>scala.tools.nsc.Settings#Setting</code> was a parameterised type!
   */
  sealed abstract class UserSetting[+A](val underlying : settings.Setting) {

    /** Default value */
    def default : A

    /** Actual value */
    def value : A

    /** Set value as a string */
    def value_=(string : String)

    /** Command-line argument name without the '-' */
    val name = underlying.name.substring(1)

    /** Saves the setting, unless it matches the default */
    def save() {
      if (value == default)
        projectNode.remove(name)
      else {
        projectNode.put(name, value.toString)
      }
	}

    /** Previously stored value.  null indicates default value */
    val savedValue = projectNode.get(name, null)

    // initialise value to saved value if available, otherwise just keep default}
    if (savedValue ne null) value = savedValue   
  }
  case class BooleanUserSetting(setting : settings.BooleanSetting) extends UserSetting[Boolean](setting) {
    def default = false
    def value = setting.value
    def value_=(string : String) { setting.value = string == "true" }
  }

  case class StringUserSetting(setting : settings.StringSetting) extends UserSetting[String](setting) {
    def default = setting.default
    def value = setting.value
    def value_=(string : String) { setting.value = string }
  }

  case class ChoiceUserSetting(setting : settings.ChoiceSetting) extends UserSetting[String](setting) {
    def default = setting.default
    def value = setting.value
    def value_=(string : String) { setting.value = string }
  }
}

/**
 * Provides a property page to allow Scala compiler settings to be changed.
 */   
class CompilerSettings extends PropertyPage with ProjectSettings {

  lazy val projectNode = {
    /** The project for which we are setting properties */
    val project = getElement() match {
      case project : IProject => project
      case javaProject : IJavaProject => javaProject.getProject()
      case other => throw new RuntimeException("Expected IProject or IJavaProject but was: " + other)
    }
    new ProjectScope(project).getNode(ScalaPlugin.plugin.pluginId)
  }

  /** The settings we can change */
  private lazy val ideSettings = userSettings.map(eclipseSetting)

  // Eclipse PropertyPage API
  def createContents(parent : Composite) : Control = {
    val composite = new Composite(parent, SWT.NONE)
    val layout = new GridLayout(3, false)
    composite.setLayout(layout)
    val data = new GridData(GridData.FILL)
    data.grabExcessHorizontalSpace = true
    composite.setLayoutData(data)
    for (setting <- ideSettings) setting.addTo(composite)
    composite
  }

  // Eclipse PropertyPage API
  override protected def performDefaults = for (setting <- ideSettings) setting.reset()

  // Eclipse PropertyPage API
  override def performOk = try {
    for (setting <- ideSettings) setting.apply()
    save()
    true
  } catch {
    case ex => ScalaPlugin.plugin.logError(ex); false
  }

  /** Function to map a Scala compiler setting to an Eclipse plugin setting */
  private def eclipseSetting(userSetting : UserSetting[Any]) : EclipseSetting = userSetting match {
  case setting : BooleanUserSetting => new CheckBoxSetting(setting)
  case setting : StringUserSetting => new TextSetting(setting)
  case setting : ChoiceUserSetting => new ComboSetting(setting)
  }

  /** 
   * Represents a setting that may by changed within Eclipse.
   */
  abstract class EclipseSetting(val setting : UserSetting[Any]) {

    def layout = {
      val gd = new GridData()
      gd.widthHint = convertWidthInCharsToPixels(15)
      gd
    }

    def addTo(page : Composite) {
      val label = new Label(page, SWT.NONE)
      label.setText(setting.name)
      createControl(page)
      val help = new Label(page, SWT.NONE);
      help.setText(setting.underlying.helpDescription);
    }

    /** Create the control on the page */
    def createControl(page : Composite)

    /** Reset the control to a default value */
    def reset()

    /** Apply the value of the control */
    def apply()
  }

  /** 
   * Boolean setting controlled by a checkbox.
   */
  class CheckBoxSetting(setting : BooleanUserSetting) extends EclipseSetting(setting) {
    private var control : Button = _

    def createControl(page : Composite) {
      control = new Button(page, SWT.CHECK)
      control.setSelection(setting.value)
      control.redraw
    }

    def reset() { control.setSelection(setting.default) }

    def apply() { 
      setting.value = control.getSelection.toString
    }
  }

  /** 
     * Text setting editable using a text field.
     */
  class TextSetting(setting : StringUserSetting) extends EclipseSetting(setting) {
    private var control : Text = _

    def createControl(page : Composite) {
      control = new Text(page, SWT.SINGLE | SWT.BORDER)
      control.setLayoutData(layout)
      control.setText(setting.value)
    }

    def reset() { control.setText(setting.default) }

    def apply() { 
      setting.value = control.getText
    }
  }

  /** 
     * Text setting selectable using a drop down combo box.
     */
  class ComboSetting(setting : ChoiceUserSetting) extends EclipseSetting(setting) {
    private var control : Combo = _

    def createControl(page : Composite) {
      control = new Combo(page, SWT.DROP_DOWN | SWT.READ_ONLY)
      control.setLayoutData(layout)
      setting.setting.choices.foreach(control.add)
      control.setText(setting.value)
    }

    def reset() { control.setText(setting.default) }

    def apply() {
      setting.value = control.getText
   }
  }
}

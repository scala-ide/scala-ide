/*
 * Copyright 2005-2010 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.eclipse.properties

import org.eclipse.ui.dialogs.PropertyPage
import scala.tools.nsc.Settings
import org.eclipse.swt.widgets._
import org.eclipse.swt.layout._
import org.eclipse.swt.SWT
import org.eclipse.core.resources._
import org.eclipse.core.runtime.preferences.{ IEclipsePreferences }
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jdt.core.IJavaProject

import scala.tools.eclipse.{ ScalaPlugin, SettingConverterUtil }
import scala.tools.eclipse.util.IDESettings

/**
 * Provides a property page to allow Scala compiler settings to be changed.
 */   
trait ProjectSettings {
  
  type Setting = Settings#Setting
  type SettingValue = Settings#SettingValue

  def preferenceStore0 : IPreferenceStore 
  val settings = new Settings

  /** The settings we may have changed */
  lazy val userSettings = IDESettings.shownSettings(settings).flatMap(userSetting)

  /** Save all the current settings */
  def save() = { 
    for (setting <- userSettings) setting.save()
    
    import org.eclipse.jface.preference.IPersistentPreferenceStore
    preferenceStore0 match {
      case savable : IPersistentPreferenceStore => savable.save()
     }
  }

  /** Function to map a Scala compiler setting to Project setting */
  def userSetting(setting : Setting) : Option[UserSetting[Any]] = setting.value match {
  case _ : Boolean => Some(new BooleanUserSetting(setting.asInstanceOf[Setting with SettingValue { type T = Boolean }]))
  case _ : String if !setting.choices.isEmpty => Some(new ChoiceUserSetting(setting.asInstanceOf[Setting with SettingValue { type T = String }]))
  case _ : String => Some(new StringUserSetting(setting.asInstanceOf[Setting with SettingValue { type T = String }]))
  case _ => None
  }

  /** 
   * Represents a setting that may by changed by user.
   *
   * Would be a lot easier if <code>scala.tools.nsc.Settings#Setting</code> was a parameterised type!
   */
  sealed abstract class UserSetting[+A](val underlying : Setting) {



    /** Actual value */
    def value : A
    /** Converts from a a string into the actual value */
    def adapt(string : String) : A
    
    /** Set value as a string */
    def value_=(string : String)

    /** Command-line argument name without the '-' */
    val name = SettingConverterUtil.convertNameToProperty(underlying.name)

    /** Default value */
    def default : A = adapt(preferenceStore0.getDefaultString(name))
    
    /** Saves the setting, unless it matches the default */
    def save() {
      if (value == default)
        preferenceStore0.setToDefault(name)
      else {
        preferenceStore0.setValue(name, value.toString)
      }
	}

    /** Previously stored value.  null indicates default value */
    val savedValue = preferenceStore0.getString(name)

    // initialise value to saved value if available, otherwise just keep default}
    if (savedValue ne null) value = savedValue   
  }
  case class BooleanUserSetting(setting : Setting with SettingValue { type T = Boolean }) extends UserSetting[Boolean](setting) {
    def value = setting.value
    def adapt(string : String) = string == "true"
    def value_=(string : String) { setting.value = adapt(string) }
  }

  case class StringUserSetting(setting : Setting with SettingValue { type T = String }) extends UserSetting[String](setting) {
    def value = setting.value
    def adapt(string : String) = string
    def value_=(string : String) { setting.value = string }
  }

  case class ChoiceUserSetting(setting : Setting with SettingValue { type T = String }) extends UserSetting[String](setting) {
    def value = setting.value
    def adapt(string : String) = string
    def value_=(string : String) { setting.value = string }
  }
}

import org.eclipse.ui.{IWorkbench, IWorkbenchPreferencePage}

/** Default behavior for WorkbenchPreference pages */
trait WorkbenchPreferencePage extends IWorkbenchPreferencePage {
  
  protected var isWorkbenchPage = false
  
    /**
     * Initializes this preference page for the given workbench.
     * <p>
     * This method is called automatically as the preference page is being created
     * and initialized. Clients must not call this method.
     * </p>
     *
     * @param workbench the workbench
     */
    override def init(workbench : IWorkbench) : Unit ={
      isWorkbenchPage = true
    }
}

/**
 * Provides a property page to allow Scala compiler settings to be changed.
 */   
class CompilerSettings extends PropertyPage with ProjectSettings with WorkbenchPreferencePage {
  //TODO - Use setValid to enable/disable apply button so we can only click the button when a property/preference
  // has changed from the saved value
  
  
  lazy val preferenceStore0 : IPreferenceStore = {
    /** The project for which we are setting properties */
    val project = getElement() match {
      case project : IProject => Some(project)
      case javaProject : IJavaProject => Some(javaProject.getProject())
      case other => None // We're a Preference page!
    }
    if(project.isEmpty) {
      super.getPreferenceStore()
    } else {
      new PropertyStore(project.get, super.getPreferenceStore(), getPageId);
    }
  }
  /** Returns the id of what preference page we use */
  def getPageId = ScalaPlugin.plugin.pluginId
  
  /** The settings we can change */
  private lazy val ideSettings = userSettings.map(eclipseSetting)
  /** Pulls the preference store associated with this plugin */
  override def doGetPreferenceStore() : IPreferenceStore = {
	    ScalaPlugin.plugin.getPreferenceStore
  }
 
  var useProjectSettingsWidget : Option[UseProjectSettingsWidget] = None;
  
  override def save() = {
    if(useProjectSettingsWidget.isDefined) {
      useProjectSettingsWidget.get.save
    }
    //This has to come later, as we need to make sure the useProjectSettingsWidget's values make it into
    //the final save.
    super[ProjectSettings].save
    //Don't let use click "apply" again until a change
    updateApplyButton
  }
  /** Updates teh apply button with the appropriate enablement. */
  override def updateApplyButton() : Unit = {
    if(getApplyButton != null) {
      if(isValid) {
          getApplyButton.setEnabled(isChanged)
      } else {
        getApplyButton.setEnabled(false)
      }
    }
  }
  
  // Eclipse PropertyPage API
  def createContents(parent : Composite) : Control = {
    val composite = {
      if(isWorkbenchPage) {
        //No Outer Composite
        val tmp = new Composite(parent, SWT.NONE)
	    val layout = new GridLayout(3, false)
	    tmp.setLayout(layout)
	    val data = new GridData(GridData.FILL)
	    data.grabExcessHorizontalSpace = true
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
	    val layout = new GridLayout(3, false)
	    tmp.setLayout(layout)
	    val data = new GridData(GridData.FILL)
	    data.grabExcessHorizontalSpace = true
	    tmp.setLayoutData(data)
        tmp
      }
    }
    
    for (setting <- ideSettings) setting.addTo(composite)
    
    
    //Make sure we check enablement of compiler settings here...
    useProjectSettingsWidget match {
      case Some(widget) => widget.handleToggle 
      case None =>
    }
    composite
  }
  
  /** We override this so we can update the status of the apply button after all components have been added */
  override def createControl(parent :Composite) : Unit = {
    super[PropertyPage].createControl(parent)
    updateApplyButton
  }
  

  // Eclipse PropertyPage API
  override protected def performDefaults = for (setting <- ideSettings) setting.reset()

  /** Check who needs to rebuild with new compiler flags */
  private def buildIfNecessary() = {
    import org.eclipse.core.resources.IncrementalProjectBuilder
    getElement() match {
      case project : IProject => 
            //Make sure project is rebuilt
		    project.build(IncrementalProjectBuilder.CLEAN_BUILD, null)
      case javaProject : IJavaProject => 
            //Make sure project is rebuilt
		    javaProject.getProject().build(IncrementalProjectBuilder.CLEAN_BUILD, null)
      case other => None // We're a Preference page!
      //TODO - FIgure out who needs to rebuild
    }
  }
  
  // Eclipse PropertyPage API
  override def performOk = try {
    for (setting <- ideSettings) setting.apply()
    save()
    buildIfNecessary()    
    true
  } catch {
    case ex => ScalaPlugin.plugin.logError(ex); false
  }
  //Make sure apply button isn't available until it should be
  def isChanged : Boolean = {
	useProjectSettingsWidget match {
	  case Some(widget) => 
		  if(widget.isChanged) {
		    return true;
		  } else {
		    //Make sure we don't check the settings of the GUI if they're all disabled
		    //and the "use Project settings" is disabled
		    if(!widget.isUseEnabled) {
		      return false;
            }
	      }
	  case None => //don't need to check
	}
    //check all our other settings
    ideSettings.exists(_.isChanged)
  }


  /** Function to map a Scala compiler setting to an Eclipse plugin setting */
  private def eclipseSetting(userSetting : UserSetting[Any]) : EclipseSetting = userSetting match {
  case setting : BooleanUserSetting => new CheckBoxSetting(setting)
  case setting : StringUserSetting => new TextSetting(setting)
  case setting : ChoiceUserSetting => new ComboSetting(setting)
  }

  
  /** This widget should only be used on property pages. */
  class UseProjectSettingsWidget {
    import SettingConverterUtil._
    
    // TODO - Does this belong here?  For now it's the only place we can really check...
    if(!preferenceStore0.contains(USE_PROJECT_SETTINGS_PREFERENCE)) {
      preferenceStore0.setDefault(USE_PROJECT_SETTINGS_PREFERENCE, false)
    }
    
    var control : Button = _
    def layout = {
      val gd = new GridData()
      //gd.widthHint = convertWidthInCharsToPixels(15)
      gd
    }
    /** Pulls our current value from the preference store */
    private def getValue = preferenceStore0.getBoolean(USE_PROJECT_SETTINGS_PREFERENCE)
    /** Adds our widget to the Proeprty Page */
    def addTo(page : Composite) = {
      val container = new Composite(page, SWT.NONE)
      container.setLayout(new GridLayout(2, false))
      //Create Control      
      control = new Button(container, SWT.CHECK)
      control.setSelection(getValue)
      control.redraw
      import org.eclipse.swt.events.SelectionListener
      import org.eclipse.swt.events.SelectionEvent
      control.addSelectionListener(new SelectionListener() {
                                     override def widgetDefaultSelected(e : SelectionEvent) : Unit = {}
                                     override def widgetSelected(e : SelectionEvent) : Unit = {
                                       handleToggle
                                     }
                                   })
      val label = new Label(container, SWT.NONE)
      label.setText("Use Project Settings")      
    }
    /** Toggles the use of a property page */
    def handleToggle = {
     val selected = control.getSelection
     ideSettings foreach { _.setEnabled(selected) }
     
     updateApplyButton
     
    }
    
    def isChanged : Boolean = {
      return getValue != control.getSelection;
    }
    
    def isUseEnabled : Boolean = {
      return preferenceStore0.getBoolean(USE_PROJECT_SETTINGS_PREFERENCE)
    }
    
    /** Saves our value into the preference store*/
    def save() = {
      preferenceStore0.setValue(USE_PROJECT_SETTINGS_PREFERENCE, control.getSelection)
    }
  }
  
  /** 
   * Represents a setting that may by changed within Eclipse.
   */
  abstract class EclipseSetting(val setting : UserSetting[Any]) {

    def layout = {
      val gd = new GridData()
      //gd.widthHint = convertWidthInCharsToPixels(15)
      gd
    }
    /** enable/disable the setting */
    def setEnabled(value:Boolean) : Unit;
    
    def addTo(page : Composite) {
      val label = new Label(page, SWT.NONE)
      label.setText(setting.name)
      createControl(page)
      val help = new Label(page, SWT.NONE);
      help.setText(setting.underlying.helpDescription);
    }

    def isChanged : Boolean;
    
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

    def setEnabled(value:Boolean) : Unit = {
      control.setEnabled(value)
      if(!value) {
        reset
      }
    }
    
    def isChanged : Boolean = {
      !setting.value.equals(control.getSelection)
    }
    
    def createControl(page : Composite) {
      control = new Button(page, SWT.CHECK)
      control.setSelection(setting.value)
      import org.eclipse.swt.events.SelectionAdapter
      import org.eclipse.swt.events.SelectionEvent
      control.addSelectionListener(new SelectionAdapter() {
                                      override def widgetSelected(e : SelectionEvent) : Unit = {
                                        updateApplyButton
                                      }	
                                   });
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

    def setEnabled(value:Boolean) : Unit = {
      control.setEnabled(value)
      if(!value) {
        reset
      }
    }
    
    def createControl(page : Composite) {
      control = new Text(page, SWT.SINGLE | SWT.BORDER)
      control.setLayoutData(layout)
      control.setText(setting.value)
      import org.eclipse.swt.events.ModifyListener;
      import org.eclipse.swt.events.ModifyEvent;
      control.addModifyListener(new ModifyListener() {
                                  def modifyText(e : ModifyEvent ) = {
                                    updateApplyButton
                                  }
                                }) 
    }

    def reset() { control.setText(setting.default) }

    def isChanged : Boolean = {
      !setting.value.equals(control.getText)
    }
    
    def apply() { 
      setting.value = control.getText
    }
  }

  /** 
     * Text setting selectable using a drop down combo box.
     */
  class ComboSetting(setting : ChoiceUserSetting) extends EclipseSetting(setting) {
    private var control : Combo = _

    def setEnabled(value:Boolean) : Unit = {
      control.setEnabled(value)
      if(!value) {
        reset
      }
    }
    
    def createControl(page : Composite) {
      control = new Combo(page, SWT.DROP_DOWN | SWT.READ_ONLY)
      control.setLayoutData(layout)
      setting.setting.choices.foreach(control.add)
      control.setText(setting.value)
      import org.eclipse.swt.events.SelectionAdapter
      import org.eclipse.swt.events.SelectionEvent
      control.addSelectionListener(new SelectionAdapter() {
                                      override def widgetSelected(e : SelectionEvent) : Unit = {
                                        updateApplyButton
                                      }	
                                   });
    }

    def reset() { control.setText(setting.default) }

    def isChanged : Boolean = {
      !setting.value.equals(control.getText)
    }
    
    def apply() {
      setting.value = control.getText
   }
  }
}

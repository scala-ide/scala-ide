/*
 * Copyright 2005-2010 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.eclipse.properties

import org.eclipse.core.resources.{ IncrementalProjectBuilder, IProject }
import org.eclipse.core.runtime.preferences.IEclipsePreferences
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jface.preference.{ IPersistentPreferenceStore, IPreferenceStore }
import org.eclipse.ui.{ IWorkbench, IWorkbenchPreferencePage }
import org.eclipse.ui.dialogs.PropertyPage
import org.eclipse.swt.SWT
import org.eclipse.swt.events.{ ModifyEvent, ModifyListener, SelectionAdapter, SelectionEvent, SelectionListener }
import org.eclipse.swt.layout.{ GridData, GridLayout }
import org.eclipse.swt.widgets.{ Button, Combo, Composite, Control, Event, Group, Label, Listener, Text }

import scala.tools.nsc.Settings

import scala.tools.eclipse.{ ScalaPlugin, SettingConverterUtil }
import scala.tools.eclipse.util.IDESettings

/**
 * Provides a property page to allow Scala compiler settings to be changed.
 */   
class CompilerSettings extends PropertyPage with IWorkbenchPreferencePage {
  //TODO - Use setValid to enable/disable apply button so we can only click the button when a property/preference
  // has changed from the saved value
  
  protected var isWorkbenchPage = false
  
  override def init(workbench : IWorkbench) {
    isWorkbenchPage = true
  }
  
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
      new PropertyStore(project.get, super.getPreferenceStore(), getPageId)
    }
  }
  /** Returns the id of what preference page we use */
  def getPageId = ScalaPlugin.plugin.pluginId
  
  /** The settings we can change */
  lazy val userSettings = IDESettings.shownSettings(new Settings)
  lazy val eclipseSettings = userSettings.map { setting =>
    val name = SettingConverterUtil.convertNameToProperty(setting.name)
    setting.tryToSetFromPropertyValue(preferenceStore0.getString(name))
    eclipseSetting(setting)
  }

  /** Pulls the preference store associated with this plugin */
  override def doGetPreferenceStore() : IPreferenceStore = {
	    ScalaPlugin.plugin.getPreferenceStore
  }
 
  var useProjectSettingsWidget : Option[UseProjectSettingsWidget] = None
  
  def save() = {
    if(useProjectSettingsWidget.isDefined) {
      useProjectSettingsWidget.get.save
    }
    //This has to come later, as we need to make sure the useProjectSettingsWidget's values make it into
    //the final save.
    for (setting <- userSettings) {
      val name = SettingConverterUtil.convertNameToProperty(setting.name)
      val isDefault = setting match {
        case bs : Settings#BooleanSetting => bs.value == false
        case is : Settings#IntSetting => is.value == is.default
        case ss : Settings#StringSetting => ss.value == ss.default
        case ms : Settings#MultiStringSetting => ms.value == Nil
        case cs : Settings#ChoiceSetting => cs.value == cs.default
      }
      if (isDefault)
        preferenceStore0.setToDefault(name)
      else {
        val value = setting match {
          case ms : Settings#MultiStringSetting => ms.value.mkString(" ")
          case setting => setting.value.toString 
        }
        
        preferenceStore0.setValue(name, value)
      }
    }
    
    preferenceStore0 match {
      case savable : IPersistentPreferenceStore => savable.save()
    }

    //Don't let use click "apply" again until a change
    updateApplyButton
  }
  /** Updates the apply button with the appropriate enablement. */
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
    
    for (setting <- eclipseSettings) setting.addTo(composite)
    
    //Make sure we check enablement of compiler settings here...
    useProjectSettingsWidget match {
      case Some(widget) => widget.handleToggle 
      case None =>
    }
    
    composite
  }
  
  /** We override this so we can update the status of the apply button after all components have been added */
  override def createControl(parent :Composite) : Unit = {
    super.createControl(parent)
    updateApplyButton
  }

  // Eclipse PropertyPage API
  override protected def performDefaults = for (setting <- eclipseSettings) setting.reset()

  /** Check who needs to rebuild with new compiler flags */
  private def buildIfNecessary() = {
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
    for (setting <- eclipseSettings) setting.apply()
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
		      return true
		    } else {
		      // Make sure we don't check the settings of the GUI if they're all disabled
		      // and the "use Project settings" is disabled
		      if(!widget.isUseEnabled)
		        return false
	      }
	    case None => //don't need to check
	  }
    
	  //check all our other settings
    eclipseSettings.exists(_.isChanged)
  }


  /** Function to map a Scala compiler setting to an Eclipse plugin setting */
  private def eclipseSetting(setting : Settings#Setting) : EclipseSetting = setting match {
    case setting : Settings#BooleanSetting => new CheckBoxSetting(setting)
    case setting : Settings#IntSetting => new IntegerSetting(setting)
    case setting : Settings#StringSetting => new StringSetting(setting)
//    case setting : Settings#PhasesSetting  => new StringSetting(setting) // !!!
    case setting : Settings#MultiStringSetting => new MultiStringSetting(setting)
    case setting : Settings#ChoiceSetting => new ComboSetting(setting)
  }
  
  /** This widget should only be used on property pages. */
  class UseProjectSettingsWidget {
    import SettingConverterUtil._
    
    // TODO - Does this belong here?  For now it's the only place we can really check...
    if(!preferenceStore0.contains(USE_PROJECT_SETTINGS_PREFERENCE)) {
      preferenceStore0.setDefault(USE_PROJECT_SETTINGS_PREFERENCE, false)
    }
    
    var control : Button = _
    def layout = new GridData()

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
      control.addSelectionListener(new SelectionListener() {
        override def widgetDefaultSelected(e : SelectionEvent) {}
        override def widgetSelected(e : SelectionEvent) { handleToggle }
      })
      val label = new Label(container, SWT.NONE)
      label.setText("Use Project Settings")      
    }

    /** Toggles the use of a property page */
    def handleToggle = {
      val selected = control.getSelection
      eclipseSettings foreach { _.setEnabled(selected) }
      updateApplyButton
    }
    
    def isChanged = getValue != control.getSelection
    
    def isUseEnabled = preferenceStore0.getBoolean(USE_PROJECT_SETTINGS_PREFERENCE)
    
    /** Saves our value into the preference store*/
    def save() {
      preferenceStore0.setValue(USE_PROJECT_SETTINGS_PREFERENCE, control.getSelection)
    }
  }
  
  /** 
   * Represents a setting that may by changed within Eclipse.
   */
  abstract class EclipseSetting(val setting : Settings#Setting) {
    def control : Control
    val data = new GridData()
    data.horizontalAlignment = GridData.FILL

    
    def setEnabled(value:Boolean) : Unit = {
      control.setEnabled(value)
      if(!value) {
        reset
      }
    }
    
    def addTo(page : Composite) {
      val label = new Label(page, SWT.NONE)
      label.setText(SettingConverterUtil.convertNameToProperty(setting.name))
      createControl(page)
      val help = new Label(page, SWT.NONE)
      help.setText(setting.helpDescription)
    }

    /** Create the control on the page */
    def createControl(page : Composite)

    def isChanged : Boolean
    
    /** Reset the control to a default value */
    def reset()

    /** Apply the value of the control */
    def apply()
  }

  /** 
   * Boolean setting controlled by a checkbox.
   */
  class CheckBoxSetting(setting : Settings#BooleanSetting) extends EclipseSetting(setting) {
    var control : Button = _

    def createControl(page : Composite) {
      control = new Button(page, SWT.CHECK)
      control.setSelection(setting.value)
      control.addSelectionListener(new SelectionAdapter() {
        override def widgetSelected(e : SelectionEvent) { updateApplyButton }	
      })
    }

    def isChanged = !setting.value.equals(control.getSelection)
    
    def reset() { control.setSelection(false) }

    def apply() { setting.value = control.getSelection }
  }

  /** 
     * Integer setting editable using a text field.
     */
  class IntegerSetting(setting : Settings#IntSetting) extends EclipseSetting(setting) {
    var control : Text = _

    def createControl(page : Composite) {
      control = new Text(page, SWT.SINGLE | SWT.BORDER)
      control.setLayoutData(data)
      control.setText(setting.value.toString)
      control.addListener (SWT.Verify, new Listener {
        def handleEvent(e : Event) { if(e.text.exists(c => c < '0' || c > '9')) e.doit = false }
      })
      control.addModifyListener(new ModifyListener() {
        def modifyText(e : ModifyEvent ) { updateApplyButton }
      }) 
    }

    def isChanged = setting.value.toString != control.getText
    
    def reset() { control.setText(setting.default.toString) }

    def apply() {
      setting.value = try {
        control.getText.toInt
      } catch {
        case _ : NumberFormatException => setting.default
      }
    }
  }

  /** 
     * String setting editable using a text field.
     */
  class StringSetting(setting : Settings#StringSetting) extends EclipseSetting(setting) {
    var control : Text = _

    def createControl(page : Composite) {
      control = new Text(page, SWT.SINGLE | SWT.BORDER)
      control.setText(setting.value)
      control.setLayoutData(data)
      
      control.addModifyListener(new ModifyListener() {
        def modifyText(e : ModifyEvent) = { updateApplyButton }
      }) 
    }

    def isChanged = setting.value != control.getText
    
    def reset() { control.setText(setting.default) }

    def apply() { setting.value = control.getText }
  }

  /** 
     * Multi string setting editable using a text field.
     */
  class MultiStringSetting(setting : Settings#MultiStringSetting) extends EclipseSetting(setting) {
    var control : Text = _

    def createControl(page : Composite) {
      control = new Text(page, SWT.SINGLE | SWT.BORDER)
      control.setLayoutData(data)
      control.setText(setting.value.mkString(" "))
      control.addModifyListener(new ModifyListener() {
        def modifyText(e : ModifyEvent) = { updateApplyButton }
      }) 
    }

    def isChanged = setting.value != control.getText
    
    def reset() { control.setText("") }

    def apply() { setting.value = control.getText.trim.split(" +").toList }
  }

  /** 
     * Text setting selectable using a drop down combo box.
     */
  class ComboSetting(setting : Settings#ChoiceSetting) extends EclipseSetting(setting) {
    var control : Combo = _

    def createControl(page : Composite) {
      control = new Combo(page, SWT.DROP_DOWN | SWT.READ_ONLY)
      control.setLayoutData(data)
      setting.choices.foreach(control.add)
      control.setText(setting.value)
      control.addSelectionListener(new SelectionAdapter() {
        override def widgetSelected(e : SelectionEvent) { updateApplyButton }	
      })
    }

    def isChanged = setting.value != control.getText
    
    def reset() { control.setText(setting.default) }

    def apply() { setting.value = control.getText }
  }
}

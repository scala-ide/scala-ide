/*
 * Copyright 2005-2010 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.eclipse.properties

import org.eclipse.core.resources.{ IncrementalProjectBuilder, IProject }
import org.eclipse.core.runtime.preferences.IEclipsePreferences
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jface.preference.{ PreferencePage, IPersistentPreferenceStore, IPreferenceStore }
import org.eclipse.ui.{ IWorkbench, IWorkbenchPreferencePage }
import org.eclipse.ui.dialogs.PropertyPage
import org.eclipse.swt.SWT
import org.eclipse.swt.events.{ ModifyEvent, ModifyListener, SelectionAdapter, SelectionEvent, SelectionListener }
import org.eclipse.swt.layout.{ GridData, GridLayout, RowLayout }
import org.eclipse.swt.widgets.{ Button, Combo, Composite, Group, Label, Control, TabFolder, TabItem}
import scala.tools.nsc.Settings

import scala.tools.eclipse.{ ScalaPlugin, SettingConverterUtil }
import scala.tools.eclipse.util.IDESettings

trait ScalaPluginPreferencePage {
	self: PreferencePage with EclipseSettings => 
	
	val eclipseBoxes: List[EclipseSetting.EclipseBox]
	
	def isChanged: Boolean = eclipseBoxes.exists(_.eSettings.exists(_.isChanged))
	
  override def performDefaults = eclipseBoxes.foreach(_.eSettings.foreach(_.reset()))
  
  def save(userBoxes: List[IDESettings.Box], store: IPreferenceStore): Unit = {
	  for (b <- userBoxes) {
      for (setting <- b.userSettings) {
        val name = SettingConverterUtil.convertNameToProperty(setting.name)
        val isDefault = setting match {
          case bs : Settings#BooleanSetting => bs.value == false
          case is : Settings#IntSetting => is.value == is.default
          case ss : Settings#StringSetting => ss.value == ss.default
          case ms : Settings#MultiStringSetting => ms.value == Nil
          case cs : Settings#ChoiceSetting => cs.value == cs.default
        }
        if (isDefault)
          store.setToDefault(name)
        else {
          val value = setting match {
            case ms : Settings#MultiStringSetting => ms.value.mkString(" ")
            case setting => setting.value.toString
          }
          store.setValue(name, value)
        }
      }
    }
    
    store match {
      case savable : IPersistentPreferenceStore => savable.save()
    }
	}
	
	// There seems to be a bug in the compiler that appears in runtime (#2296)
	// So updateApply is going to forward to real updateApplyButton
	def updateApply: Unit 
}

/**
 * Provides a property page to allow Scala compiler settings to be changed.
 */   
class CompilerSettings extends PropertyPage with IWorkbenchPreferencePage with EclipseSettings
  with ScalaPluginPreferencePage {
  //TODO - Use setValid to enable/disable apply button so we can only click the button when a property/preference
  // has changed from the saved value
  
  protected var isWorkbenchPage = false
  
  override def init(workbench : IWorkbench) {
    isWorkbenchPage = true
  }
  
  lazy val preferenceStore0 : IPreferenceStore = {
    /** The project for which we are setting properties */
    val project = getElement() match {
      case project : IProject         => Some(project)
      case javaProject : IJavaProject => Some(javaProject.getProject())
      case other                      => None // We're a Preference page!
    }
    if(project.isEmpty)
      super.getPreferenceStore()
    else
      new PropertyStore(project.get, super.getPreferenceStore(), getPageId)
  }

  /** Returns the id of what preference page we use */
  def getPageId = ScalaPlugin.plugin.pluginId

  import EclipseSetting.toEclipseBox
  /** The settings we can change */
  lazy val userBoxes    = IDESettings.shownSettings(new Settings) ++ IDESettings.buildManagerSettings
  lazy val eclipseBoxes =	userBoxes.map { s => toEclipseBox(s, preferenceStore0) }

  /** Pulls the preference store associated with this plugin */
  override def doGetPreferenceStore() : IPreferenceStore = {
	    ScalaPlugin.plugin.getPreferenceStore
  }
 
  var useProjectSettingsWidget : Option[UseProjectSettingsWidget] = None
  
  def save(): Unit = {
    if(useProjectSettingsWidget.isDefined) {
      useProjectSettingsWidget.get.save
    }
    //This has to come later, as we need to make sure the useProjectSettingsWidget's values make it into
    //the final save.
    save(userBoxes, preferenceStore0)

    //Don't let user click "apply" again until a change
    updateApplyButton
  }
  
  def updateApply = updateApplyButton
  
  /** Updates the apply button with the appropriate enablement. */
  protected override def updateApplyButton() : Unit = {
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
    
    //Make sure we check enablement of compiler settings here...
    useProjectSettingsWidget match {
      case Some(widget) => widget.handleToggle 
      case None =>
    }
    
    tabFolder.pack()
    composite
  }
  
  /** We override this so we can update the status of the apply button after all components have been added */
  override def createControl(parent :Composite) : Unit = {
    super.createControl(parent)
    updateApplyButton
  }

  /** Check who needs to rebuild with new compiler flags */
  private def buildIfNecessary() = {
    getElement() match {
      case project : IProject => 
            //Make sure project is rebuilt
		    project.build(IncrementalProjectBuilder.CLEAN_BUILD, null)
      case javaProject : IJavaProject => 
            //Make sure project is rebuilt
		    javaProject.getProject().build(IncrementalProjectBuilder.CLEAN_BUILD, null)
      case other => 
		    None // We're a Preference page!
      //TODO - Figure out who needs to rebuild
    }
  }
  
  // Eclipse PropertyPage API
  override def performOk = try {
  	eclipseBoxes.foreach(_.eSettings.foreach(_.apply()))
    save()
    buildIfNecessary()    
    true
  } catch {
    case ex => ScalaPlugin.plugin.logError(ex); false
  }
  
  //Make sure apply button isn't available until it should be
  override def isChanged : Boolean = {
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
	  super.isChanged
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
      eclipseBoxes.foreach(_.eSettings.foreach(_.setEnabled(selected)))
      updateApplyButton
    }
    
    def isChanged = getValue != control.getSelection
    
    def isUseEnabled = preferenceStore0.getBoolean(USE_PROJECT_SETTINGS_PREFERENCE)
    
    /** Saves our value into the preference store*/
    def save() {
      preferenceStore0.setValue(USE_PROJECT_SETTINGS_PREFERENCE, control.getSelection)
    }
  }
}  

package scala.tools.eclipse
package diagnostic

import org.eclipse.jface.window.Window
import org.eclipse.jface.dialogs.{ MessageDialog, ErrorDialog, Dialog, IDialogConstants }
import org.eclipse.jface.action.IAction
import org.eclipse.jface.preference.IPreferenceStore

import org.eclipse.swt.widgets.{ List => SWTList, _ }
import org.eclipse.swt.layout.{ GridLayout, GridData }
import org.eclipse.swt.SWT
import org.eclipse.swt.events.{ ModifyListener, ModifyEvent, SelectionAdapter, SelectionListener, SelectionEvent }
import org.eclipse.swt.graphics.{ Font, FontData }

import org.eclipse.jdt.ui.PreferenceConstants
import org.eclipse.jdt.internal.ui.preferences.PreferencesMessages
import org.eclipse.jdt.internal.corext.util.Messages

import org.eclipse.core.runtime.IStatus
import scala.tools.eclipse.contribution.weaving.jdt.configuration.{ WeavingStateConfigurer }
import org.eclipse.ui.PlatformUI
import org.eclipse.ui.browser.IWorkbenchBrowserSupport 

class DiagnosticDialog(shell: Shell) extends Dialog(shell) {

  /* Dialog logic:
     * if current settings do not match default settings: 
         2) "Use other settings" is enabled
     * if user clicks "scala defaults" then values in dialog boxes change
         * if the user changes any of the settings
           1) save all values
           2) "use other settings" is enabled.
  */
  
  protected val configurer = new WeavingStateConfigurer
  
  val heapSize = Runtime.getRuntime.maxMemory / (1024 * 1024)
  val recommendedHeap = 1024
  
  protected val prefStore: IPreferenceStore = PreferenceConstants.getPreferenceStore
  
  // keep track of whether we are programmatically updating a field, in which case the listeners ignore the event
  // a horrible hack, but I think the only alternative is to use the (heavyweight) jface data binding mechanism -DM
  protected var updating = false 
  
  protected var errorMessageField: Text = null
  protected var weavingButton: Button = null
  protected var scalaSettingsButton: Button = null // radio button
  protected var otherSettingsButton: Button = null // radio button
  protected var autoActivationButton: Button = null
  protected var delayText: Text = null
  
  protected var boldFont: Font = null
    
//  protected val markOccurrencesData = new BoolWidgetData(PreferenceConstants.EDITOR_MARK_OCCURRENCES, false)
  protected val completionData = new BoolWidgetData("", true) {      
    val scalaCompletion = "org.scala-ide.sdt.core.scala_completions"
    val scalaJavaCompletion = "org.scala-ide.sdt.core.scala_java_completions"
      
    value = getStoredValue // initialize from preference store
    
    def getStoredValue: Boolean = {
      val currentExcluded: Array[String] = PreferenceConstants.getExcludedCompletionProposalCategories
      !(currentExcluded.contains(scalaCompletion) || currentExcluded.contains(scalaJavaCompletion))
    }
    
    override def saveToStore {
      updateValue
      if (value && !getStoredValue) {
        val currentExcluded: Array[String] = PreferenceConstants.getExcludedCompletionProposalCategories
        PreferenceConstants.setExcludedCompletionProposalCategories(
            currentExcluded.filterNot { cat => cat == scalaCompletion || cat == scalaJavaCompletion })
      }
    }
  }
  
  protected val autoActivationData = new BoolWidgetData(PreferenceConstants.CODEASSIST_AUTOACTIVATION, true)
  protected val activationDelayData = new IntWidgetData(PreferenceConstants.CODEASSIST_AUTOACTIVATION_DELAY, 500)
  
  protected var widgetDataList: List[WidgetData] = List(completionData, autoActivationData, activationDelayData) // , markOccurrencesData
     
  // helper classes for loading and storing widget values
  abstract class WidgetData {
    type T
    var value: T
    val recommendedVal: T
    def isRecommendedVal: Boolean = value == recommendedVal
    def saveToStore: Unit // save control contents to the preference store
    def updateWidget: Unit // set control contents to `value`
    def updateValue: Unit // set `value` to conents of widget
    def setToRecommended: Unit // set control contents to the recommended value
  }

  // this horrible class hierarchy is mostly this way because of erasure and the overloaded methods in IPreferenceStore
  // note the copy-pasted implementations of `saveToStore`. This is overloading at work.
  class IntWidgetData(keyName: String, val recommendedVal: Int) extends WidgetData {
    type T = Int
    var value: Int = prefStore.getInt(keyName)
    var textWidget: Text = null // can't be initialized in the constructor because widget won't have been created yet 
    def saveToStore {
      updateValue
      prefStore.setValue(keyName, value) 
    }
    def updateWidget { textWidget.setText(value.toString) }
    def updateValue {
      value = DiagnosticDialog.getIntOrError(textWidget.getText) match {
        case Left(error) => recommendedVal
        case Right(num) => num
      }
    }
    def setToRecommended { textWidget.setText(recommendedVal.toString) }
  }
  
  class BoolWidgetData(keyName: String, val recommendedVal: Boolean) extends WidgetData {
    type T = Boolean
    var value: Boolean = prefStore.getBoolean(keyName)
    var checkbox: Button = null // can't be initialized in the constructor because widget won't have been created yet
    
    def saveToStore = { 
      updateValue
      prefStore.setValue(keyName, value) // code duplication is due to overloading in IPreferenceStore.setValue    
    }
    def updateWidget { checkbox.setSelection(value) }
    def updateValue { value = checkbox.getSelection }
    def setToRecommended { checkbox.setSelection(recommendedVal) }
  }
  // end helper classes
    
  protected override def isResizable = true
    
  protected override def createContents(parent: Composite): Control = {
    val control = super.createContents(parent)
    doEnableDisable()
    refreshRadioButtons()
    getButton(IDialogConstants.OK_ID).setFocus
    control
  }
    
  protected override def createDialogArea(parent: Composite): Control = {
    parent.getShell.setText("Setup Diagnostics")
    val control = new Composite(parent, SWT.NONE)
    control.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, true))
    control.setLayout(new GridLayout)

    def newGroup(name: String, theParent: Composite, layout: GridLayout = new GridLayout(2, false)): Group = {
      val group = new Group(theParent, SWT.SHADOW_NONE)
      group.setText(name)
      group.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true))
      group.setLayout(layout)
      group
    }
    
    val weavingGroup = newGroup("JDT Weaving", control)    
    this.weavingButton = newCheckboxButton(weavingGroup, "Enable JDT weaving (required for Scala plugin)")
    weavingButton.setSelection(configurer.isWeaving)
    weavingButton.setEnabled(!configurer.isWeaving) // disable the control if weaving is already enabled
    if (!configurer.isWeaving) {
      new Label(weavingGroup, SWT.LEFT).setText("Note: change will take effect after workbench restart")
    }

    // radio buttons
    val radioGroup = newGroup("Scala JDT Settings", control)    

    val radioButtonListener = new SelectionAdapter {
      override def widgetSelected(e: SelectionEvent) {
        if (scalaSettingsButton.getSelection) {
          updating = true
          widgetDataList foreach { _.setToRecommended }
          updating = false
        }
        else {
          updating = true
          widgetDataList foreach { _.updateWidget }
          updating = false
        }
        doEnableDisable() 
      }    
    }
    this.scalaSettingsButton = newRadioButton(radioGroup, "Use recommended default settings", radioButtonListener)    
    this.otherSettingsButton = newRadioButton(radioGroup, "Use other settings", radioButtonListener)
    
    // the inner controls
    val innerGroup = newGroup("", radioGroup)
    innerGroup.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true, 2, 1))

    // Note: Mark Occurences is now working properly, so it doesn't need to be recommended to be turned off...
//    val markOccurrencesButton = 
//      newCheckboxButton(innerGroup, "Enable JDT \"Mark Occurrences\" (not recommended)", markOccurrencesData)
        
    val completionButton = 
      newCheckboxButton(innerGroup, "Use Scala-compatible JDT content assist proposals (recommended)", completionData)
    
    this.autoActivationButton = 
      newCheckboxButton(innerGroup, "Enable JDT content assist auto-activation (recommended)", autoActivationData)

    new Label(innerGroup, SWT.LEFT).setText("Content assist auto-activation delay (ms)")
    this.delayText = new Text(innerGroup, SWT.BORDER | SWT.SINGLE)
    delayText.setTextLimit(4)
    delayText.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false))
    delayText.setData(activationDelayData)
    activationDelayData.textWidget = delayText

    val heapGroup = new Group(control, SWT.SHADOW_NONE)
    heapGroup.setText("Heap settings")
    heapGroup.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true))
    heapGroup.setLayout(new GridLayout(1, true))
    
    new Label(heapGroup, SWT.LEFT).setText("Current maximum heap size: " + heapSize + "M")
    
    if (heapSize < recommendedHeap) {
      // create the warning label 
      val warningLabel = new Label(heapGroup, SWT.LEFT)
      warningLabel.setText("Warning: recommended value is at least " + recommendedHeap + "M")

      // set the font to bold
      val currentFont = warningLabel.getFont
      val fontData = currentFont.getFontData // returns an array of fonts due to different platforms having different font behavior
      fontData foreach { _.setStyle(SWT.BOLD) }
      this.boldFont = new Font(currentFont.getDevice, fontData)
      warningLabel.setFont(boldFont)
      
      val link = new Link(heapGroup, SWT.NONE)
      link.setText(
          "See <a href=\"http://wiki.eclipse.org/FAQ_How_do_I_increase_the_heap_size_available_to_Eclipse%3F\">" +
          "instructions for changing heap size</a>.")
                    
      link.addListener(SWT.Selection, DiagnosticDialog.linkListener)
    }

    val otherGroup = newGroup("Additional", control, new GridLayout(1, true))
    
    val knownIssuesLink = new Link(otherGroup, SWT.NONE)
    knownIssuesLink.setText("See list of <a href=\"https://www.assembla.com/wiki/show/scala-ide/Known_Issues\">known issues</a>" +
         " for known problems and workarounds")      
    knownIssuesLink.addListener(SWT.Selection, DiagnosticDialog.linkListener)
    
    errorMessageField = new Text(control, SWT.READ_ONLY | SWT.WRAP)
    errorMessageField.setLayoutData(new GridData(GridData.GRAB_HORIZONTAL | GridData.HORIZONTAL_ALIGN_FILL))
    errorMessageField.setBackground(errorMessageField.getDisplay.getSystemColor(SWT.COLOR_WIDGET_BACKGROUND))
   
    widgetDataList foreach { _.updateWidget }
        
    // IMPORTANT: add the listeners AFTER updating the widgets. Otherwise, the listeners will be triggered when setting the 
    // widget's initial values
    val selectionListener = new SelectionAdapter {
      override def widgetSelected(e: SelectionEvent) { 
        e.getSource.asInstanceOf[Control].getData.asInstanceOf[WidgetData].updateValue
        refreshRadioButtons()
        doEnableDisable() 
      }    
    }
    
//    markOccurrencesButton.addSelectionListener(selectionListener)
    autoActivationButton.addSelectionListener(selectionListener)
    completionButton.addSelectionListener(selectionListener)

    delayText.addModifyListener(new ModifyListener {
      def modifyText(e: ModifyEvent) { 
        verifyNumber(delayText.getText) 
        activationDelayData.updateValue
        refreshRadioButtons
      }
    })
    
    Dialog.applyDialogFont(parent)
    control
  }
    
  private def newCheckboxButton(parent: Composite, text: String): Button = {
    val button = new Button(parent, SWT.CHECK)
    button.setText(text)
    button.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, true, 2, 1))
    button
  }
  
  private def newCheckboxButton(parent: Composite, text: String, data: BoolWidgetData): Button = {
    val result = newCheckboxButton(parent, text)
    result.setData(data)
    data.checkbox = result
    result
  }
  
  private def newRadioButton(parent: Composite, text: String, listener: SelectionListener): Button = {
    val button = new Button(parent, SWT.RADIO)
    button.setText(text)
    button.addSelectionListener(listener)
    button
  }
  
  def doEnableDisable() {
    val selected = autoActivationButton.getSelection        
    if (!selected && errorMessageField.getData.asInstanceOf[Boolean]) {
      delayText.setText("")
      setErrorMessage(None) // clear the error if there was one
    } else if (selected) { 
      verifyNumber(delayText.getText)
    }
    
    delayText.setEnabled(autoActivationButton.getSelection)
  }
  
  def refreshRadioButtons() {
    if (!updating) {
      val isDefault = widgetDataList forall { _.isRecommendedVal }
      scalaSettingsButton.setSelection(isDefault)
      otherSettingsButton.setSelection(!isDefault)
    }
  }
  
  def verifyNumber(text: String) = {
    DiagnosticDialog.getIntOrError(text) match {
      case Left(msg) => setErrorMessage(Option(msg))
      case _ => setErrorMessage(None)
    }
  }
    
  def setErrorMessage(msg: Option[String]) {
    if (errorMessageField != null && !errorMessageField.isDisposed) {
      errorMessageField.setText(msg.getOrElse(" \n "))
      errorMessageField.setData(msg.isDefined)

      // ** copied from org.eclipse.jface.dialogs.InputDialog.setErrorMessage(String) **
      // Disable the error message text control if there is no error, or no error text (empty or whitespace only).  
      // Hide it also to avoid color change.  See https://bugs.eclipse.org/bugs/show_bug.cgi?id=130281
      errorMessageField.setEnabled(msg.isDefined)
      errorMessageField.setVisible(msg.isDefined)
      errorMessageField.getParent.update

      getButton(IDialogConstants.OK_ID).setEnabled(msg.isEmpty)
    }    
  }
  
  override def okPressed {
    widgetDataList foreach { _.saveToStore }
    val doEnableWeaving = weavingButton.getSelection && !configurer.isWeaving
    super.okPressed
    if (doEnableWeaving)
      turnWeavingOn()
  }
  
  override def close: Boolean = {
    if (boldFont != null) 
      boldFont.dispose
    super.close
  }
  
  def turnWeavingOn() {
//    JDTWeavingPreferences.setAskToEnableWeaving(false)
    
    val changeResult: IStatus = configurer.changeWeavingState(true)    
    
    if (changeResult.getSeverity <= IStatus.WARNING) {
      val note = 
        if (changeResult.getSeverity == IStatus.WARNING)
          "\n\n(Note: weaving status changed, but there were warnings. See the error log for more details.)"
        else ""
      val restart = MessageDialog.openQuestion(shell, "Restart Eclipse?",
          "Weaving will be enabled only after restarting the workbench.\n\nRestart now?" + note)
      if (restart)
        PlatformUI.getWorkbench.restart      
    } else {
      showFailureDialog(changeResult)
    }
  }  
  
  def showFailureDialog(result: IStatus) {
    val changeInstructions =     
"""Error: could not enable JDT weaving (see detail below).

To turn on JDT aspect weaving manually:
   1. Open the file <eclipse-install-dir>/eclipse/configuration/config.ini
   2. Look for the line "osgi.framework.extensions"
      a) If it *doesn't* exist, add the line "osgi.framework.extensions=org.eclipse.equinox.weaving.hook"
      b) If it does exist, append ",org.eclipse.equinox.weaving.hook" 

To disable JDT weaving manually, remove "org.eclipse.equinox.weaving.hook" or the entire line "osgi.framework.extensions" as applicable.
"""
    ErrorDialog.openError(shell, "Error enabling JDT weaving", changeInstructions, result);    
  }
}

object DiagnosticDialog {
  /** Returns either an error message or the integer value of `number` */
  def getIntOrError(number: String): Either[String, Int] = {
    if (number.isEmpty) {
      Left(PreferencesMessages.SpellingPreferencePage_empty_threshold)
    } else {
      try {
        val result = number.toInt
        if (result <= 0) Left(Messages.format(PreferencesMessages.SpellingPreferencePage_invalid_threshold, number))
        else Right(result)
      } catch {
        case e: NumberFormatException =>
          Left(Messages.format(PreferencesMessages.SpellingPreferencePage_invalid_threshold, number))
      }
    }    
  }
  
  object linkListener extends Listener {
    def handleEvent(e: Event) {
      try {
        val browserSupport = PlatformUI.getWorkbench.getBrowserSupport
        browserSupport.getExternalBrowser.openURL(new java.net.URL(e.text))
      } catch {
        case e: Exception => e.printStackTrace
      }
    }  
  }  
}
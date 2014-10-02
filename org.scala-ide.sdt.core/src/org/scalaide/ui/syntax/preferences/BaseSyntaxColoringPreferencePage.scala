package org.scalaide.ui.syntax.preferences

import scala.PartialFunction.condOpt
import org.eclipse.core.runtime.preferences.InstanceScope
import org.eclipse.jdt.internal.ui.preferences._
import org.eclipse.jdt.internal.ui.preferences.OverlayPreferenceStore.BOOLEAN
import org.eclipse.jdt.internal.ui.preferences.OverlayPreferenceStore.OverlayKey
import org.eclipse.jdt.internal.ui.preferences.OverlayPreferenceStore.STRING
import org.eclipse.jface.preference._
import org.eclipse.jface.text.source.SourceViewer
import org.eclipse.jface.viewers._
import org.eclipse.swt.SWT
import org.eclipse.swt.events.SelectionEvent
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.widgets.{ List => SWT_List, _ }
import org.eclipse.ui.IWorkbench
import org.eclipse.ui.IWorkbenchPreferencePage
import org.eclipse.ui.dialogs.PreferencesUtil
import org.scalaide.core.SdtConstants
import org.scalaide.ui.internal.preferences.SyntaxColoringTreeContentAndLabelProvider
import org.scalaide.ui.syntax.ScalaSyntaxClass
import org.scalaide.ui.syntax.ScalaSyntaxClass.Category
import org.scalaide.ui.syntax.ScalaSyntaxClasses
import org.scalaide.util.internal.eclipse.EclipseUtils._
import org.scalaide.util.internal.eclipse.SWTUtils._
import org.scalaide.util.ui.SWTUtils.gridData
import org.scalaide.ui.internal.preferences.PreviewerFactory

/** Base class to create a syntax coloring preference page.
 *
 *  This class can be implemented by clients. The methods [[additionalOverlayKeys]], [[additionalPerformDefaults]], [[additionalCreateContent]]
 *  can be overridden if additional preferences should be configurable through the preference page. None of the other methods are expected to be overridden.
 *
 *  @param categories the list of categories to configure
 *  @param defaultCategory the category selected and expanded when the preference page is open
 *  @param preferenceStore the preference store where to read and store the configuration
 *  @param previewText the text to display in the previewer
 *  @param previewerFactory the factory for the previewer, a text area showing the `previewText`
 */
abstract class BaseSyntaxColoringPreferencePage(categories: List[ScalaSyntaxClass.Category], defaultCategory: Category, preferenceStore: IPreferenceStore, previewText: String, previewerFactoryConfiguration: PreviewerFactoryConfiguration) extends PreferencePage with IWorkbenchPreferencePage {

  /** Creates the additional UI elements to configure the additional preferences.
   *
   *  The parent composite can be used directly, without additional composite. It is configured with a 2 columns [[GridLayout]] layout.
   */
  protected def additionalCreateContent(parent: Composite) {}

  /** Returns the keys of the additional preferences to configure.
   */
  def additionalOverlayKeys: List[OverlayKey] = Nil

  /** Additional actions to be performed when the `Restore Default` button is used. Usually,
   *  resetting the UI according to the preferences defaults.
   */
  def additionalPerformDefaults() {}

  import org.scalaide.util.ui.SWTUtils._

  setPreferenceStore(preferenceStore)
  protected val overlayStore = makeOverlayPreferenceStore
  private var previewerFactory = new PreviewerFactory(previewerFactoryConfiguration)

  private var foregroundColorEditorLabel: Label = _
  private var syntaxForegroundColorEditor: ColorSelector = _
  private var backgroundColorEditorLabel: Label = _
  private var syntaxBackgroundColorEditor: ColorSelector = _
  private var enabledCheckBox: Button = _
  private var backgroundColorEnabledCheckBox: Button = _
  private var foregroundColorButton: Button = _
  private var backgroundColorButton: Button = _
  private var boldCheckBox: Button = _
  private var italicCheckBox: Button = _
  private var underlineCheckBox: Button = _
  private var treeViewer: TreeViewer = _
  private var previewer: SourceViewer = _

  def init(workbench: IWorkbench) {}

  def createContents(parent: Composite): Control = {
    initializeDialogUnits(parent)

    createSyntaxPage(parent)
  }

  import OverlayPreferenceStore._
  private def makeOverlayKeys(syntaxClass: ScalaSyntaxClass): List[OverlayKey] = {
    List(
      new OverlayKey(BOOLEAN, syntaxClass.enabledKey),
      new OverlayKey(STRING, syntaxClass.foregroundColorKey),
      new OverlayKey(STRING, syntaxClass.backgroundColorKey),
      new OverlayKey(BOOLEAN, syntaxClass.backgroundColorEnabledKey),
      new OverlayKey(BOOLEAN, syntaxClass.boldKey),
      new OverlayKey(BOOLEAN, syntaxClass.italicKey),
      new OverlayKey(BOOLEAN, syntaxClass.underlineKey))
  }

  private def allSyntaxClasses = categories.flatMap { _.children }

  private def makeOverlayPreferenceStore = {
    val keys = additionalOverlayKeys ::: allSyntaxClasses.flatMap(makeOverlayKeys)
    new OverlayPreferenceStore(getPreferenceStore, keys.toArray)
  }

  override def performOk() = {
    super.performOk()
    overlayStore.propagate()
    InstanceScope.INSTANCE.getNode(SdtConstants.PluginId).flush()
    true
  }

  override def dispose() {
    overlayStore.stop()
    super.dispose()
  }

  override def performDefaults() {
    super.performDefaults()
    overlayStore.loadDefaults()
    handleSyntaxColorListSelection()
    additionalPerformDefaults()
  }

  def createTreeViewer(editorComposite: Composite) {
    treeViewer = new TreeViewer(editorComposite, SWT.SINGLE | SWT.BORDER)

    val contentAndLabelProvider = new SyntaxColoringTreeContentAndLabelProvider(categories)
    treeViewer.setContentProvider(contentAndLabelProvider)
    treeViewer.setLabelProvider(contentAndLabelProvider)

    treeViewer.getControl.setLayoutData(gridData(
      horizontalAlignment = SWT.FILL,
      verticalAlignment = SWT.FILL,
      grabExcessHorizontalSpace = true,
      grabExcessVerticalSpace = false,
      verticalSpan = 2))

    treeViewer.addDoubleClickListener { event: DoubleClickEvent =>
      val element = event.getSelection.asInstanceOf[IStructuredSelection].getFirstElement
      if (treeViewer.isExpandable(element))
        treeViewer.setExpandedState(element, !treeViewer.getExpandedState(element))
    }

    treeViewer.addSelectionChangedListener { () =>
      handleSyntaxColorListSelection()
    }

    treeViewer.setInput(new Object)
  }

  def createSyntaxPage(parent: Composite): Control = {
    overlayStore.load()
    overlayStore.start()

    val composite = new Composite(parent, SWT.NONE)
    val layout = new GridLayout(2, /*makeColumnsEqualWidth*/ true)
    layout.marginHeight = 0
    layout.marginWidth = 0
    composite.setLayout(layout)

    val link = new Link(composite, SWT.NONE)
    link.setText(PreferencesMessages.JavaEditorColoringConfigurationBlock_link)
    link.addSelectionListener { e: SelectionEvent =>
      PreferencesUtil.createPreferenceDialogOn(parent.getShell, e.text, null, null)
    }
    link.setLayoutData(gridData(
      horizontalAlignment = SWT.FILL,
      verticalAlignment = SWT.BEGINNING,
      grabExcessHorizontalSpace = true,
      grabExcessVerticalSpace = false,
      horizontalSpan = 2))

    val filler = new Label(composite, SWT.LEFT)
    filler.setLayoutData(gridData(
      horizontalAlignment = SWT.FILL,
      horizontalSpan = 2))

    additionalCreateContent(composite)

    val elementLabel = new Label(composite, SWT.LEFT)
    elementLabel.setText(PreferencesMessages.JavaEditorPreferencePage_coloring_element)
    elementLabel.setLayoutData(gridData(horizontalSpan = 2))

    createTreeViewer(composite)

    val stylesGroup = new Group(composite, SWT.NONE)
    stylesGroup.setText("style")
    stylesGroup.setLayout(new GridLayout(2, /*makeColumnsEqualWidth*/ false))
    stylesGroup.setLayoutData(gridData(verticalAlignment = SWT.TOP))

    enabledCheckBox = new Button(stylesGroup, SWT.CHECK)
    enabledCheckBox.setText(PreferencesMessages.JavaEditorPreferencePage_enable)
    enabledCheckBox.setLayoutData(gridData(horizontalSpan = 2))

    foregroundColorEditorLabel = new Label(stylesGroup, SWT.LEFT)
    foregroundColorEditorLabel.setText("Foreground:")
    foregroundColorEditorLabel.setLayoutData(gridData())

    syntaxForegroundColorEditor = new ColorSelector(stylesGroup)
    foregroundColorButton = syntaxForegroundColorEditor.getButton
    foregroundColorButton.setLayoutData(gridData())

    backgroundColorEditorLabel = new Label(stylesGroup, SWT.LEFT)
    backgroundColorEditorLabel.setText("Background:")
    backgroundColorEditorLabel.setLayoutData(gridData())

    syntaxBackgroundColorEditor = new ColorSelector(stylesGroup)
    backgroundColorButton = syntaxBackgroundColorEditor.getButton
    backgroundColorButton.setLayoutData(gridData())

    backgroundColorEnabledCheckBox = new Button(stylesGroup, SWT.CHECK)
    backgroundColorEnabledCheckBox.setText("Paint background")
    backgroundColorEnabledCheckBox.setLayoutData(gridData(horizontalSpan = 2))

    boldCheckBox = new Button(stylesGroup, SWT.CHECK)
    boldCheckBox.setText(PreferencesMessages.JavaEditorPreferencePage_bold)
    boldCheckBox.setLayoutData(gridData(horizontalSpan = 2))

    italicCheckBox = new Button(stylesGroup, SWT.CHECK)
    italicCheckBox.setText(PreferencesMessages.JavaEditorPreferencePage_italic)
    italicCheckBox.setLayoutData(gridData(horizontalSpan = 2))

    underlineCheckBox = new Button(stylesGroup, SWT.CHECK)
    underlineCheckBox.setText(PreferencesMessages.JavaEditorPreferencePage_underline)
    underlineCheckBox.setLayoutData(gridData(horizontalSpan = 2))

    val previewLabel = new Label(composite, SWT.LEFT)
    previewLabel.setText(PreferencesMessages.JavaEditorPreferencePage_preview)
    previewLabel.setLayoutData(gridData(horizontalSpan = 2))

    previewer = createPreviewer(composite)
    val previewerControl = previewer.getControl
    val gd = gridData(
      horizontalAlignment = SWT.FILL,
      verticalAlignment = SWT.FILL,
      grabExcessHorizontalSpace = true,
      grabExcessVerticalSpace = true,
      horizontalSpan = 2)
    gd.widthHint = convertWidthInCharsToPixels(20)
    gd.heightHint = convertHeightInCharsToPixels(12)
    previewerControl.setLayoutData(gd)

    setUpSelectionListeners()

    treeViewer.setSelection(new StructuredSelection(defaultCategory))
    treeViewer.expandToLevel(defaultCategory, 1)

    composite
  }

  private def setUpSelectionListeners() {
    enabledCheckBox.addSelectionListener { () =>
      for (syntaxClass <- selectedSyntaxClass)
        overlayStore.setValue(syntaxClass.enabledKey, enabledCheckBox.getSelection)
    }
    foregroundColorButton.addSelectionListener { () =>
      for (syntaxClass <- selectedSyntaxClass)
        PreferenceConverter.setValue(overlayStore, syntaxClass.foregroundColorKey, syntaxForegroundColorEditor.getColorValue)
    }
    backgroundColorButton.addSelectionListener { () =>
      for (syntaxClass <- selectedSyntaxClass)
        PreferenceConverter.setValue(overlayStore, syntaxClass.backgroundColorKey, syntaxBackgroundColorEditor.getColorValue)
    }
    backgroundColorEnabledCheckBox.addSelectionListener { () =>
      for (syntaxClass <- selectedSyntaxClass) {
        overlayStore.setValue(syntaxClass.backgroundColorEnabledKey, backgroundColorEnabledCheckBox.getSelection)
        backgroundColorButton.setEnabled(backgroundColorEnabledCheckBox.getSelection)
      }
    }
    boldCheckBox.addSelectionListener { () =>
      for (syntaxClass <- selectedSyntaxClass)
        overlayStore.setValue(syntaxClass.boldKey, boldCheckBox.getSelection)
    }
    italicCheckBox.addSelectionListener { () =>
      for (syntaxClass <- selectedSyntaxClass)
        overlayStore.setValue(syntaxClass.italicKey, italicCheckBox.getSelection)
    }
    underlineCheckBox.addSelectionListener { () =>
      for (syntaxClass <- selectedSyntaxClass)
        overlayStore.setValue(syntaxClass.underlineKey, underlineCheckBox.getSelection)
    }
  }

  private def createPreviewer(parent: Composite): SourceViewer = {
    previewerFactory.createPreviewer(parent, overlayStore, previewText)
  }

  private def selectedSyntaxClass: Option[ScalaSyntaxClass] = condOpt(treeViewer.getSelection) {
    case SelectedItems(syntaxClass: ScalaSyntaxClass) => syntaxClass
  }

  private def massSetEnablement(enabled: Boolean) = {
    val widgets = List(enabledCheckBox, syntaxForegroundColorEditor.getButton, foregroundColorEditorLabel,
      syntaxBackgroundColorEditor.getButton, backgroundColorEditorLabel, backgroundColorEnabledCheckBox, boldCheckBox,
      italicCheckBox, underlineCheckBox)
    widgets foreach { _.setEnabled(enabled) }
  }

  protected def handleSyntaxColorListSelection() = selectedSyntaxClass match {
    case None =>
      massSetEnablement(false)
    case Some(syntaxClass) =>
      val isSemanticClass = ScalaSyntaxClasses.scalaSemanticCategory.children contains syntaxClass
      if (isSemanticClass && !(overlayStore getBoolean ScalaSyntaxClasses.ENABLE_SEMANTIC_HIGHLIGHTING))
        massSetEnablement(false)
      else {
        import syntaxClass._
        syntaxForegroundColorEditor.setColorValue(overlayStore getColor foregroundColorKey)
        syntaxBackgroundColorEditor.setColorValue(overlayStore getColor backgroundColorKey)
        val backgroundColorEnabled = overlayStore getBoolean backgroundColorEnabledKey
        backgroundColorEnabledCheckBox.setSelection(backgroundColorEnabled)
        enabledCheckBox.setSelection(overlayStore getBoolean enabledKey)
        boldCheckBox.setSelection(overlayStore getBoolean boldKey)
        italicCheckBox.setSelection(overlayStore getBoolean italicKey)
        underlineCheckBox.setSelection(overlayStore getBoolean underlineKey)

        massSetEnablement(true)
        enabledCheckBox.setEnabled(canBeDisabled)
        syntaxBackgroundColorEditor.getButton.setEnabled(backgroundColorEnabled)
        syntaxForegroundColorEditor.getButton.setEnabled(hasForegroundColor)
      }
  }

}

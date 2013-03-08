package scala.tools.eclipse.properties.syntaxcolouring

import scala.PartialFunction.condOpt
import scala.tools.eclipse.properties.syntaxcolouring.ScalaSyntaxClasses._
import scala.tools.eclipse.util.EclipseUtils._
import scala.tools.eclipse.util.SWTUtils._
import scala.tools.eclipse._

import org.eclipse.jdt.internal.ui.preferences.OverlayPreferenceStore._
import org.eclipse.jdt.internal.ui.preferences._
import org.eclipse.jface.layout.PixelConverter
import org.eclipse.jface.preference._
import org.eclipse.jface.text.source.SourceViewer
import org.eclipse.jface.util.PropertyChangeEvent
import org.eclipse.jface.viewers._
import org.eclipse.swt.events.SelectionEvent
import org.eclipse.swt.layout._
import org.eclipse.swt.widgets.{ List => _, _ }
import org.eclipse.swt.SWT
import org.eclipse.ui.dialogs.PreferencesUtil
import org.eclipse.ui.IWorkbench
import org.eclipse.ui.IWorkbenchPreferencePage

import SyntaxColouringPreviewText.ColouringLocation

/**
 * @see org.eclipse.jdt.internal.ui.preferences.JavaEditorColoringConfigurationBlock
 */
class SyntaxColouringPreferencePage extends PreferencePage with IWorkbenchPreferencePage {

  import GridDataHelper._

  setPreferenceStore(ScalaPlugin.prefStore)
  private val overlayStore = makeOverlayPreferenceStore

  private var enableSemanticHighlightingCheckBox: Button = _
  private var extraAccuracyCheckBox: Button = _
  private var strikethroughDeprecatedCheckBox: Button = _
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

    val scrolled = new ScrolledPageContent(parent, SWT.H_SCROLL | SWT.V_SCROLL)
    scrolled.setExpandHorizontal(true)
    scrolled.setExpandVertical(true)

    val control = createSyntaxPage(scrolled)

    scrolled.setContent(control)
    val size = control.computeSize(SWT.DEFAULT, SWT.DEFAULT)
    scrolled.setMinSize(size.x, size.y)

    scrolled
  }

  import OverlayPreferenceStore._
  private def makeOverlayKeys(syntaxClass: ScalaSyntaxClass): List[OverlayKey] = {
    List(
      new OverlayKey(BOOLEAN, syntaxClass.enabledKey),
      new OverlayKey(STRING, syntaxClass.foregroundColourKey),
      new OverlayKey(STRING, syntaxClass.backgroundColourKey),
      new OverlayKey(BOOLEAN, syntaxClass.backgroundColourEnabledKey),
      new OverlayKey(BOOLEAN, syntaxClass.boldKey),
      new OverlayKey(BOOLEAN, syntaxClass.italicKey),
      new OverlayKey(BOOLEAN, syntaxClass.underlineKey))
  }

  def makeOverlayPreferenceStore = {
    val keys =
      new OverlayKey(BOOLEAN, ENABLE_SEMANTIC_HIGHLIGHTING) ::
        new OverlayKey(BOOLEAN, USE_SYNTACTIC_HINTS) ::
        new OverlayKey(BOOLEAN, STRIKETHROUGH_DEPRECATED) ::
        ALL_SYNTAX_CLASSES.flatMap(makeOverlayKeys)
    new OverlayPreferenceStore(getPreferenceStore, keys.toArray)
  }

  override def performOk() = {
    super.performOk()
    overlayStore.propagate()
    ScalaPlugin.plugin.savePluginPreferences()
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
    enableSemanticHighlightingCheckBox.setSelection(overlayStore getBoolean ENABLE_SEMANTIC_HIGHLIGHTING)
    extraAccuracyCheckBox.setEnabled(enableSemanticHighlightingCheckBox.getSelection)
    strikethroughDeprecatedCheckBox.setEnabled(enableSemanticHighlightingCheckBox.getSelection)
    extraAccuracyCheckBox.setSelection(overlayStore getBoolean USE_SYNTACTIC_HINTS)
    strikethroughDeprecatedCheckBox.setSelection(overlayStore getBoolean STRIKETHROUGH_DEPRECATED)
  }

  def createTreeViewer(editorComposite: Composite) {
    treeViewer = new TreeViewer(editorComposite, SWT.SINGLE | SWT.BORDER)

    treeViewer.setContentProvider(SyntaxColouringTreeContentAndLabelProvider)
    treeViewer.setLabelProvider(SyntaxColouringTreeContentAndLabelProvider)

    // scrollbars and tree indentation guess
    val widthHint = ALL_SYNTAX_CLASSES.map { syntaxClass => convertWidthInCharsToPixels(syntaxClass.displayName.length) }.max +
      Option(treeViewer.getControl.asInstanceOf[Scrollable].getVerticalBar).map { _.getSize.x * 3 }.getOrElse(0)

    treeViewer.getControl.setLayoutData(gridData(
      horizontalAlignment = SWT.BEGINNING,
      verticalAlignment = SWT.BEGINNING,
      grabExcessHorizontalSpace = false,
      grabExcessVerticalSpace = true,
      widthHint = widthHint,
      heightHint = convertHeightInCharsToPixels(11)))

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

  private def gridLayout(marginHeight: Int = 5, marginWidth: Int = 5, numColumns: Int = 1): GridLayout = {
    val layout = new GridLayout
    layout.marginHeight = marginHeight
    layout.marginWidth = marginWidth
    layout.numColumns = numColumns
    layout
  }

  def createSyntaxPage(parent: Composite): Control = {
    overlayStore.load()
    overlayStore.start()

    val outerComposite = new Composite(parent, SWT.NONE)
    outerComposite.setLayout(gridLayout(marginHeight = 0, marginWidth = 0))

    val link = new Link(outerComposite, SWT.NONE)
    link.setText(PreferencesMessages.JavaEditorColoringConfigurationBlock_link)
    link.addSelectionListener { e: SelectionEvent =>
      PreferencesUtil.createPreferenceDialogOn(parent.getShell, e.text, null, null)
    }
    link.setLayoutData(gridData(
      horizontalAlignment = SWT.FILL,
      verticalAlignment = SWT.BEGINNING,
      grabExcessHorizontalSpace = true,
      grabExcessVerticalSpace = false,
      widthHint = 150,
      horizontalSpan = 2))

    val filler = new Label(outerComposite, SWT.LEFT)
    filler.setLayoutData(gridData(
      horizontalAlignment = SWT.FILL,
      horizontalSpan = 1,
      heightHint = new PixelConverter(outerComposite).convertHeightInCharsToPixels(1) / 2))

    enableSemanticHighlightingCheckBox = new Button(outerComposite, SWT.CHECK)
    enableSemanticHighlightingCheckBox.setText("Enable semantic highlighting")
    enableSemanticHighlightingCheckBox.setLayoutData(new GridData(GridData.FILL_HORIZONTAL))
    enableSemanticHighlightingCheckBox.setSelection(overlayStore.getBoolean(ENABLE_SEMANTIC_HIGHLIGHTING))

    extraAccuracyCheckBox = new Button(outerComposite, SWT.CHECK)
    extraAccuracyCheckBox.setText("Use slower but more accurate semantic highlighting")
    extraAccuracyCheckBox.setLayoutData(new GridData(GridData.FILL_HORIZONTAL))
    extraAccuracyCheckBox.setSelection(overlayStore.getBoolean(USE_SYNTACTIC_HINTS))
    extraAccuracyCheckBox.setEnabled(enableSemanticHighlightingCheckBox.getSelection)

    strikethroughDeprecatedCheckBox = new Button(outerComposite, SWT.CHECK)
    strikethroughDeprecatedCheckBox.setText("Strikethrough deprecated symbols")
    strikethroughDeprecatedCheckBox.setLayoutData(new GridData(GridData.FILL_HORIZONTAL))
    strikethroughDeprecatedCheckBox.setSelection(overlayStore.getBoolean(STRIKETHROUGH_DEPRECATED))
    strikethroughDeprecatedCheckBox.setEnabled(enableSemanticHighlightingCheckBox.getSelection)

    val elementLabel = new Label(outerComposite, SWT.LEFT)
    elementLabel.setText(PreferencesMessages.JavaEditorPreferencePage_coloring_element)
    elementLabel.setLayoutData(new GridData(GridData.FILL_HORIZONTAL))

    val elementEditorComposite = new Composite(outerComposite, SWT.NONE)
    elementEditorComposite.setLayout(gridLayout(marginHeight = 0, marginWidth = 0, numColumns = 2))
    elementEditorComposite.setLayoutData(gridData(
      horizontalAlignment = SWT.FILL, verticalAlignment = SWT.BEGINNING, grabExcessHorizontalSpace = true,
      grabExcessVerticalSpace = false))

    createTreeViewer(elementEditorComposite)

    val stylesComposite = new Composite(elementEditorComposite, SWT.NONE)
    stylesComposite.setLayout(gridLayout(marginHeight = 0, marginWidth = 0, numColumns = 2))
    stylesComposite.setLayoutData(new GridData(GridData.FILL_BOTH))

    enabledCheckBox = new Button(stylesComposite, SWT.CHECK)
    enabledCheckBox.setText(PreferencesMessages.JavaEditorPreferencePage_enable)
    enabledCheckBox.setLayoutData(gridData(
      horizontalAlignment = GridData.BEGINNING, horizontalIndent = 0, horizontalSpan = 2))

    foregroundColorEditorLabel = new Label(stylesComposite, SWT.LEFT)
    foregroundColorEditorLabel.setText("Foreground:")

    foregroundColorEditorLabel.setLayoutData(gridData(horizontalAlignment = GridData.BEGINNING, horizontalIndent = 20))

    syntaxForegroundColorEditor = new ColorSelector(stylesComposite)
    foregroundColorButton = syntaxForegroundColorEditor.getButton
    foregroundColorButton.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING))

    backgroundColorEditorLabel = new Label(stylesComposite, SWT.LEFT)
    backgroundColorEditorLabel.setText("Background:")

    backgroundColorEditorLabel.setLayoutData(gridData(horizontalAlignment = GridData.BEGINNING, horizontalIndent = 20))

    syntaxBackgroundColorEditor = new ColorSelector(stylesComposite)
    backgroundColorButton = syntaxBackgroundColorEditor.getButton
    backgroundColorButton.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING))

    backgroundColorEnabledCheckBox = new Button(stylesComposite, SWT.CHECK)
    backgroundColorEnabledCheckBox.setText("Paint background")

    backgroundColorEnabledCheckBox.setLayoutData(gridData(
      horizontalAlignment = GridData.BEGINNING, horizontalIndent = 20, horizontalSpan = 2))

    boldCheckBox = new Button(stylesComposite, SWT.CHECK)
    boldCheckBox.setText(PreferencesMessages.JavaEditorPreferencePage_bold)

    boldCheckBox.setLayoutData(gridData(
      horizontalAlignment = GridData.BEGINNING, horizontalIndent = 20, horizontalSpan = 2))

    italicCheckBox = new Button(stylesComposite, SWT.CHECK)
    italicCheckBox.setText(PreferencesMessages.JavaEditorPreferencePage_italic)
    italicCheckBox.setLayoutData(gridData(
      horizontalAlignment = GridData.BEGINNING, horizontalIndent = 20, horizontalSpan = 2))

    underlineCheckBox = new Button(stylesComposite, SWT.CHECK)
    underlineCheckBox.setText(PreferencesMessages.JavaEditorPreferencePage_underline)
    underlineCheckBox.setLayoutData(
      gridData(horizontalAlignment = GridData.BEGINNING, horizontalIndent = 20, horizontalSpan = 2))

    val previewLabel = new Label(outerComposite, SWT.LEFT)
    previewLabel.setText(PreferencesMessages.JavaEditorPreferencePage_preview)
    previewLabel.setLayoutData(new GridData(GridData.FILL_HORIZONTAL))

    previewer = createPreviewer(outerComposite)
    val previewerControl = previewer.getControl
    previewerControl.setLayoutData(gridData(
      horizontalAlignment = GridData.FILL,
      verticalAlignment = GridData.FILL,
      grabExcessHorizontalSpace = true,
      grabExcessVerticalSpace = true,
      widthHint = convertWidthInCharsToPixels(20),
      heightHint = convertHeightInCharsToPixels(12)))
    updatePreviewerColours()

    setUpSelectionListeners()

    treeViewer.setSelection(new StructuredSelection(scalaSyntacticCategory))

    outerComposite.layout(false)
    outerComposite
  }

  private def setUpSelectionListeners() {
    overlayStore.addPropertyChangeListener { event: PropertyChangeEvent =>
      updatePreviewerColours()
    }
    enableSemanticHighlightingCheckBox.addSelectionListener { () =>
      overlayStore.setValue(ENABLE_SEMANTIC_HIGHLIGHTING, enableSemanticHighlightingCheckBox.getSelection)
      extraAccuracyCheckBox.setEnabled(enableSemanticHighlightingCheckBox.getSelection)
      strikethroughDeprecatedCheckBox.setEnabled(enableSemanticHighlightingCheckBox.getSelection)
      handleSyntaxColorListSelection()
    }
    extraAccuracyCheckBox.addSelectionListener { () =>
      overlayStore.setValue(USE_SYNTACTIC_HINTS, extraAccuracyCheckBox.getSelection)
    }
    strikethroughDeprecatedCheckBox.addSelectionListener { () =>
      overlayStore.setValue(STRIKETHROUGH_DEPRECATED, strikethroughDeprecatedCheckBox.getSelection)
    }
    enabledCheckBox.addSelectionListener { () =>
      for (syntaxClass <- selectedSyntaxClass)
        overlayStore.setValue(syntaxClass.enabledKey, enabledCheckBox.getSelection)
    }
    foregroundColorButton.addSelectionListener { () =>
      for (syntaxClass <- selectedSyntaxClass)
        PreferenceConverter.setValue(overlayStore, syntaxClass.foregroundColourKey, syntaxForegroundColorEditor.getColorValue)
    }
    backgroundColorButton.addSelectionListener { () =>
      for (syntaxClass <- selectedSyntaxClass)
        PreferenceConverter.setValue(overlayStore, syntaxClass.backgroundColourKey, syntaxBackgroundColorEditor.getColorValue)
    }
    backgroundColorEnabledCheckBox.addSelectionListener { () =>
      for (syntaxClass <- selectedSyntaxClass) {
        overlayStore.setValue(syntaxClass.backgroundColourEnabledKey, backgroundColorEnabledCheckBox.getSelection)
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

  private def createPreviewer(parent: Composite): SourceViewer =
    ScalaPreviewerFactory.createPreviewer(parent, overlayStore, SyntaxColouringPreviewText.previewText)

  private def selectedSyntaxClass: Option[ScalaSyntaxClass] = condOpt(treeViewer.getSelection) {
    case SelectedItems(syntaxClass: ScalaSyntaxClass) => syntaxClass
  }

  private def massSetEnablement(enabled: Boolean) = {
    val widgets = List(enabledCheckBox, syntaxForegroundColorEditor.getButton, foregroundColorEditorLabel,
      syntaxBackgroundColorEditor.getButton, backgroundColorEditorLabel, backgroundColorEnabledCheckBox, boldCheckBox,
      italicCheckBox, underlineCheckBox)
    widgets foreach { _.setEnabled(enabled) }
  }

  private def handleSyntaxColorListSelection() = selectedSyntaxClass match {
    case None =>
      massSetEnablement(false)
    case Some(syntaxClass) =>
      val isSemanticClass = ScalaSyntaxClasses.scalaSemanticCategory.children contains syntaxClass
      if (isSemanticClass && !(overlayStore getBoolean ENABLE_SEMANTIC_HIGHLIGHTING))
        massSetEnablement(false)
      else {
        import syntaxClass._
        syntaxForegroundColorEditor.setColorValue(overlayStore getColor foregroundColourKey)
        syntaxBackgroundColorEditor.setColorValue(overlayStore getColor backgroundColourKey)
        val backgroundColorEnabled = overlayStore getBoolean backgroundColourEnabledKey
        backgroundColorEnabledCheckBox.setSelection(backgroundColorEnabled)
        enabledCheckBox.setSelection(overlayStore getBoolean enabledKey)
        boldCheckBox.setSelection(overlayStore getBoolean boldKey)
        italicCheckBox.setSelection(overlayStore getBoolean italicKey)
        underlineCheckBox.setSelection(overlayStore getBoolean underlineKey)

        massSetEnablement(true)
        enabledCheckBox.setEnabled(canBeDisabled)
        syntaxBackgroundColorEditor.getButton.setEnabled(backgroundColorEnabled)
      }
  }

  private def updatePreviewerColours() {
    val textWidget = previewer.getTextWidget
    for (ColouringLocation(syntaxClass, offset, length) <- SyntaxColouringPreviewText.semanticLocations)
      if (overlayStore.getBoolean(ENABLE_SEMANTIC_HIGHLIGHTING) && overlayStore.getBoolean(syntaxClass.enabledKey)) {
        val styleRange = syntaxClass.getStyleRange(overlayStore)
        styleRange.start = offset
        styleRange.length = length
        textWidget.setStyleRange(styleRange)
      }
  }

}

object GridDataHelper {
  def gridData(
    horizontalAlignment: Int = SWT.BEGINNING,
    verticalAlignment: Int = SWT.CENTER,
    grabExcessHorizontalSpace: Boolean = false,
    grabExcessVerticalSpace: Boolean = false,
    widthHint: Int = SWT.DEFAULT,
    heightHint: Int = SWT.DEFAULT,
    horizontalSpan: Int = 1,
    horizontalIndent: Int = 0): GridData =
    {
      val gridData = new GridData(horizontalAlignment, verticalAlignment, grabExcessHorizontalSpace,
        grabExcessVerticalSpace)
      gridData.widthHint = widthHint
      gridData.heightHint = heightHint
      gridData.horizontalSpan = horizontalSpan
      gridData.horizontalIndent = horizontalIndent
      gridData
    }
}
package scala.tools.eclipse.properties

import java.util.HashMap
import org.eclipse.jdt.internal.ui.preferences.OverlayPreferenceStore
import PartialFunction.condOpt
import org.eclipse.jface.viewers.{ IDoubleClickListener, DoubleClickEvent }
import scala.tools.eclipse.properties.ScalaSyntaxClasses._
import org.eclipse.jface.layout.PixelConverter
import org.eclipse.jdt.internal.ui.preferences.PreferencesMessages
import scala.tools.eclipse.ScalaPlugin
import org.eclipse.jdt.ui.text.IColorManager
import org.eclipse.jface.preference.ColorSelector
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.preference.PreferenceConverter
import org.eclipse.jface.preference.PreferencePage
import org.eclipse.jface.resource.JFaceResources
import org.eclipse.jface.text.Document
import org.eclipse.jface.text.IDocumentPartitioner
import org.eclipse.jface.text.TextUtilities
import org.eclipse.jface.viewers.ISelectionChangedListener
import org.eclipse.jface.viewers.IStructuredSelection
import org.eclipse.jface.viewers.LabelProvider
import org.eclipse.jface.viewers.SelectionChangedEvent
import org.eclipse.jface.viewers.StructuredSelection
import org.eclipse.jface.viewers.Viewer
import org.eclipse.jface.viewers.TreeViewer
import org.eclipse.swt.SWT
import org.eclipse.swt.events.SelectionAdapter
import org.eclipse.swt.events.SelectionEvent
import org.eclipse.swt.graphics.Font
import org.eclipse.swt.graphics.Point
import org.eclipse.swt.graphics.RGB
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.widgets.Button
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Control
import org.eclipse.swt.widgets.Label
import org.eclipse.swt.widgets.Link
import org.eclipse.swt.widgets.Scrollable
import org.eclipse.ui.IWorkbench
import org.eclipse.ui.IWorkbenchPreferencePage
import org.eclipse.ui.dialogs.PreferencesUtil
import org.eclipse.ui.editors.text.EditorsUI
import org.eclipse.ui.texteditor.ChainedPreferenceStore
import org.eclipse.jdt.internal.ui.preferences.ScrolledPageContent
import org.eclipse.jface.viewers.ITreeContentProvider
import org.eclipse.jdt.internal.ui.javaeditor.JavaSourceViewer
import org.eclipse.jdt.ui.PreferenceConstants
import scala.tools.eclipse.lexical.ScalaDocumentPartitioner
import scala.tools.eclipse.ScalaSourceViewerConfiguration
import org.eclipse.jdt.ui.text.IJavaPartitions
import org.eclipse.jface.util.IPropertyChangeListener
import org.eclipse.jface.util.PropertyChangeEvent

/**
 * @see org.eclipse.jdt.internal.ui.preferences.JavaEditorColoringConfigurationBlock
 */
class SyntaxColouringPreferencePage extends PreferencePage with IWorkbenchPreferencePage {
  import SyntaxColouringPreferencePage._

  setPreferenceStore(ScalaPlugin.plugin.getPreferenceStore)
  private val overlayStore = makeOverlayPreferenceStore

  private var colorEditorLabel: Label = _
  private var syntaxForegroundColorEditor: ColorSelector = _
  private var boldCheckBox: Button = _
  private var italicCheckBox: Button = _
  private var underlineCheckBox: Button = _
  private var strikethroughCheckBox: Button = _
  private var treeViewer: TreeViewer = _

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

  def makeOverlayPreferenceStore = {
    import OverlayPreferenceStore._
    val keys = ALL_SYNTAX_CLASSES.flatMap { syntaxClass =>
      List(
        new OverlayKey(STRING, syntaxClass.colourKey),
        new OverlayKey(BOOLEAN, syntaxClass.boldKey),
        new OverlayKey(BOOLEAN, syntaxClass.italicKey),
        new OverlayKey(BOOLEAN, syntaxClass.strikethroughKey),
        new OverlayKey(BOOLEAN, syntaxClass.underlineKey))
    }.toArray
    new OverlayPreferenceStore(getPreferenceStore, keys)
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
  }

  object TreeContentAndLabelProvider extends LabelProvider with ITreeContentProvider {

    case class Category(name: String, children: List[ScalaSyntaxClass])

    val scalaCategory = Category("Scala", List(BRACKET, KEYWORD, RETURN, MULTI_LINE_STRING, OPERATOR, DEFAULT, STRING))

    val commentsCategory = Category("Comments", List(SINGLE_LINE_COMMENT, MULTI_LINE_COMMENT, SCALADOC))

    val xmlCategory = Category("XML", List(XML_ATTRIBUTE_EQUALS, XML_ATTRIBUTE_NAME, XML_ATTRIBUTE_VALUE,
      XML_CDATA_BORDER, XML_COMMENT, XML_TAG_DELIMITER, XML_TAG_NAME, XML_PI))

    val categories = List(scalaCategory, commentsCategory, xmlCategory)

    def getElements(inputElement: AnyRef) = categories.toArray

    def getChildren(parentElement: AnyRef) = parentElement match {
      case Category(_, children) => children.toArray
      case _ => Array()
    }

    def getParent(element: AnyRef): Category = {
      for {
        category <- categories
        child <- category.children
        if (child == element)
      } return category
      null
    }

    def hasChildren(element: AnyRef) = getChildren(element).nonEmpty

    def inputChanged(viewer: Viewer, oldInput: AnyRef, newInput: AnyRef) {}

    override def getText(element: AnyRef) = element match {
      case Category(name, _) => name
      case ScalaSyntaxClass(displayName, _) => displayName
    }
  }

  def createTreeViewer(editorComposite: Composite) {
    treeViewer = new TreeViewer(editorComposite, SWT.SINGLE | SWT.BORDER)

    treeViewer.setContentProvider(TreeContentAndLabelProvider)
    treeViewer.setLabelProvider(TreeContentAndLabelProvider)

    // scrollbars and tree indentation guess
    val widthHint = ALL_SYNTAX_CLASSES.map { syntaxClass => convertWidthInCharsToPixels(syntaxClass.displayName.length) }.max +
      Option(treeViewer.getControl.asInstanceOf[Scrollable].getVerticalBar).map { _.getSize.x * 3 }.getOrElse(0)

    treeViewer.getControl.setLayoutData(gridData(
      horizontalAlignment = SWT.BEGINNING,
      verticalAlignment = SWT.BEGINNING,
      grabExcessHorizontalSpace = false,
      grabExcessVerticalSpace = true,
      widthHint = widthHint,
      heightHint = convertHeightInCharsToPixels(9)))

    treeViewer.addDoubleClickListener(new IDoubleClickListener {
      def doubleClick(event: DoubleClickEvent) {
        val element = event.getSelection.asInstanceOf[IStructuredSelection].getFirstElement
        if (treeViewer.isExpandable(element))
          treeViewer.setExpandedState(element, !treeViewer.getExpandedState(element))
      }
    })

    treeViewer.addSelectionChangedListener(new ISelectionChangedListener {
      def selectionChanged(event: SelectionChangedEvent) {
        handleSyntaxColorListSelection()
      }
    })

    treeViewer.setInput(new Object)
  }

  private implicit def fnToSelectionAdapter[T](p: SelectionEvent => T): SelectionAdapter =
    new SelectionAdapter() {
      override def widgetSelected(e: SelectionEvent) = p(e)
    }

  private def gridLayout(marginHeight: Int = 5, marginWidth: Int = 5, numColumns: Int = 1): GridLayout = {
    val layout = new GridLayout
    layout.marginHeight = marginHeight
    layout.marginWidth = marginWidth
    layout.numColumns = numColumns
    layout
  }

  private def gridData(
    horizontalAlignment: Int = SWT.BEGINNING,
    verticalAlignment: Int = SWT.CENTER, //
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

    var elementLabel = new Label(outerComposite, SWT.LEFT)
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

    colorEditorLabel = new Label(stylesComposite, SWT.LEFT)
    colorEditorLabel.setText(PreferencesMessages.JavaEditorPreferencePage_color)

    colorEditorLabel.setLayoutData(gridData(horizontalAlignment = GridData.BEGINNING, horizontalIndent = 20))

    syntaxForegroundColorEditor = new ColorSelector(stylesComposite)
    val foregroundColorButton = syntaxForegroundColorEditor.getButton
    foregroundColorButton.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING))

    boldCheckBox = new Button(stylesComposite, SWT.CHECK)
    boldCheckBox.setText(PreferencesMessages.JavaEditorPreferencePage_bold)

    boldCheckBox.setLayoutData(gridData(
      horizontalAlignment = GridData.BEGINNING, horizontalIndent = 20, horizontalSpan = 2))

    italicCheckBox = new Button(stylesComposite, SWT.CHECK)
    italicCheckBox.setText(PreferencesMessages.JavaEditorPreferencePage_italic)
    italicCheckBox.setLayoutData(gridData(
      horizontalAlignment = GridData.BEGINNING, horizontalIndent = 20, horizontalSpan = 2))

    strikethroughCheckBox = new Button(stylesComposite, SWT.CHECK)
    strikethroughCheckBox.setText(PreferencesMessages.JavaEditorPreferencePage_strikethrough)
    strikethroughCheckBox.setLayoutData(gridData(
      horizontalAlignment = GridData.BEGINNING, horizontalIndent = 20, horizontalSpan = 2))

    underlineCheckBox = new Button(stylesComposite, SWT.CHECK)
    underlineCheckBox.setText(PreferencesMessages.JavaEditorPreferencePage_underline)
    underlineCheckBox.setLayoutData(
      gridData(horizontalAlignment = GridData.BEGINNING, horizontalIndent = 20, horizontalSpan = 2))

    val previewLabel = new Label(outerComposite, SWT.LEFT)
    previewLabel.setText(PreferencesMessages.JavaEditorPreferencePage_preview)
    previewLabel.setLayoutData(new GridData(GridData.FILL_HORIZONTAL))

    val previewer = createPreviewer(outerComposite)
    previewer.setLayoutData(gridData(
      horizontalAlignment = GridData.FILL,
      verticalAlignment = GridData.FILL,
      grabExcessHorizontalSpace = true,
      grabExcessVerticalSpace = true,
      widthHint = convertWidthInCharsToPixels(20),
      heightHint = convertHeightInCharsToPixels(5)))

    foregroundColorButton.addSelectionListener { e: SelectionEvent =>
      for (syntaxClass <- selectedSyntaxClass)
        PreferenceConverter.setValue(overlayStore, syntaxClass.colourKey, syntaxForegroundColorEditor.getColorValue)
    }
    boldCheckBox.addSelectionListener { e: SelectionEvent =>
      for (syntaxClass <- selectedSyntaxClass)
        overlayStore.setValue(syntaxClass.boldKey, boldCheckBox.getSelection)
    }
    italicCheckBox.addSelectionListener { e: SelectionEvent =>
      for (syntaxClass <- selectedSyntaxClass)
        overlayStore.setValue(syntaxClass.italicKey, italicCheckBox.getSelection)
    }
    underlineCheckBox.addSelectionListener { e: SelectionEvent =>
      for (syntaxClass <- selectedSyntaxClass)
        overlayStore.setValue(syntaxClass.underlineKey, underlineCheckBox.getSelection)
    }
    strikethroughCheckBox.addSelectionListener { e: SelectionEvent =>
      for (syntaxClass <- selectedSyntaxClass)
        overlayStore.setValue(syntaxClass.strikethroughKey, strikethroughCheckBox.getSelection)
    }

    treeViewer.setSelection(new StructuredSelection(TreeContentAndLabelProvider.scalaCategory))

    outerComposite.layout(false)
    outerComposite
  }

  private def createPreviewer(parent: Composite): Control = {
    val store = new ChainedPreferenceStore(Array(overlayStore, EditorsUI.getPreferenceStore))

    val previewViewer = new JavaSourceViewer(parent, null, null, false, SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER, store)
    val font = JFaceResources.getFont(PreferenceConstants.EDITOR_TEXT_FONT)
    previewViewer.getTextWidget.setFont(font)
    previewViewer.setEditable(false)

    val configuration = new ScalaSourceViewerConfiguration(store, store, null)
    previewViewer.configure(configuration)

    store.addPropertyChangeListener(new IPropertyChangeListener() {
      def propertyChange(event: PropertyChangeEvent) {
        if (configuration.affectsTextPresentation(event))
          configuration.handlePropertyChangeEvent(event)
        previewViewer.invalidateTextPresentation()
      }
    })

    val document = new Document(previewText)
    val partitioners = new HashMap[String, IDocumentPartitioner]
    partitioners.put(IJavaPartitions.JAVA_PARTITIONING, new ScalaDocumentPartitioner)
    TextUtilities.addDocumentPartitioners(document, partitioners)
    previewViewer.setDocument(document)

    previewViewer.getControl
  }

  private def selectedSyntaxClass: Option[ScalaSyntaxClass] =
    condOpt(treeViewer.getSelection.asInstanceOf[IStructuredSelection].getFirstElement) {
      case syntaxClass: ScalaSyntaxClass => syntaxClass
    }

  private def massSetEnablement(enabled: Boolean) =
    List(syntaxForegroundColorEditor.getButton, colorEditorLabel, boldCheckBox, italicCheckBox,
      strikethroughCheckBox, underlineCheckBox) foreach { _.setEnabled(enabled) }

  private def handleSyntaxColorListSelection() = selectedSyntaxClass match {
    case None =>
      massSetEnablement(false)
    case Some(syntaxClass) =>
      val rgb = PreferenceConverter.getColor(overlayStore, syntaxClass.colourKey)
      syntaxForegroundColorEditor.setColorValue(rgb)
      boldCheckBox.setSelection(overlayStore.getBoolean(syntaxClass.boldKey))
      italicCheckBox.setSelection(overlayStore.getBoolean(syntaxClass.italicKey))
      strikethroughCheckBox.setSelection(overlayStore.getBoolean(syntaxClass.strikethroughKey))
      underlineCheckBox.setSelection(overlayStore.getBoolean(syntaxClass.underlineKey))
      massSetEnablement(true)
  }

}

object SyntaxColouringPreferencePage {

  val previewText = """/** Scaladoc */
class ClassName {
  def method(arg: String): Int = {
    // Single-line comment
    /* Multi-line comment */
    val s = "foo" + """ + "\"\"\"" + "multiline string" + "\"\"\"" + """
    val xml =
      <tag attributeName="value">
        <!-- XML comment -->
        <?processinginstruction?>
        <![CDATA[ CDATA ]]>
        PCDATA
      </tag>
    return 42
  }
}"""

}
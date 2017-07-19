package org.scalaide.ui.internal.preferences

import java.io.IOException
import java.net.URL
import net.miginfocom.layout._
import net.miginfocom.swt.MigLayout
import org.eclipse.core.runtime.preferences.InstanceScope
import org.eclipse.jdt.internal.ui.preferences.OverlayPreferenceStore
import org.eclipse.jface.dialogs.MessageDialog
import org.eclipse.jface.text._
import org.eclipse.jface.util._
import org.eclipse.swt.SWT
import org.eclipse.swt.events._
import org.eclipse.swt.widgets._
import org.eclipse.ui._
import org.scalaide.core.IScalaPlugin
import org.scalaide.core.internal.formatter.FormatterPreferences
import org.scalaide.core.internal.formatter.FormatterPreferences._
import scalariform.formatter._
import scalariform.formatter.preferences._
import org.scalaide.logging.HasLogger
import org.scalaide.core.SdtConstants

class FormatterPreferencePage extends FieldEditors with HasLogger {
  import FormatterPreferencePage._

  lazy val overlayStore = {
    import OverlayPreferenceStore._
    val keys =
      for (preference <- AllPreferences.preferences)
        yield preference.preferenceType match {
        case BooleanPreference => new OverlayKey(BOOLEAN, preference.eclipseKey)
        case IntegerPreference(_, _) => new OverlayKey(INT, preference.eclipseKey)
        case IntentPreference => new OverlayKey(STRING, preference.eclipseKey)
      }
    val overlayStore = new OverlayPreferenceStore(getPreferenceStore, keys.toArray)
    overlayStore.load()
    overlayStore.start()
    overlayStore
  }

  abstract class PrefTab(tabName: String, previewText: String) {

    protected var previewDocument: IDocument = _

    def build(tabFolder: TabFolder): Unit = {
      val tabItem = new TabItem(tabFolder, SWT.NONE)
      tabItem.setText(tabName)
      val tabComposite = new Composite(tabFolder, SWT.NONE)
      tabItem.setControl(tabComposite)
      buildContents(tabComposite)
    }

    protected def buildContents(composite: Composite): Unit

    private def formatPreviewText: String = ScalaFormatter.format(previewText, getPreferences(overlayStore))

    protected def addCheckBox(parent: Composite, text: String, preference: BooleanPreferenceDescriptor): Unit = {
      handleCheckBox(
        parent, text, preference,
        initialStateSelected = overlayStore(preference),
        storePreference = btn => overlayStore(preference) = btn.getSelection,
        setSelected = btn => btn.setSelection(overlayStore(preference))
      )
    }

    protected def addCheckBox(parent: Composite, text: String, preference: IntentPreferenceDescriptor): Unit = {
      handleCheckBox(
        parent, text, preference,
        initialStateSelected = overlayStore(preference) == Force.toString,
        storePreference = btn => overlayStore(preference) = if (btn.getSelection) Force else Prevent,
        setSelected = btn => btn.setSelection(overlayStore(preference) == Force.toString)
      )
    }

    private def handleCheckBox[T <: PreferenceDescriptor[_]](
      parent: Composite,
      text: String,
      preference: T,
      initialStateSelected: Boolean,
      storePreference: Button => Unit,
      setSelected: Button => Unit): Unit = {

      import org.scalaide.util.eclipse.SWTUtils.fnToSelectionAdapter

      val checkBox = new Button(parent, SWT.CHECK | SWT.WRAP)
      checkBox.setText(text)
      checkBox.setToolTipText(preference.description + " (" + preference.key + ")")
      checkBox.setSelection(initialStateSelected)
      checkBox.setLayoutData(new CC().spanX(2).growX.wrap)

      checkBox.addSelectionListener { e: SelectionEvent =>
        storePreference(checkBox)
        previewDocument.set(formatPreviewText)
      }

      overlayStore.addPropertyChangeListener { e: PropertyChangeEvent =>
        if (e.getProperty.equals(preference.eclipseKey)) setSelected(checkBox)
      }

      allEnableDisableControls += checkBox
    }

    protected def addNumericField(parent: Composite, text: String, preference: PreferenceDescriptor[Int]): Unit = {
      import org.scalaide.util.eclipse.SWTUtils.RichControl

      val IntegerPreference(min, max) = preference.preferenceType
      val label = new Label(parent, SWT.LEFT)
      label.setText(text)
      label.setToolTipText(preference.description + " (" + preference.key + ")")
      label.setLayoutData(new CC())
      val field = new Text(parent, SWT.SINGLE | SWT.BORDER)
      field.setText(overlayStore(preference).toString)
      field.setLayoutData(new CC().sizeGroupX("numfield").alignX("right").minWidth("40px").wrap)

      def validateNumber(s: String) =
        try Integer.parseInt(s) match {
          case n if n < min || n > max => None
          case n => Some(n)
        } catch {
          case _: NumberFormatException => None
        }

      def valueChanged(): Unit = {
        validateNumber(field.getText) match {
          case Some(n) =>
            overlayStore(preference) = n
            previewDocument.set(formatPreviewText)
            setErrorMessage(null)
          case None =>
            setErrorMessage("Number must be an integer between " + min + " and " + max)
        }
      }

      field.onKeyReleased { valueChanged() }
      field.onFocusLost { valueChanged() }
      overlayStore.addPropertyChangeListener { e: PropertyChangeEvent =>
        if (e.getProperty == preference.eclipseKey)
          field.setText(overlayStore(preference).toString)
      }

      allEnableDisableControls ++= Set(label, field)
    }

    protected def addPreview(parent: Composite): Unit = {
      val previewLabel = new Label(parent, SWT.LEFT)
      allEnableDisableControls += previewLabel
      previewLabel.setText("Preview:")
      previewLabel.setLayoutData(new CC().spanX(2).wrap)
      val previewer = createPreviewer(parent)
      previewer.setLayoutData(new CC().spanX(2).grow)
    }

    protected def createPreviewer(parent: Composite): Control = {
      val previewer = new PreviewerFactory(ScalaPreviewerFactoryConfiguration).createPreviewer(parent, getPreferenceStore, formatPreviewText)
      previewDocument = previewer.getDocument
      val control = previewer.getControl
      allEnableDisableControls += control
      control
    }
  }

  object AlignmentPrefTab extends PrefTab("Alignment", AlignPreviewText) {

    def buildContents(composite: Composite): Unit = {
      composite.setLayout(new MigLayout(new LC().fill, new AC, new AC().index(5).grow(1)))

      addCheckBox(composite, AlignArguments.description, AlignArguments)
      addCheckBox(composite, AlignParameters.description, AlignParameters)
      addCheckBox(composite, AlignSingleLineCaseStatements.description, AlignSingleLineCaseStatements)
      addNumericField(
        composite,
        AlignSingleLineCaseStatements.MaxArrowIndent.description,
        AlignSingleLineCaseStatements.MaxArrowIndent
      )

      addPreview(composite)
    }
  }

  object IndentPrefTab extends PrefTab("Indentation", IndentPreviewText) {

    def buildContents(composite: Composite): Unit = {
      composite.setLayout(new MigLayout(new LC().fill, new AC, new AC().index(10).grow(1)))

      addNumericField(composite, IndentSpaces.description, IndentSpaces)
      addCheckBox(composite, IndentWithTabs.description, IndentWithTabs)
      addCheckBox(composite, DoubleIndentConstructorArguments.description, DoubleIndentConstructorArguments)
      addCheckBox(composite, DoubleIndentMethodDeclaration.description, DoubleIndentMethodDeclaration)
      addCheckBox(composite, DanglingCloseParenthesis.description, DanglingCloseParenthesis)
      addCheckBox(composite, FirstParameterOnNewline.description, FirstParameterOnNewline)
      addCheckBox(composite, FirstArgumentOnNewline.description, FirstArgumentOnNewline)
      addCheckBox(composite, IndentLocalDefs.description, IndentLocalDefs)
      addCheckBox(composite, IndentPackageBlocks.description, IndentPackageBlocks)

      addPreview(composite)
    }
  }

  object SpacesPrefTab extends PrefTab("Spaces", SpacesPreviewText) {

    def buildContents(composite: Composite): Unit = {
      composite.setLayout(new MigLayout(new LC().fill, new AC, new AC().index(9).grow(1)))

      addCheckBox(composite, CompactStringConcatenation.description, CompactStringConcatenation)
      addCheckBox(composite, PreserveSpaceBeforeArguments.description, PreserveSpaceBeforeArguments)
      addCheckBox(composite, SpaceBeforeColon.description, SpaceBeforeColon)
      addCheckBox(composite, SpaceBeforeContextColon.description, SpaceBeforeContextColon)
      addCheckBox(composite, SpaceInsideBrackets.description, SpaceInsideBrackets)
      addCheckBox(composite, SpaceInsideParentheses.description, SpaceInsideParentheses)
      addCheckBox(composite, SpacesAroundMultiImports.description, SpacesAroundMultiImports)
      addCheckBox(composite, SpacesWithinPatternBinders.description, SpacesWithinPatternBinders)

      addPreview(composite)
    }
  }

  object MiscPrefTab extends PrefTab("Miscellaneous", MiscPreviewText) {

    def buildContents(composite: Composite): Unit = {
      composite.setLayout(new MigLayout(new LC().fill, new AC, new AC().index(5).grow(1)))

      addCheckBox(composite, CompactControlReadability.description, CompactControlReadability)
      addCheckBox(composite, FormatXml.description, FormatXml)
      addCheckBox(composite, NewlineAtEndOfFile.description, NewlineAtEndOfFile)
      addCheckBox(composite, RewriteArrowSymbols.description, RewriteArrowSymbols)

      addPreview(composite)
    }

  }

  object ScaladocPrefTab extends PrefTab("Scaladoc", ScaladocPreviewText) {

    def buildContents(composite: Composite): Unit = {
      composite.setLayout(new MigLayout(new LC().fill, new AC, new AC().index(3).grow(1)))

      addCheckBox(composite, MultilineScaladocCommentsStartOnFirstLine.description, MultilineScaladocCommentsStartOnFirstLine)
      addCheckBox(composite, PlaceScaladocAsterisksBeneathSecondAsterisk.description, PlaceScaladocAsterisksBeneathSecondAsterisk)

      addPreview(composite)
    }

  }
  override def createContents(parent: Composite): Control = {
    initUnderlyingPreferenceStore(SdtConstants.PluginId, IScalaPlugin().getPreferenceStore)
    mkMainControl(parent)(createEditors)
  }

  def createEditors(control: Composite): Unit = {
    import org.scalaide.util.eclipse.SWTUtils.fnToSelectionAdapter
    import org.scalaide.util.eclipse.SWTUtils.noArgFnToSelectionAdapter

    { // Manual link + import / export buttons
      val buttonPanel = new Composite(control, SWT.NONE)

      buttonPanel.setLayout(
        new MigLayout(
          new LC().insetsAll("0"),
          new AC()
            .index(0).grow.align("left")
            .index(1).grow(0).align("right")
            .index(2).grow(0).align("right")))

      val link = new Link(buttonPanel, SWT.NONE)
      link.setText("<a>Scalariform manual</a>")
      link.addSelectionListener { e: SelectionEvent =>
        val url = new URL(ScalariformDocUrl)
        PlatformUI.getWorkbench.getBrowserSupport.createBrowser(null).openURL(url)
      }
      link.setLayoutData(new CC)

      val importButton = new Button(buttonPanel, SWT.PUSH)
      importButton.setText("Import...")
      importButton.setLayoutData(new CC().sizeGroupX("button"))
      importButton.addSelectionListener { () => importPreferences() }

      val exportButton = new Button(buttonPanel, SWT.PUSH)
      exportButton.setText("Export...")
      exportButton.setLayoutData(new CC().sizeGroupX("button").wrap)
      exportButton.addSelectionListener { () => exportPreferences() }

      buttonPanel.setLayoutData(new CC().spanX(2).growX.wrap)
      allEnableDisableControls ++= Set(link, importButton, exportButton)
    }

    val tabFolder = new TabFolder(control, SWT.TOP)
    tabFolder.setLayoutData(new CC().spanX(2).grow)

    AlignmentPrefTab.build(tabFolder)
    IndentPrefTab.build(tabFolder)
    SpacesPrefTab.build(tabFolder)
    ScaladocPrefTab.build(tabFolder)
    MiscPrefTab.build(tabFolder)

    allEnableDisableControls += tabFolder
  }

  override def useProjectSpecifcSettingsKey = USE_PROJECT_SPECIFIC_SETTINGS_KEY

  override def pageId = PageId

  override def performOk() = {
    super.performOk()
    overlayStore.propagate()
    InstanceScope.INSTANCE.getNode(SdtConstants.PluginId).flush()
    true
  }

  override def dispose(): Unit = {
    overlayStore.stop()
    super.dispose()
  }

  override def performDefaults(): Unit = {
    overlayStore.loadDefaults()
    super.performDefaults()
  }

  private def getPreferenceFileNameViaDialog(title: String, initialFileName: String = ""): Option[String] = {
    val dialog = new FileDialog(getShell, SWT.SAVE)
    dialog.setText(title)
    dialog.setFileName(initialFileName)
    val dialogSettings = IScalaPlugin().getDialogSettings
    Option(dialogSettings get ImportExportDialogPath) foreach dialog.setFilterPath
    val fileName = dialog.open()
    if (fileName == null)
      None
    else {
      dialogSettings.put(ImportExportDialogPath, dialog.getFilterPath)
      Some(fileName)
    }
  }

  private def exportPreferences(): Unit = {
    for (fileName <- getPreferenceFileNameViaDialog("Export formatter preferences", DefaultPreferenceFileName)) {
      val preferences = FormatterPreferences.getPreferences(overlayStore)
      try
        PreferencesImporterExporter.savePreferences(fileName, preferences)
      catch {
        case e: IOException =>
          eclipseLog.error(e)
          MessageDialog.openError(getShell, "Error writing to " + fileName, e.getMessage)
      }
    }
  }

  private def importPreferences(): Unit = {
    for (fileName <- getPreferenceFileNameViaDialog("Import formatter preferences")) {
      val preferences = try
        PreferencesImporterExporter.loadPreferences(fileName)
      catch {
        case e: IOException =>
          eclipseLog.error(e)
          MessageDialog.openError(getShell, "Error opening " + fileName, e.getMessage)
          return
      }
      overlayStore.importPreferences(preferences)
    }
  }

}

object FormatterPreferencePage {

  val DefaultPreferenceFileName = "formatterPreferences.properties"

  val PageId = "scala.tools.eclipse.formatter.FormatterPreferencePage"

  val ImportExportDialogPath = "formatter.importExportDialogPath"

  val ScalariformDocUrl = "https://github.com/scala-ide/scalariform#preferences"

  val SpacesPreviewText = """import a.{b, c, d}

class ClassName[T: List](name: String) {

  println("hello"+name+"world")

  stack.pop() should equal (2)

  x match {
    case elem@Multi(values@_*) =>
  }

}
"""

  val AlignPreviewText = """
object Foo {
  def method(string: String,
int: Int) = {
    s match {
      case "wibble" => 42
      case "foo" => 123
      case _ => 100
    }
  }
  method(
    string = "hello",
    int = 1
  )
}
"""

  val IndentPreviewText = """package foo {
class Bar(param1: Int,
param2: String)
extends Foo with Baz {

  def method(string: String,
int: Int) = {
    def localDef: Unit = {
      // ..
    }
  }
  method(string,
    int)
}
}"""

  val MiscPreviewText = """val xml = <foo>
<bar/>
 <baz  attr= "value" />
</foo>
for (n <- 1 to 10)
 n match {
  case _ => 42
}
val book = Book(
  name = "Name",
  author = "Author",
  rating = 5
)
if (condition) {
  // do something
}
else if (condition2) {
  // do something else
}
else {
  // last ditch
}
"""

  val ScaladocPreviewText = """/**
 * Multiline Scaladoc
 * comment
 */
 class A
"""

}

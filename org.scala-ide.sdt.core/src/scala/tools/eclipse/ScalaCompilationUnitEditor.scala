package scala.tools.eclipse

import scala.tools.eclipse.properties.syntaxcolouring.ScalaSyntaxClasses
import scala.tools.eclipse.semantichighlighting.Presenter
import scala.tools.eclipse.semantichighlighting.TextPresentationHighlighter
import scala.tools.eclipse.ui.DisplayThread
import scala.tools.eclipse.util.SWTUtils.fnToPropertyChangeListener

import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor
import org.eclipse.jdt.internal.ui.javaeditor.JavaSourceViewer
import org.eclipse.jface.text.source.SourceViewerConfiguration
import org.eclipse.jface.util.IPropertyChangeListener
import org.eclipse.jface.util.PropertyChangeEvent
import org.eclipse.swt.widgets.Composite

/** Trait containing common logic used by both the `ScalaSourceFileEditor` and `ScalaClassFileEditor`.*/
trait ScalaCompilationUnitEditor extends JavaEditor with ScalaEditor {
  /**@note Current implementation assumes that all accesses to this member should be confined to the UI Thread */
  private var semanticHighlightingPresenter: semantichighlighting.Presenter = _
  protected def semanticHighlightingPreferences = semantichighlighting.Preferences(scalaPrefStore)

  private val preferenceListener: IPropertyChangeListener = handlePreferenceStoreChanged _

  scalaPrefStore.addPropertyChangeListener(preferenceListener)

  protected def scalaPrefStore = ScalaPlugin.prefStore
  def javaPrefStore = super.getPreferenceStore

  override def setSourceViewerConfiguration(configuration: SourceViewerConfiguration) {
    super.setSourceViewerConfiguration(
      configuration match {
        case svc: ScalaSourceViewerConfiguration => svc
        case _ => new ScalaSourceViewerConfiguration(javaPrefStore, scalaPrefStore, this)
      })
  }

  override def createPartControl(parent: Composite) {
    super.createPartControl(parent)

    if (isScalaSemanticHighlightingEnabled)
      installScalaSemanticHighlighting(forceSemanticHighlightingOnInstallment)
  }

  /** Should semantic highlighting be triggered at initialization. */
  def forceSemanticHighlightingOnInstallment: Boolean

  protected def installScalaSemanticHighlighting(forceRefresh: Boolean): Unit = {
    if (semanticHighlightingPresenter == null) {
      val presentationHighlighter = createSemanticHighlighter
      semanticHighlightingPresenter = new Presenter(ScalaCompilationUnitEditor.this, presentationHighlighter, semanticHighlightingPreferences, DisplayThread)
      semanticHighlightingPresenter.initialize(forceRefresh)
    }
  }

  def createSemanticHighlighter: TextPresentationHighlighter

  protected def uninstallScalaSemanticHighlighting(removesHighlights: Boolean): Unit = {
    if (semanticHighlightingPresenter != null) {
      semanticHighlightingPresenter.dispose(removesHighlights)
      semanticHighlightingPresenter = null
    }
  }

  def sourceViewer: JavaSourceViewer = super.getSourceViewer.asInstanceOf[JavaSourceViewer]

  override protected final def installSemanticHighlighting(): Unit = { /* Never install the Java semantic highlighting engine on a Scala Editor*/ }

  private def isScalaSemanticHighlightingEnabled: Boolean = semanticHighlightingPreferences.isEnabled

  override def dispose() {
    super.dispose()
    scalaPrefStore.removePropertyChangeListener(preferenceListener)
    uninstallScalaSemanticHighlighting(removesHighlights = false)
  }

  override protected def handlePreferenceStoreChanged(event: PropertyChangeEvent) = {
    event.getProperty match {
      case ScalaSyntaxClasses.ENABLE_SEMANTIC_HIGHLIGHTING =>
        // This preference can be changed only via the preference dialog, hence the below block
        // is ensured to be always run within the UI Thread. Check the JavaDoc of `handlePreferenceStoreChanged`
        if (isScalaSemanticHighlightingEnabled) installScalaSemanticHighlighting(forceRefresh = true)
        else uninstallScalaSemanticHighlighting(removesHighlights = true)

      case _ =>
        super.handlePreferenceStoreChanged(event)
    }
  }

  override final def createJavaSourceViewerConfiguration: ScalaSourceViewerConfiguration =
    new ScalaSourceViewerConfiguration(javaPrefStore, scalaPrefStore, this)

  override final def getInteractiveCompilationUnit(): InteractiveCompilationUnit = {
    // getInputJavaElement always returns the right value
    super.getInputJavaElement().asInstanceOf[InteractiveCompilationUnit]
  }
}
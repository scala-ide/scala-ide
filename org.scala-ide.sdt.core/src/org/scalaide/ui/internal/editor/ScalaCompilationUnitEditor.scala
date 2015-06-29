package org.scalaide.ui.internal.editor

import org.eclipse.jdt.internal.ui.javaeditor.IJavaEditorActionConstants
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor
import org.eclipse.jdt.internal.ui.javaeditor.JavaSourceViewer
import org.eclipse.jface.text.source.SourceViewerConfiguration
import org.eclipse.jface.util.IPropertyChangeListener
import org.eclipse.jface.util.PropertyChangeEvent
import org.eclipse.swt.widgets.Composite
import org.scalaide.core.IScalaPlugin
import org.scalaide.core.compiler.InteractiveCompilationUnit
import org.scalaide.ui.internal.actions.ScalaCopyQualifiedNameAction
import org.scalaide.ui.internal.editor.decorators.indentguide.IndentGuidePainter
import org.scalaide.ui.internal.editor.decorators.semantichighlighting
import org.scalaide.ui.internal.editor.decorators.semicolon.InferredSemicolonPainter
import org.scalaide.ui.syntax.ScalaSyntaxClasses
import org.scalaide.util.eclipse.SWTUtils.fnToPropertyChangeListener
import org.scalaide.util.ui.DisplayThread

/** Trait containing common logic used by both the `ScalaSourceFileEditor` and `ScalaClassFileEditor`.*/
trait ScalaCompilationUnitEditor extends JavaEditor with ScalaEditor {
  /**@note Current implementation assumes that all accesses to this member should be confined to the UI Thread */
  private var semanticHighlightingPresenter: semantichighlighting.Presenter = _
  protected def semanticHighlightingPreferences = semantichighlighting.Preferences(scalaPrefStore)

  private val preferenceListener: IPropertyChangeListener = handlePreferenceStoreChanged _

  scalaPrefStore.addPropertyChangeListener(preferenceListener)

  protected def scalaPrefStore = IScalaPlugin().getPreferenceStore()
  def javaPrefStore = super.getPreferenceStore

  override def setSourceViewerConfiguration(configuration: SourceViewerConfiguration): Unit = {
    super.setSourceViewerConfiguration(
      configuration match {
        case svc: ScalaSourceViewerConfiguration => svc
        case _ => new ScalaSourceViewerConfiguration(javaPrefStore, scalaPrefStore, this)
      })
  }

  protected override def createActions(): Unit = {
    super.createActions()
    installScalaAwareCopyQualifiedNameAction()
  }

  private def installScalaAwareCopyQualifiedNameAction(): Unit = {
    setAction(IJavaEditorActionConstants.COPY_QUALIFIED_NAME, new ScalaCopyQualifiedNameAction(this))
  }

  override def createPartControl(parent: Composite): Unit = {
    super.createPartControl(parent)

    val sv = sourceViewer
    val painter = Seq(new IndentGuidePainter(sv), new InferredSemicolonPainter(sv))
    painter foreach sv.addPainter

    if (isScalaSemanticHighlightingEnabled)
      installScalaSemanticHighlighting(forceSemanticHighlightingOnInstallment)
  }

  /** Should semantic highlighting be triggered at initialization. */
  def forceSemanticHighlightingOnInstallment: Boolean

  protected def installScalaSemanticHighlighting(forceRefresh: Boolean): Unit = {
    if (semanticHighlightingPresenter == null) {
      val presentationHighlighter = createSemanticHighlighter
      semanticHighlightingPresenter = new semantichighlighting.Presenter(ScalaCompilationUnitEditor.this, presentationHighlighter, semanticHighlightingPreferences, DisplayThread)
      semanticHighlightingPresenter.initialize(forceRefresh)
    }
  }

  def createSemanticHighlighter: semantichighlighting.TextPresentationHighlighter

  protected def uninstallScalaSemanticHighlighting(removesHighlights: Boolean): Unit = {
    if (semanticHighlightingPresenter != null) {
      semanticHighlightingPresenter.dispose(removesHighlights)
      semanticHighlightingPresenter = null
    }
  }

  def sourceViewer: JavaSourceViewer = super.getSourceViewer.asInstanceOf[JavaSourceViewer]

  override protected final def installSemanticHighlighting(): Unit = { /* Never install the Java semantic highlighting engine on a Scala Editor*/ }

  private def isScalaSemanticHighlightingEnabled: Boolean = semanticHighlightingPreferences.isEnabled

  override def dispose(): Unit = {
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
    IScalaPlugin().scalaCompilationUnit(getEditorInput()).orNull
  }
}

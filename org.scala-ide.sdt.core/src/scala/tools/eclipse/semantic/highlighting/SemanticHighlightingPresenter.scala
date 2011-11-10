/**
 *
 */
package scala.tools.eclipse
package semantic.highlighting

import java.util.Collections

import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jdt.core.WorkingCopyOwner
import org.eclipse.jdt.internal.ui.JavaPlugin
import org.eclipse.jface.preference.{PreferenceConverter, IPreferenceStore}
import org.eclipse.jface.text.source.{ISourceViewer, IAnnotationAccess, AnnotationPainter, Annotation}
import org.eclipse.jface.text.{Position, IPainter}
import org.eclipse.jface.util.{PropertyChangeEvent, IPropertyChangeListener}
import org.eclipse.swt.SWT
import org.eclipse.ui.part.FileEditorInput
import org.eclipse.ui.{PlatformUI, IWorkbenchPart, IPartListener}

import scala.collection.mutable.SynchronizedMap
import scala.collection.mutable.HashMap
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.tools.eclipse.properties.ImplicitsPreferencePage.{P_ITALIC, P_BOLD, P_ACTIVE}
import scala.tools.eclipse.util.HasLogger
import scala.tools.eclipse.{ScalaSourceFileEditor, ScalaPresentationCompiler}
import scala.tools.nsc.util.SourceFile

import reconciliation.ReconciliationParticipant

/**
 * This class is instantiated by the reconciliationParticipants extension point and
 * simply forwards to the SemanticHighlightingReconciliation  object.
 */
class SemanticHighlightingReconciliationParticipant extends ReconciliationParticipant {

  override def afterReconciliation(scu: ScalaCompilationUnit, monitor: IProgressMonitor, workingCopyOwner: WorkingCopyOwner) {
    if (!ScalaPlugin.plugin.headlessMode) {
      SemanticHighlightingReconciliation.afterReconciliation(scu, monitor, workingCopyOwner)
    }
  }
}

/**
 * Manages the SemanticHighlightingPresenter instances for the open editors.
 *
 * Each ScalaCompilationUnit has one associated SemanticHighlightingPresenter,
 * which is created the first time a reconciliation is performed for a
 * compilation unit. When the editor (respectively the IWorkbenchPart) is closed,
 * the SemanticHighlightingPresenter is removed.
 *
 * @author Mirko Stocker
 */
object SemanticHighlightingReconciliation {

  private val participants = new HashMap[ScalaCompilationUnit, SemanticHighlightingPresenter] 
    with SynchronizedMap[ScalaCompilationUnit, SemanticHighlightingPresenter] 

  /**
   *  A listener that removes a  SemanticHighlightingPresenter when the part is closed.
   */
  class UnregisteringPartListener(scu: ScalaCompilationUnit) extends IPartListener {

    def partClosed(part: IWorkbenchPart) {
      participants.remove(scu)
    }

    def partActivated(part: IWorkbenchPart) {}
    def partBroughtToTop(part: IWorkbenchPart) {}
    def partDeactivated(part: IWorkbenchPart) {}
    def partOpened(part: IWorkbenchPart) {}
  }

  /**
   * Searches for the Editor that currently displays the compilation unit, then creates
   * an instance of SemanticHighlightingPresenter. A listener is registered at the editor
   * to remove the SemanticHighlightingPresenter when the editor is closed.
   */
  private def createSemanticHighlighterForEditor(scu: ScalaCompilationUnit) = {

    def getPagesWithEditors = {
      PlatformUI.getWorkbench.getWorkbenchWindows flatMap (_.getPages) flatMap { page =>
        page.getEditorReferences.toList map (_.getEditor(false)) collect {
          case editor: ScalaSourceFileEditor => (page, editor)
        }
      }
    }

    getPagesWithEditors flatMap {
      case (page, editor) =>
        Option(editor.getEditorInput) collect {
          case editorInput: FileEditorInput if editorInput.getPath equals scu.getResource.getLocation =>

            page.addPartListener(new UnregisteringPartListener(scu))

            new SemanticHighlightingPresenter(editorInput, editor.sourceViewer)
        }
    }
  }

  def afterReconciliation(scu: ScalaCompilationUnit, monitor: IProgressMonitor, workingCopyOwner: WorkingCopyOwner) {

    val firstTimeReconciliation = !participants.contains(scu)

    if (firstTimeReconciliation) {
      createSemanticHighlighterForEditor(scu) foreach (participants(scu) = _)
    }

    participants(scu).update(scu)
  }
}

/**
 * @author Jin Mingjian
 * @author David Bernard
 *
 */
class SemanticHighlightingPresenter(editor: FileEditorInput, sourceViewer: ISourceViewer) extends HasLogger {
  import scala.tools.eclipse.properties.ImplicitsPreferencePage._
  import SemanticHighlightingPresenter._

  val annotationAccess = new IAnnotationAccess() {
    def getType(annotation: Annotation) = annotation.getType();
    def isMultiLine(annotation: Annotation) = true
    def isTemporary(annotation: Annotation) = true
  }

  private def isFontStyleBold = pluginStore.getBoolean(P_BOLD) match {
    case true => SWT.BOLD
    case _ => SWT.NORMAL
  }

  private def isFontStyleItalic = pluginStore.getBoolean(P_ITALIC) match {
    case true => SWT.ITALIC
    case _ => SWT.NORMAL
  }

  private lazy val P_COLOR = {
    val lookup = new org.eclipse.ui.texteditor.AnnotationPreferenceLookup()
    val pref = lookup.getAnnotationPreference(AnnotationsTypes.Implicits)
    pref.getColorPreferenceKey()
  }

  def fColorValue = ColorManager.getDefault.getColor(PreferenceConverter.getColor(editorsStore, P_COLOR))

  val impTextStyleStrategy = new ImplicitConversionsOrArgsTextStyleStrategy(isFontStyleBold | isFontStyleItalic)

  val painter: AnnotationPainter = {
    val b = new AnnotationPainter(sourceViewer, annotationAccess)
    b.addAnnotationType(AnnotationsTypes.Implicits, AnnotationsTypes.Implicits)
    b.addTextStyleStrategy(AnnotationsTypes.Implicits, impTextStyleStrategy)
    //FIXME settings color of the underline is required to active TextStyle (bug ??, better way ??)
    b.setAnnotationTypeColor(AnnotationsTypes.Implicits, fColorValue)
    sourceViewer.asInstanceOf[org.eclipse.jface.text.TextViewer].addPainter(b)
    sourceViewer.asInstanceOf[org.eclipse.jface.text.TextViewer].addTextPresentationListener(b)
    b
  }

  private val _listener = new IPropertyChangeListener {
    def propertyChange(event: PropertyChangeEvent) {
      val changed = event.getProperty() match {
        case P_BOLD | P_ITALIC | P_COLOR => true
        case P_ACTIVE => {
          refresh()
          false
        }
        case _ => false
      }
      if (changed) {
        impTextStyleStrategy.fontStyle = isFontStyleBold | isFontStyleItalic
        painter.setAnnotationTypeColor(AnnotationsTypes.Implicits, fColorValue)
        painter.paint(IPainter.CONFIGURATION)
      }
    }
  }

  private def refresh() {
    val wb = PlatformUI.getWorkbench()
    for (
      win <- wb.getWorkbenchWindows;
      page <- win.getPages;
      editorRef <- page.getEditorReferences;
      editorIn <- Option(editorRef.getEditorInput)
    ) {
      JavaPlugin.getDefault().getWorkingCopyManager().getWorkingCopy(editorIn) match {
        case scu: ScalaCompilationUnit => update(scu)
        case _ => //ignore
      }
    }
  }

  protected def pluginStore: IPreferenceStore = ScalaPlugin.plugin.getPreferenceStore
  protected def editorsStore: IPreferenceStore = org.eclipse.ui.editors.text.EditorsUI.getPreferenceStore

  new PropertyChangeListenerProxy(_listener, pluginStore, editorsStore).autoRegister()

  //TODO monitor P_ACTIVATE to register/unregister update
  //TODO monitor P_ACTIVATE to remove existings annotation (true => false) or update openning file (false => true)
  def update(scu: ScalaCompilationUnit) = {
    
    if (scu.getResource.getLocation == editor.getPath.makeAbsolute) {
      scu.doWithSourceFile { (sourceFile, compiler) =>

        var annotationsToAdd = Collections.emptyMap[Annotation, Position]()
        
        if (pluginStore.getBoolean(P_ACTIVE)) {
          val response = new compiler.Response[compiler.Tree]
          compiler.askLoadedTyped(sourceFile, response)
          response.get(200) match {
            case Some(Left(_)) =>
              annotationsToAdd = findAllImplicitConversions(compiler, sourceFile)
            case Some(Right(exc)) => 
              logger.error(exc)
            case None =>
              logger.warning("Timeout while waiting for `askLoadedTyped` during implicit highlighting.")
          }
        }
        
        Annotations.update(sourceViewer, AnnotationsTypes.Implicits, annotationsToAdd)
      }
    }
  }
}

object SemanticHighlightingPresenter {
  final val DisplayStringSeparator = " => "
    
  def findAllImplicitConversions(compiler: ScalaPresentationCompiler, sourceFile: SourceFile) = {
    
    import compiler.{Tree, Traverser, ApplyImplicitView, ApplyToImplicitArgs}
    
    def mkImplicitConversionAnnotation(t: ApplyImplicitView) = {
      val txt = new String(sourceFile.content, t.pos.startOrPoint, math.max(0, t.pos.endOrPoint - t.pos.startOrPoint)).trim()
      val annotation = new ImplicitConversionsOrArgsAnnotation("Implicit conversions found: " + txt + DisplayStringSeparator + t.fun.symbol.name + "(" + txt + ")")
      val pos = new Position(t.pos.startOrPoint, txt.length)
      (annotation, pos)
    }
    
    def mkImplicitArgumentAnnotation(t: ApplyToImplicitArgs) = {
      val txt = new String(sourceFile.content, t.pos.startOrPoint, math.max(0, t.pos.endOrPoint - t.pos.startOrPoint)).trim()
      // Defensive, but why x.symbol is null (see bug 1000477) for "Some(x.flatten))"
      // TODO find the implicit args value
      val argsStr = t.args match {
        case null => ""
        case l => l.collect{case x if x.hasSymbol => x.symbol.name }.mkString("( ", ", ", " )")
      }
      val annotation = new ImplicitConversionsOrArgsAnnotation("Implicit arguments found: " + txt + DisplayStringSeparator + txt + argsStr)
      val pos = new Position(t.pos.startOrPoint, txt.length)
      (annotation, pos)
    }
    
    val implicits = new java.util.HashMap[Annotation, Position]

    new Traverser {
      override def traverse(t: Tree): Unit = {
        t match {
          case v: ApplyImplicitView =>
            val (annotation, pos) = mkImplicitConversionAnnotation(v)
            implicits.put(annotation, pos)
          case v: ApplyToImplicitArgs =>
            val (annotation, pos) = mkImplicitArgumentAnnotation(v)
            implicits.put(annotation, pos)
          case _ =>
        }
        super.traverse(t)
      }
    }.traverse(compiler.body(sourceFile))
          
    implicits
  }
}

package scala.tools.eclipse.semantichighlighting.implicits

import java.util.Collections
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.tools.eclipse.properties.ImplicitsPreferencePage._
import scala.tools.eclipse.reconciliation.ReconciliationParticipant
import scala.tools.eclipse.util.Utils.any2optionable
import scala.tools.eclipse.util.EclipseUtils
import scala.tools.eclipse.logging.HasLogger
import scala.tools.eclipse.ScalaPlugin
import scala.tools.eclipse.ScalaPresentationCompiler
import scala.reflect.internal.util.SourceFile
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jdt.core.WorkingCopyOwner
import org.eclipse.jdt.internal.ui.JavaPlugin
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.preference.PreferenceConverter
import org.eclipse.jface.text.source.Annotation
import org.eclipse.jface.text.source.AnnotationPainter
import org.eclipse.jface.text.source.IAnnotationAccess
import org.eclipse.jface.text.source.ISourceViewer
import org.eclipse.jface.text.IPainter
import org.eclipse.jface.text.Position
import org.eclipse.jface.text.TextViewer
import org.eclipse.jface.util.IPropertyChangeListener
import org.eclipse.jface.util.PropertyChangeEvent
import org.eclipse.swt.SWT
import org.eclipse.ui.editors.text.EditorsUI
import org.eclipse.ui.part.FileEditorInput
import scala.tools.eclipse.util.AnnotationUtils
import scala.tools.eclipse.semantic.SemanticAction
import scala.tools.eclipse.properties.ImplicitsPreferencePage
import org.eclipse.jface.text.Region
import scala.tools.eclipse.hyperlink.text._


/**
 * @author Jin Mingjian
 * @author David Bernard
 *
 */
class ImplicitHighlightingPresenter(sourceViewer: ISourceViewer) extends SemanticAction with HasLogger {
  import ImplicitHighlightingPresenter._

  val annotationAccess = new IAnnotationAccess {
    def getType(annotation: Annotation) = annotation.getType
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
    val pref = lookup.getAnnotationPreference(ImplicitAnnotation.ID)
    pref.getColorPreferenceKey()
  }

  private def colorValue = {
    val rgb = PreferenceConverter.getColor(EditorsUI.getPreferenceStore, P_COLOR)
    ColorManager.colorManager.getColor(rgb)
  }

  private val impTextStyleStrategy = new ImpliticAnnotationTextStyleStrategy(isFontStyleBold | isFontStyleItalic)

  private val painter: AnnotationPainter = {
    val p = new AnnotationPainter(sourceViewer, annotationAccess)
    p.addAnnotationType(ImplicitAnnotation.ID, ImplicitAnnotation.ID)
    p.addTextStyleStrategy(ImplicitAnnotation.ID, impTextStyleStrategy)
    //FIXME settings color of the underline is required to active TextStyle (bug ??, better way ??)
    p.setAnnotationTypeColor(ImplicitAnnotation.ID, colorValue)
    val textViewer = sourceViewer.asInstanceOf[TextViewer]
    textViewer.addPainter(p)
    textViewer.addTextPresentationListener(p)
    p
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
        painter.setAnnotationTypeColor(ImplicitAnnotation.ID, colorValue)
        painter.paint(IPainter.CONFIGURATION)
      }
    }
  }

  private def refresh() =
    for {
      page <- EclipseUtils.getWorkbenchPages
      editorReference <- page.getEditorReferences
      editorInput <- Option(editorReference.getEditorInput)
      compilationUnit <- Option(JavaPlugin.getDefault.getWorkingCopyManager.getWorkingCopy(editorInput))
      scu <- compilationUnit.asInstanceOfOpt[ScalaCompilationUnit]
    } apply(scu)

  private def pluginStore: IPreferenceStore = ScalaPlugin.plugin.getPreferenceStore

  PropertyChangeListenerProxy(_listener, pluginStore, EditorsUI.getPreferenceStore).autoRegister()

  //TODO monitor P_ACTIVATE to register/unregister update
  //TODO monitor P_ACTIVATE to remove existings annotation (true => false) or update openning file (false => true)
  override def apply(scu: ScalaCompilationUnit): Unit = {
    scu.doWithSourceFile { (sourceFile, compiler) =>
      var annotationsToAdd = Map[Annotation, Position]()

      if (pluginStore.getBoolean(P_ACTIVE)) {
        val response = new compiler.Response[compiler.Tree]
        compiler.askLoadedTyped(sourceFile, response)
        response.get(200) match {
          case Some(Left(_)) =>
            annotationsToAdd = findAllImplicitConversions(compiler, scu, sourceFile)
          case Some(Right(exc)) =>
            logger.error(exc)
          case None =>
            logger.warn("Timeout while waiting for `askLoadedTyped` during implicit highlighting.")
        }
      }

      AnnotationUtils.update(sourceViewer, ImplicitAnnotation.ID, annotationsToAdd)
    }
  }
}

object ImplicitHighlightingPresenter {
  final val DisplayStringSeparator = " => "

  private def pluginStore: IPreferenceStore = ScalaPlugin.plugin.getPreferenceStore

  def findAllImplicitConversions(compiler: ScalaPresentationCompiler, scu: ScalaCompilationUnit, sourceFile: SourceFile) = {
    import compiler.{ Tree, Traverser, ApplyImplicitView, ApplyToImplicitArgs }

    object ImplicitHyperlinkFactory extends HyperlinkFactory {
      protected val global: compiler.type = compiler
    }

    def mkPosition(pos: compiler.Position, txt: String): Position = {
      val start = pos.startOrPoint
      val end = if (pluginStore.getBoolean(ImplicitsPreferencePage.P_FIRST_LINE_ONLY)) {
        val eol = txt.indexOf('\n')
        if (eol > -1) eol else txt.length
      } else txt.length
      
      new Position(start, end)
    }

    def mkImplicitConversionAnnotation(t: ApplyImplicitView) = {
      val txt = new String(sourceFile.content, t.pos.startOrPoint, math.max(0, t.pos.endOrPoint - t.pos.startOrPoint)).trim()      
      val pos = mkPosition(t.pos, txt)
      val region = new Region(pos.offset, pos.getLength)
      val annotation = new ImplicitConversionAnnotation(() => ImplicitHyperlinkFactory.create(Hyperlink.withText("Open Implicit"), t.symbol, region),
          "Implicit conversions found: " + txt + DisplayStringSeparator + t.fun.symbol.name + "(" + txt + ")")
      
      (annotation, pos)
    }

    def mkImplicitArgumentAnnotation(t: ApplyToImplicitArgs) = {
      val txt = new String(sourceFile.content, t.pos.startOrPoint, math.max(0, t.pos.endOrPoint - t.pos.startOrPoint)).trim()
      // Defensive, but why x.symbol is null (see bug 1000477) for "Some(x.flatten))"
      // TODO find the implicit args value
      val argsStr = t.args match {
        case null => ""
        case l => l.map { x =>
          if ((x.symbol ne null) && (x.symbol ne compiler.NoSymbol))
            x.symbol.fullName
          else
            "<error>"
        }.mkString("( ", ", ", " )")
      }
      val annotation = new ImplicitArgAnnotation("Implicit arguments found: " + txt + DisplayStringSeparator + txt + argsStr)
      val pos = mkPosition(t.pos, txt)
      (annotation, pos)
    }

    var implicits = Map[Annotation, Position]()

    new Traverser {
      override def traverse(t: Tree): Unit = {
        t match {
          case v: ApplyImplicitView =>
            val (annotation, pos) = mkImplicitConversionAnnotation(v)
            implicits += (annotation -> pos)
          case v: ApplyToImplicitArgs if !pluginStore.getBoolean(ImplicitsPreferencePage.P_CONVERSIONS_ONLY) =>
            val (annotation, pos) = mkImplicitArgumentAnnotation(v)
            implicits += (annotation -> pos)
          case _ =>
        }
        super.traverse(t)
      }
    }.traverse(compiler.loadedType(sourceFile).fold(identity, _ => compiler.EmptyTree))

    implicits
  }
}

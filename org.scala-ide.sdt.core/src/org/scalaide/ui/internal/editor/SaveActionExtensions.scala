package org.scalaide.ui.internal.editor

import scala.concurrent.duration._
import scala.reflect.internal.util.SourceFile
import scala.tools.refactoring.common.{ TextChange => RTextChange }
import scala.util.Failure
import scala.util.Left
import scala.util.Right
import scala.util.Success

import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.jdt.internal.ui.javaeditor.saveparticipant.IPostSaveListener
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.ITextSelection
import org.eclipse.text.undo.DocumentUndoManagerRegistry
import org.scalaide.core.IScalaPlugin
import org.scalaide.core.compiler.IScalaPresentationCompiler
import org.scalaide.core.compiler.IScalaPresentationCompiler.Implicits._
import org.scalaide.core.internal.extensions.ExtensionCompiler
import org.scalaide.core.internal.extensions.ExtensionCreators
import org.scalaide.core.internal.extensions.SaveActions
import org.scalaide.core.internal.jdt.model.ScalaSourceFile
import org.scalaide.core.internal.statistics.Features.Feature
import org.scalaide.core.internal.statistics.Groups
import org.scalaide.core.internal.text.TextDocument
import org.scalaide.core.text.Change
import org.scalaide.core.text.TextChange
import org.scalaide.extensions.CompilerSupport
import org.scalaide.extensions.SaveAction
import org.scalaide.extensions.SaveActionSetting
import org.scalaide.extensions.SaveActionSetting
import org.scalaide.logging.HasLogger
import org.scalaide.util.eclipse.EclipseUtils
import org.scalaide.util.eclipse.EditorUtils
import org.scalaide.util.internal.FutureUtils
import org.scalaide.util.internal.eclipse.TextEditUtils

object SaveActionExtensions extends AnyRef with HasLogger {

  /**
   * Contains all available document save actions. They are cached here
   * because their creation is expensive.
   */
  private val documentSaveActions = {
    SaveActions.documentSaveActionsData flatMap {
      case (fqn, setting) ⇒
        ExtensionCompiler.savelyLoadExtension[ExtensionCreators.DocumentSaveAction](fqn).map(setting → _)
    }
  }

  /**
   * Contains all available compiler save actions. They are cached here
   * because their creation is expensive.
   */
  private val compilerSaveActions = {
    SaveActions.compilerSaveActionsData flatMap {
      case (fqn, setting) ⇒
        ExtensionCompiler.savelyLoadExtension[ExtensionCreators.CompilerSaveAction](fqn).map(setting → _)
    }
  }

  /**
   * The time a save action gets until the IDE waits no longer on its result.
   */
  private def saveActionTimeout: FiniteDuration =
    IScalaPlugin().getPreferenceStore().getInt(SaveActions.SaveActionTimeoutId).millis
}

trait SaveActionExtensions extends HasLogger {
  import SaveActionExtensions._

  /**
   * It is necessary to store the selection and the source file where the
   * selection is applied to in order to prevent unnecessary updates of the
   * editor. This is important because updating the editor is a costly
   * operation. The editor should only be updated once after all save actions
   * are computed, but after each save action all computed changes have to be
   * applied to the underlying source file in order to keep subsequent save
   * actions up to date with all changes.
   *
   * All intermediate state is stored in this variable and in `lastSourceFile`.
   */
  private[this] var lastSelection: ITextSelection = _

  /** See `lastSelection` for an explanation why this variable is needed. */
  private[this] var lastSourceFile: ScalaSourceFile = _

  /**
   * This provides a listener of an API that can be understood by JDT. We don't
   * really need it but as long as [[ScalaDocumentProvider]] is based on the
   * API of JDT we don't have the choice to not use it.
   */
  def createScalaSaveActionListener(udoc: IDocument): IPostSaveListener = {
    new IPostSaveListener {
      override def getName = "ScalaSaveActions"
      override def getId = "ScalaSaveActions"
      override def needsChangedRegions(cu: ICompilationUnit) = false
      override def saved(cu: ICompilationUnit, changedRegions: Array[IRegion], monitor: IProgressMonitor): Unit = {
        EclipseUtils.withSafeRunner("An error occurred while executing Scala save actions") {
          applySaveActions(udoc)
        }
      }
    }
  }

  /**
   * Applies all save actions to the contents of the given document.
   *
   * Throws [[IllegalStateException]] when save actions had to be aborted.
   */
  private def applySaveActions(udoc: IDocument): Unit = {
    def updateEditor() = EditorUtils.doWithCurrentEditor {
      _.selectAndReveal(lastSelection.getOffset, lastSelection.getLength)
    }

    EditorUtils.withScalaSourceFileAndSelection { (ssf, sel) =>
      lastSourceFile = ssf
      lastSelection = sel

      val undoManager = DocumentUndoManagerRegistry.getDocumentUndoManager(udoc)
      undoManager.beginCompoundChange()

      try {
        applyDocumentExtensions(udoc)
        applyCompilerExtensions(udoc)
      }
      finally {
        updateEditor()

        undoManager.endCompoundChange()

        lastSourceFile = null
        lastSelection = null
      }
      None
    }
  }

  /**
   * Applies all save actions that extend [[DocumentSupport]].
   */
  private def applyDocumentExtensions(udoc: IDocument): Unit = {
    for ((setting, ext) <- documentSaveActions if isEnabled(setting.id)) {
      val doc = new TextDocument(udoc)
      val instance = ext(doc)
      performDocumentExtension(instance, udoc) {
        instance.perform()
      }
    }
  }

  /**
   * Applies all save actions that extend [[CompilerSupport]].
   */
  private def applyCompilerExtensions(udoc: IDocument): Unit = {
    val timeout = saveActionTimeout

    def loop(xs: Seq[(SaveActionSetting, ExtensionCreators.CompilerSaveAction)]): Unit = xs match {
      case Seq() ⇒

      case (setting, ext) +: xs if isEnabled(setting.id) ⇒
        val res = FutureUtils.performWithTimeout(timeout) {
          EclipseUtils.withSafeRunner(s"An error occurred while executing save action '${setting.id}'.") {
            createExtensionWithCompilerSupport(ext) map { instance =>
              instance.global.asInstanceOf[IScalaPresentationCompiler].asyncExec {
                instance.perform()
              }.getOrElse(Seq())()
            }
          }.flatten.getOrElse(Seq())
        }

        res match {
          case Success(changes) ⇒
            EclipseUtils.withSafeRunner(s"An error occurred while applying changes of save action '${setting.id}'.") {
              applyChanges(setting, changes, udoc)
            }
            loop(xs)

          case Failure(_) ⇒
            eclipseLog.error(s"""|
               |A save action that relies on compiler support didn't complete in a
               | given time, it had $timeout time to complete. Because it is
               | unlikely that further save actions that rely on compiler support will
               | complete in time, no further save actions are executed. Please consider
               | to disable save actions that rely on the compiler. The performed save
               | action itself couldn't be aborted, therefore if you know that it may
               | never complete in future, you may wish to restart your Eclipse to
               | clean up your VM.
               |
               |""".stripMargin.replaceAll("\n", ""))
        }

      case _ +: xs ⇒
        loop(xs)
    }
    loop(compilerSaveActions)
  }

  /**
   * Performs an extension, but waits not more than [[saveActionTimeout]] for a
   * completion of the save actions calculation.
   *
   * The save action can't be aborted, therefore this method only returns early
   * but may leave the `Future` in a never ending state.
   *
   * `ext` is the actual computation that executes the save action in order to
   * get a sequence of changes. It is executes in a safe environment that
   * catches errors.
   */
  private def performDocumentExtension(instance: SaveAction, udoc: IDocument)(ext: => Seq[Change]): Unit = {
    val id = instance.setting.id
    val timeout = saveActionTimeout

    val res = FutureUtils.performWithTimeout(timeout) {
      EclipseUtils.withSafeRunner(s"An error occurred while executing save action '$id'.") {
        ext
      }.getOrElse(Seq())
    }

    res match {
      case Success(changes) =>
        EclipseUtils.withSafeRunner(s"An error occurred while applying changes of save action '$id'.") {
          applyChanges(instance.setting, changes, udoc)
        }

      case Failure(f) =>
        eclipseLog.error(s"""|
           |Save action '$id' didn't complete, it had $timeout
           | time to complete. Please consider to disable it in the preferences.
           | The save action itself can't be aborted, therefore if you know that
           | it may never complete in future, you may wish to restart your Eclipse
           | to clean up your VM.
           |
           |""".stripMargin.replaceAll("\n", ""))
    }
  }

  /**
   * Executing this method has side effects. It applies all changes to `udoc`,
   * the underlying file and it updates `lastSelection`.
   */
  private def applyChanges(setting: SaveActionSetting, changes: Seq[Change], udoc: IDocument): Unit = {
    if (changes.isEmpty)
      return

    val feature = Feature(setting.id)(setting.name, Groups.SaveAction)
    feature.incUsageCounter()

    val sf = lastSourceFile.lastSourceMap().sourceFile
    val len = udoc.getLength()
    val edits = changes map {
      case tc @ TextChange(start, end, text) =>
        if (start < 0 || end > len || end < start || text == null)
          throw new IllegalArgumentException(s"The text change object '$tc' of save action '${setting.id}' is invalid.")
        new RTextChange(sf, start, end, text)
    }
    TextEditUtils.applyChangesToFile(udoc, lastSelection, lastSourceFile.file, edits.toList) match {
      case Some(sel) => lastSelection = sel
      case _         => throw new IllegalStateException("Couldn't apply changes to underlying file. All remaining save actions have to be aborted.")
    }
  }

  private def createExtensionWithCompilerSupport(creator: ExtensionCreators.CompilerSaveAction): Option[SaveAction with CompilerSupport] = {
    lastSourceFile.withSourceFile { (sf, compiler) =>
      import compiler._

      val r = new Response[Tree]
      askLoadedTyped(sf, r)
      r.get match {
        case Left(tree) =>
          Some(creator(compiler, tree, sf, lastSelection.getOffset(), lastSelection.getOffset()+lastSelection.getLength()))
        case Right(e) =>
          logger.error(
              s"An error occurred while trying to get tree of file '${sf.file.name}'.", e)
          None
      }
    }.flatten
  }

  /** Checks if a save action given by its `id` is enabled. */
  private def isEnabled(id: String): Boolean =
    IScalaPlugin().getPreferenceStore().getBoolean(id)
}

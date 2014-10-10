package org.scalaide.ui.internal.editor

import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.reflect.internal.util.SourceFile
import scala.tools.refactoring.common.{TextChange => RTextChange}
import scala.util._

import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.jdt.internal.ui.javaeditor.saveparticipant.IPostSaveListener
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.IRegion
import org.eclipse.text.undo.DocumentUndoManagerRegistry
import org.scalaide.core.IScalaPlugin
import org.scalaide.core.compiler.IScalaPresentationCompiler
import org.scalaide.core.compiler.IScalaPresentationCompiler.Implicits._
import org.scalaide.core.internal.extensions.saveactions._
import org.scalaide.core.internal.text.TextDocument
import org.scalaide.core.text.Change
import org.scalaide.core.text.TextChange
import org.scalaide.extensions.CompilerSupport
import org.scalaide.extensions.SaveAction
import org.scalaide.extensions.SaveActionSetting
import org.scalaide.extensions.saveactions._
import org.scalaide.logging.HasLogger
import org.scalaide.util.eclipse.EclipseUtils
import org.scalaide.util.eclipse.EditorUtils
import org.scalaide.util.internal.FutureUtils.TimeoutFuture
import org.scalaide.util.internal.eclipse.TextEditUtils

object SaveActionExtensions {

  /**
   * The ID which is used as key in the preference store to identify the actual
   * timeout value for save actions.
   */
  final val SaveActionTimeoutId: String = "org.scalaide.extensions.SaveAction.Timeout"

  /**
   * The time a save action gets until the IDE waits no longer on its result.
   */
  private def saveActionTimeout: FiniteDuration =
    IScalaPlugin().getPreferenceStore().getInt(SaveActionTimeoutId).millis

  private val documentSaveActions = Seq(
    RemoveTrailingWhitespaceSetting -> RemoveTrailingWhitespaceCreator.create _,
    AddNewLineAtEndOfFileSetting -> AddNewLineAtEndOfFileCreator.create _,
    AutoFormattingSetting -> AutoFormattingCreator.create _,
    RemoveDuplicatedEmptyLinesSetting -> RemoveDuplicatedEmptyLinesCreator.create _,
    TabToSpaceConverterSetting -> TabToSpaceConverterCreator.create _
  )

  private val compilerSaveActions = Seq[(SaveActionSetting, CompilerSupportCreator)](
  )

  private type CompilerSupportCreator = (
      IScalaPresentationCompiler, IScalaPresentationCompiler#Tree,
      SourceFile, Int, Int
    ) => SaveAction with CompilerSupport

  /**
   * The settings for all existing save actions.
   */
  val saveActionSettings: Seq[SaveActionSetting] =
    documentSaveActions.map(_._1) ++ compilerSaveActions.map(_._1)
}

trait SaveActionExtensions extends HasLogger {
  import SaveActionExtensions._

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
   */
  private def applySaveActions(udoc: IDocument): Unit = {
    val undoManager = DocumentUndoManagerRegistry.getDocumentUndoManager(udoc)
    undoManager.beginCompoundChange()

    applyDocumentExtensions(udoc)

    undoManager.endCompoundChange()
  }

  /**
   * Applies all save actions that extends [[DocumentSupport]].
   */
  private def applyDocumentExtensions(udoc: IDocument) = {
    for ((setting, ext) <- documentSaveActions if isEnabled(setting.id)) {
      val doc = new TextDocument(udoc)
      val instance = ext(doc)
      performExtension(instance, udoc) {
        instance.perform()
      }
    }
  }

  /**
   * Applies all save actions that extends [[CompilerSupport]].
   */
  private def applyCompilerExtensions(udoc: IDocument) = {
    for ((setting, ext) <- compilerSaveActions if isEnabled(setting.id)) {
      createExtensionWithCompilerSupport(ext) foreach { instance =>
        performExtension(instance, udoc) {
          instance.global.asInstanceOf[IScalaPresentationCompiler].asyncExec {
            instance.perform()
          }.getOrElse(Seq())()
        }
      }
    }
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
  private def performExtension(instance: SaveAction, udoc: IDocument)(ext: => Seq[Change]) = {
    val id = instance.setting.id
    val timeout = saveActionTimeout

    val futureToUse: Seq[Change] => Future[Seq[Change]] =
      if (IScalaPlugin().noTimeoutMode)
        Future(_)
      else
        TimeoutFuture(timeout)(_)

    val f = futureToUse {
      EclipseUtils.withSafeRunner(s"An error occurred while executing save action '$id'.") {
        ext
      }.getOrElse(Seq())
    }
    Await.ready(f, Duration.Inf).value.get match {
      case Success(changes) =>
        EclipseUtils.withSafeRunner(s"An error occurred while applying changes of save action '$id'.") {
          applyChanges(id, changes, udoc)
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

  private def applyChanges(saveActionId: String, changes: Seq[Change], udoc: IDocument) = {
    EditorUtils.withScalaSourceFileAndSelection { (ssf, sel) =>
      val sf = ssf.lastSourceMap().sourceFile
      val len = udoc.getLength()
      val edits = changes map {
        case tc @ TextChange(start, end, text) =>
          if (start < 0 || end > len || end < start || text == null)
            throw new IllegalArgumentException(s"The text change object '$tc' of save action '$saveActionId' is invalid.")
          new RTextChange(sf, start, end, text)
      }
      TextEditUtils.applyChangesToFileWhileKeepingSelection(udoc, sel, ssf.file, edits.toList)
      None
    }
  }

  private def createExtensionWithCompilerSupport(creator: CompilerSupportCreator): Option[SaveAction with CompilerSupport] = {
    EditorUtils.withScalaSourceFileAndSelection { (ssf, sel) =>
      ssf.withSourceFile { (sf, compiler) =>
        import compiler._

        val r = new Response[Tree]
        askLoadedTyped(sf, r)
        r.get match {
          case Left(tree) =>
            Some(creator(compiler, tree, sf, sel.getOffset(), sel.getOffset()+sel.getLength()))
          case Right(e) =>
            logger.error(
                s"An error occurred while trying to get tree of file '${sf.file.name}'.", e)
            None
        }
      }.flatten
    }
  }

  /** Checks if a save action given by its `id` is enabled. */
  private def isEnabled(id: String): Boolean =
    IScalaPlugin().getPreferenceStore().getBoolean(id)
}

package org.scalaide.ui.internal.editor

import scala.reflect.internal.util.SourceFile
import scala.tools.refactoring.common.{TextChange => RTextChange}

import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.jdt.internal.ui.javaeditor.saveparticipant.IPostSaveListener
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.IRegion
import org.scalaide.core.ScalaPlugin
import org.scalaide.core.compiler.ScalaPresentationCompiler
import org.scalaide.core.internal.extensions.saveactions.AddNewLineAtEndOfFileCreator
import org.scalaide.core.internal.extensions.saveactions.AutoFormattingCreator
import org.scalaide.core.internal.extensions.saveactions.RemoveTrailingWhitespaceCreator
import org.scalaide.core.internal.text.TextDocument
import org.scalaide.core.text.Change
import org.scalaide.core.text.TextChange
import org.scalaide.extensions.SaveAction
import org.scalaide.extensions.SaveActionSetting
import org.scalaide.extensions.saveactions.AddNewLineAtEndOfFileSetting
import org.scalaide.extensions.saveactions.AutoFormattingSetting
import org.scalaide.extensions.saveactions.RemoveTrailingWhitespaceSetting
import org.scalaide.logging.HasLogger
import org.scalaide.util.internal.eclipse.EclipseUtils
import org.scalaide.util.internal.eclipse.EditorUtils

object SaveActionExtensions {

  /**
   * The settings for all existing save actions.
   */
  val saveActionSettings: Seq[SaveActionSetting] = Seq(
    RemoveTrailingWhitespaceSetting,
    AddNewLineAtEndOfFileSetting,
    AutoFormattingSetting
  )
}

trait SaveActionExtensions extends HasLogger {

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
    applyDocumentExtensions(udoc)
    applyCompilerExtensions(udoc)
  }

  /**
   * Applies all save actions that extends [[DocumentSupport]].
   */
  private def applyDocumentExtensions(udoc: IDocument) = {
    val exts = Seq(
      RemoveTrailingWhitespaceCreator.create _,
      AddNewLineAtEndOfFileCreator.create _,
      AutoFormattingCreator.create _
    )

    val doc = new TextDocument(udoc)
    for (ext <- exts) {
      val instance = ext(doc)
      performExtension(instance, udoc)
    }
  }

  /**
   * Applies all save actions that extends [[CompilerSupport]].
   */
  private def applyCompilerExtensions(udoc: IDocument) = {
    val exts = Seq[CompilerSupportCreator](
    )

    for (ext <- exts) {
      createExtensionWithCompilerSupport(ext) foreach { instance =>
        performExtension(instance, udoc)
      }
    }
  }

  private def performExtension(instance: SaveAction, udoc: IDocument) = {
    def isEnabled(id: String): Boolean =
      ScalaPlugin.prefStore.getBoolean(id)

    val enabled = isEnabled(instance.setting.id)
    logger.info(s"Save action '${instance.setting.id}' is enabled: $enabled")

    val changes =
      if (enabled)
        EclipseUtils.withSafeRunner(s"An error occurred while executing save action ${instance.setting.id}.") {
          instance.perform()
        }
      else
        None
    applyChanges(changes.getOrElse(Seq()), udoc)
  }

  private def applyChanges(changes: Seq[Change], udoc: IDocument) = {
    EditorUtils.withScalaSourceFileAndSelection { (ssf, sel) =>
      val sf = ssf.sourceFile()
      val edits = changes map {
        case TextChange(start, end, text) =>
          new RTextChange(sf, start, end, text)
      }
      EditorUtils.applyChangesToFileWhileKeepingSelection(udoc, sel, ssf.file, edits.toList)
      None
    }
  }

  private type CompilerSupportCreator = (
      ScalaPresentationCompiler, ScalaPresentationCompiler#Tree,
      SourceFile, Int, Int
    ) => SaveAction

  private def createExtensionWithCompilerSupport(creator: CompilerSupportCreator): Option[SaveAction] = {
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
}
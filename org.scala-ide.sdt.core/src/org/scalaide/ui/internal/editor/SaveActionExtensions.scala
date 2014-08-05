package org.scalaide.ui.internal.editor

import scala.tools.refactoring.common.{TextChange => RTextChange}

import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.jdt.internal.ui.javaeditor.saveparticipant.IPostSaveListener
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.IRegion
import org.scalaide.core.ScalaPlugin
import org.scalaide.core.internal.extensions.saveactions.AddNewLineAtEndOfFileCreator
import org.scalaide.core.internal.extensions.saveactions.RemoveTrailingWhitespaceCreator
import org.scalaide.core.internal.text.TextDocument
import org.scalaide.core.text.TextChange
import org.scalaide.extensions.CompilerSupport
import org.scalaide.extensions.SaveActionSetting
import org.scalaide.extensions.ScalaIdeExtension
import org.scalaide.extensions.saveactions.AddNewLineAtEndOfFileSetting
import org.scalaide.extensions.saveactions.RemoveTrailingWhitespaceSetting
import org.scalaide.logging.HasLogger
import org.scalaide.util.internal.eclipse.EditorUtils

object SaveActionExtensions {

  val saveActionSettings: Seq[SaveActionSetting] = Seq(
    RemoveTrailingWhitespaceSetting,
    AddNewLineAtEndOfFileSetting
  )
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
        try compilationUnitSaved(cu, udoc)
        catch {
          case e: Exception =>
            logger.error("Error while executing Scala save actions", e)
        }
      }
    }
  }

  private def compilationUnitSaved(cu: ICompilationUnit, udoc: IDocument): Unit = {
    val changes = documentSuppportExtensions(udoc) ++ compilerSupportExtensions

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

  private def findEnabledExtensions[A <: ScalaIdeExtension](exts: Seq[A]): Seq[A] = {
    def isEnabled(id: String): Boolean =
      ScalaPlugin.prefStore.getBoolean(id)

    exts filter { ext =>
      saveActionSettings.find(_ == ext.setting) exists { s =>
        isEnabled(s.id)
      }
    }
  }

  private def documentSuppportExtensions(udoc: IDocument) = {
    val doc = new TextDocument(udoc)
    val extensions = Seq(
      RemoveTrailingWhitespaceCreator.create(doc),
      AddNewLineAtEndOfFileCreator.create(doc)
    )
    val enabledExtensions = findEnabledExtensions(extensions)

    enabledExtensions.flatMap(_.perform())
  }

  private def compilerSupportExtensions() = {
    EditorUtils.withScalaSourceFileAndSelection { (ssf, sel) =>
      ssf.withSourceFile { (sf, compiler) =>
        import compiler._

        val r = new Response[Tree]
        askLoadedTyped(sf, r)
        r.get match {
          case Left(tree) =>
            val extensions = Seq[CompilerSupport]()
            val enabledExtensions = findEnabledExtensions(extensions)

            enabledExtensions.flatMap(_.perform())
          case Right(e) =>
            logger.error(
                s"An error occurred while trying to get tree of file '${sf.file.name}'."+
                s" Aborting all save actions that extend ${classOf[CompilerSupport].getName()}", e)
            Seq()
        }
      }
    }.toSeq.flatten
  }
}
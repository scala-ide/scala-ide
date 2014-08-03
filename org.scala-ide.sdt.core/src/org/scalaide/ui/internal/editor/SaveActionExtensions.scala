package org.scalaide.ui.internal.editor

import scala.tools.refactoring.common.{TextChange => RTextChange}

import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.jdt.internal.ui.javaeditor.saveparticipant.IPostSaveListener
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.IRegion
import org.scalaide.core.internal.extensions.saveactions.RemoveTrailingWhitespaceCreator
import org.scalaide.core.internal.text.TextDocument
import org.scalaide.core.text.TextChange
import org.scalaide.logging.HasLogger
import org.scalaide.util.internal.eclipse.EditorUtils

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
        try compilationUnitSaved(cu, udoc)
        catch {
          case e: Exception =>
            logger.error("Error while executing Scala save actions", e)
        }
      }
    }
  }

  private def compilationUnitSaved(cu: ICompilationUnit, udoc: IDocument): Unit = {
    val doc = new TextDocument(udoc)
    val extensions = Seq(RemoveTrailingWhitespaceCreator.create(doc))
    val changes = extensions.flatMap(_.perform())

    EditorUtils.withScalaSourceFileAndSelection { (ssf, sel) =>
      val sv = ssf.sourceFile()
      val edits = changes map {
        case TextChange(start, end, text) =>
          new RTextChange(sv, start, end, text)
      }
      EditorUtils.applyChangesToFileWhileKeepingSelection(udoc, sel, ssf.file, edits.toList)
      None
    }
  }

}
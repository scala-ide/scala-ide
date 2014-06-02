package org.scalaide.ui.internal.editor.decorators.macros

import org.scalaide.ui.internal.editor.decorators.BaseSemanticAction
import org.eclipse.jface.text.source.ISourceViewer
import org.scalaide.core.internal.jdt.model.ScalaCompilationUnit
import scala.reflect.internal.util.SourceFile
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text.source.Annotation
import org.eclipse.jface.text.Position
import org.scalaide.core.internal.compiler.ScalaPresentationCompiler
import org.scalaide.ui.internal.editor.decorators.BaseSemanticAction

/*
 * Semantic action for highlighting macros.
 * */
class MacroHighlightingPresenter(sourceViewer: ISourceViewer)
  extends BaseSemanticAction(
    sourceViewer,
    MacroExpansionAnnotation.ID,
    Some("macro")) {
  protected override def findAll(compiler: ScalaPresentationCompiler, scu: ScalaCompilationUnit, sourceFile: SourceFile): Map[Annotation, Position] =
    MacroHighlightingPresenter.findAllMacros(compiler, scu, sourceFile)
}

object MacroHighlightingPresenter {
  def findAllMacros(compiler: ScalaPresentationCompiler, scu: ScalaCompilationUnit, sourceFile: SourceFile) = {
    import compiler.Tree
    import compiler.Traverser

    var macroExpansions = Map[Annotation, Position]()

    new Traverser {
      override def traverse(t: Tree): Unit = {
        // If there exist an attachment conforming to the ScalaMeta API
        val macroAttachment = if (t.attachments.get[java.util.HashMap[String, Any]].isDefined) {
          t.attachments.get[java.util.HashMap[String, Any]].map { att =>
            // Conforming to ScalaMeta API, there should exist expandee under the "expandeeTree" key, and
            // a source code listing that is equal to macro expansion under the "expansionString" key.
            (att.get("expandeeTree").asInstanceOf[Tree], att.get("expansionString").asInstanceOf[String])
          }
        } // Or if there exist an attachment, conforming to internal scalac's API
        else if (t.attachments.get[compiler.analyzer.MacroExpansionAttachment].isDefined) {
          t.attachments.get[compiler.analyzer.MacroExpansionAttachment].map { att =>
            /* see compiler.analyzer.MacroExpansionAttachment */
            (att.expandee, compiler.showCode(att.expanded.asInstanceOf[Tree]))
          }
        } else None

        macroAttachment.flatMap {
          case (expandeeTree: Tree, expansionString: String) =>
            val originalMacroPos = expandeeTree.pos
            if (expandeeTree.symbol.fullName == "scala.reflect.materializeClassTag" || originalMacroPos.start == originalMacroPos.end) None
            else {
              val annotation = new MacroExpansionAnnotation(expansionString)
              val pos = new Position(originalMacroPos.start, originalMacroPos.end - originalMacroPos.start)
              Some(annotation, pos)
            }
        }.map {
          case (annotation: MacroExpansionAnnotation, pos: Position) =>
            macroExpansions += (annotation -> pos)
        }
        super.traverse(t)
      }
    }.traverse(compiler.loadedType(sourceFile).fold(identity, _ => compiler.EmptyTree))

    macroExpansions
  }
}
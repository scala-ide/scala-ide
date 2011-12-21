package scala.tools.eclipse.semantichighlighting.classifier

import scala.tools.nsc.interactive.Global
import scala.tools.nsc.util.SourceFile

object SymbolClassifier {

  def classifySymbols(sourceFile: SourceFile, global: Global, useSyntacticHints: Boolean): List[SymbolInfo] =
    new SymbolClassification(sourceFile, global, useSyntacticHints).classifySymbols

}

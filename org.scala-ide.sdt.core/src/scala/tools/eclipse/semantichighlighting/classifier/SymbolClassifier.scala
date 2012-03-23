package scala.tools.eclipse.semantichighlighting.classifier

import scala.tools.nsc.util.SourceFile
import scala.tools.eclipse.ScalaPresentationCompiler

object SymbolClassifier {

  def classifySymbols(sourceFile: SourceFile, compiler: ScalaPresentationCompiler, useSyntacticHints: Boolean): List[SymbolInfo] =
    new SymbolClassification(sourceFile, compiler, useSyntacticHints).classifySymbols

}

package scala.tools.eclipse.lexical

import org.eclipse.jface.text.rules._

abstract class AbstractRule(val getSuccessToken: IToken) extends IPredicateRule {

  // See NoResumeRule
  def evaluate(scanner: ICharacterScanner, resume: Boolean) = if (resume) Token.UNDEFINED else evaluate(scanner)

  def evaluate(originalScanner: ICharacterScanner) = evaluate(new RewindableScanner(originalScanner))

  def evaluate(scanner: RewindableScanner): IToken

}

package scala.tools.eclipse.lexical

import org.eclipse.jface.text.rules._

/**
 * Mixin to always return an UNDEFINED token if asked to resume mid-partition. If all the rules in a RuleBasedPartitionScanner have this behaviour, then it falls back to scanning
 * from the beginning of the partition.
 */
trait NoResumeRule extends IPredicateRule {

  abstract override def evaluate(scanner: ICharacterScanner, resume: Boolean) = if (resume) Token.UNDEFINED else super.evaluate(scanner, false)

}

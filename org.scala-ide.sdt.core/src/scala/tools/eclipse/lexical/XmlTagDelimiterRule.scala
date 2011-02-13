package scala.tools.eclipse.lexical

import org.eclipse.jface.text.rules._
import scala.annotation.{ tailrec, switch }

class XmlTagDelimiterRule(val successToken: IToken) extends AbstractRule(successToken) {
  def evaluate(scanner: RewindableScanner): IToken =
    scanner.read() match {
      case '<' | '>' =>
        successToken
      case _ =>
        scanner.noMatch
    }
}
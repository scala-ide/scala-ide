package scala.tools.eclipse.lexical

import org.eclipse.jface.text.rules._
import scala.annotation.{ tailrec, switch }

class BackQuotedIdentRule(val successToken: IToken) extends AbstractRule(successToken) {

  def evaluate(scanner: RewindableScanner): IToken =
    if (scanner.read() == '`')
      consumeUntilBackQuote(scanner)
    else
      scanner.noMatch()

  @tailrec
  private def consumeUntilBackQuote(scanner: RewindableScanner): IToken =
    (scanner.read(): @switch) match {
      case '`' | '\n' ⇒ successToken
      case '\r' if scanner.peek != '\n' ⇒ successToken
      case ICharacterScanner.EOF ⇒ {
        scanner.unread()
        successToken
      }
      case _ ⇒ consumeUntilBackQuote(scanner)
    }
}
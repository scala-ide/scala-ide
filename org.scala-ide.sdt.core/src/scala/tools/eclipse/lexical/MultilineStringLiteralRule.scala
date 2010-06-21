package scala.tools.eclipse.lexical

import org.eclipse.jface.text.rules._
import scala.annotation.{ tailrec, switch }

class MultilineStringLiteralRule(val successToken: IToken) extends AbstractRule(successToken) {

  def evaluate(scanner: RewindableScanner): IToken =
    if (scanner.read() != '"')
      scanner.noMatch()
    else if (scanner.read() != '"')
      scanner.noMatch()
    else if (scanner.read() != '"')
      scanner.noMatch()
    else
      consumeUntilTripleQuotes(scanner)

  @tailrec
  private def consumeUntilTripleQuotes(scanner: RewindableScanner): IToken =
    (scanner.read(): @switch) match {
      case '"' =>
        if (scanner.read() == '"')
          if (scanner.read() == '"') {
            while (scanner.read() == '"') {}
            scanner.unread()
            successToken
          } else {
            scanner.unread()
            consumeUntilTripleQuotes(scanner)
          }
        else {
          scanner.unread()
          consumeUntilTripleQuotes(scanner)
        }
      case ICharacterScanner.EOF => {
        scanner.unread()
        successToken
      }
      case _ =>
        consumeUntilTripleQuotes(scanner)
    }

}
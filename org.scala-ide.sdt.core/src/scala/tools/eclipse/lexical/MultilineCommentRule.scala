package scala.tools.eclipse.lexical

import org.eclipse.jface.text.rules._
import scala.annotation.{ tailrec, switch }

class MultilineCommentRule(val successToken: IToken, val scalaDoc: Boolean) extends AbstractRule(successToken) {

  def evaluate(scanner: RewindableScanner): IToken = {
    if (scanner.read() != '/')
      scanner.noMatch()
    else if (scanner.read() != '*')
      scanner.noMatch()
    else if (scalaDoc && scanner.read() != '*')
      scanner.noMatch()
    else if (scalaDoc && scanner.peek == '/')
      scanner.noMatch()
    else
      consumeUntilSplatSlash(scanner, nesting = 1)
  }

  @tailrec
  private def consumeUntilSplatSlash(scanner: RewindableScanner, nesting: Int): IToken =
    (scanner.read(): @switch) match {
      case '/' =>
        if (scanner.read() == '*')
          consumeUntilSplatSlash(scanner, nesting + 1)
        else {
          scanner.unread()
          consumeUntilSplatSlash(scanner, nesting)
        }
      case '*' =>
        if (scanner.read() == '/')
          if (nesting == 1)
            successToken
          else
            consumeUntilSplatSlash(scanner, nesting - 1)
        else {
          scanner.unread()
          consumeUntilSplatSlash(scanner, nesting)
        }
      case ICharacterScanner.EOF => {
        scanner.unread()
        successToken
      }
      case _ => consumeUntilSplatSlash(scanner, nesting)
    }

}
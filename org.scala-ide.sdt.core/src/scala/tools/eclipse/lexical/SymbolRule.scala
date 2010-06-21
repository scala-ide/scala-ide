package scala.tools.eclipse.lexical

import org.eclipse.jface.text.rules._
import scala.tools.nsc.util.Chars._
import scala.annotation.tailrec

class SymbolRule(private val successToken: IToken) extends AbstractRule(successToken) {
  
  def evaluate(scanner: RewindableScanner): IToken = {
    val ch1 = scanner.read()
    if (ch1 != '\'')
      scanner.noMatch()
    else {
      val ch2 = scanner.read()
      if (ch2 != ICharacterScanner.EOF)
        if (isIdentifierStart(ch2.asInstanceOf[Char]))
          getIdentRest(scanner)
        else if (isOperatorPart(ch2.asInstanceOf[Char]) && ch2 != '\\')
          getOperatorRest(scanner)
        else
          scanner.noMatch()
      else
        scanner.noMatch()
    }
  }

  @tailrec
  private def getOperatorRest(scanner: RewindableScanner): IToken = {
    val ch = scanner.read()
    if (ch == '\'')
      scanner.noMatch()
    else if (ch == '/') {
      val ch1 = scanner.peek
      if (ch1 == '/' || ch1 == '*') {
        scanner.unread()
        successToken
      } else
        getOperatorRest(scanner)
    } else if (ch != ICharacterScanner.EOF && isOperatorPart(ch.asInstanceOf[Char]))
      getOperatorRest(scanner)
    else {
      scanner.unread()
      successToken
    }
  }

  @tailrec
  private def getIdentRest(scanner: RewindableScanner): IToken = {
    val ch = scanner.read()
    if (ch == '\'')
      scanner.noMatch()
    else if (ch == '_')
      getIdentOrOperatorRest(scanner)
    else if (Character.isUnicodeIdentifierPart(ch) || ch == '$')
      getIdentRest(scanner)
    else {
      scanner.unread()
      successToken
    }
  }

  private def getIdentOrOperatorRest(scanner: RewindableScanner): IToken = {
    val ch = scanner.peek
    if (ch == ICharacterScanner.EOF)
      successToken
    else if (isIdentifierStart(ch.asInstanceOf[Char]))
      getIdentRest(scanner)
    else if (isOperatorPart(ch.asInstanceOf[Char]))
      getOperatorRest(scanner)
    else
      successToken
  }

}

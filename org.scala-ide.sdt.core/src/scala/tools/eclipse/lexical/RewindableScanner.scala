package scala.tools.eclipse.lexical

import org.eclipse.jface.text.rules._

class RewindableScanner(scanner: ICharacterScanner) extends ICharacterScanner {
  private var readCount = 0

  def read(): Int = {
    readCount += 1
    scanner.read()
  }

  def unread(): Unit = {
    readCount -= 1
    scanner.unread()
  }

  def rewind() = {
    for (i <- 1 to readCount)
      scanner.unread()
    readCount = 0
  }

  def peek: Int = {
    val result = scanner.read()
    scanner.unread()
    result
  }

  def noMatch(): IToken = {
    rewind()
    Token.UNDEFINED
  }

  def getColumn = scanner.getColumn

  def getLegalLineDelimiters = scanner.getLegalLineDelimiters
}

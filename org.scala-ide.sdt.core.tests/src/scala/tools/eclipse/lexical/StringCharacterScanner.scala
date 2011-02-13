package scala.tools.eclipse.lexical
import org.eclipse.jface.text.rules._

class StringCharacterScanner(s: String) extends ICharacterScanner {
    private var index = 0

    def read(): Int = {
      val result = if (index >= s.length)
        ICharacterScanner.EOF
      else
        s(index)
      index += 1
      result
    }

    def unread() { index -= 1 }

    val getColumn = -1

    val getLegalLineDelimiters = Array[Array[Char]]()

    def remainder = s.substring(index)

    def consumed = s.substring(0, Math.min(index, s.length))

  }

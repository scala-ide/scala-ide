package scala.tools.eclipse.lexical

import scala.tools.eclipse.properties.syntaxcolouring.ScalaSyntaxClass

import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text.rules.{ ICharacterScanner, IToken, IWordDetector, Token, WordRule }

/**
 * Scans Scaladoc content and tokenizes it into different style ranges.
 *
 * This Scanner assumes that anything passed to it is already Scaladoc - it does
 * not search for Scaladoc content inside of arbitrary passed input.
 */
class ScaladocTokenScanner(
    scaladocClass: ScalaSyntaxClass,
    annotationClass: ScalaSyntaxClass,
    macroClass: ScalaSyntaxClass,
    taskTagClass: ScalaSyntaxClass,
    preferenceStore: IPreferenceStore,
    javaPreferenceStore: IPreferenceStore
) extends ScalaCommentScanner(scaladocClass, taskTagClass, preferenceStore, javaPreferenceStore) {

  private val annotationRule = new ScaladocWordRule(new AnnotationDetector, getToken(scaladocClass), getToken(annotationClass))
  private val macroRule = new ScaladocWordRule(new MacroDetector, getToken(scaladocClass), getToken(macroClass))

  appendRules(Array(annotationRule, macroRule))
}

/**
 * Extends a normal `WordRule` with behavior needed for Scaladoc.
 */
private class ScaladocWordRule(
    wordDetector: IWordDetector,
    scaladocToken: IToken,
    defaultToken: IToken
) extends WordRule(wordDetector, defaultToken) {

  /**
   * Changes the behavior of a `WordRule` in so far that occurrences of characters
   * that determine a word start (means `WordDetector.isWordStart` returns `true`)
   * are not treated as a special token in the following cases:
   *
   * - The character stands for its own, which means that `WordDetector.isWordPart`
   *   returns `false` for both the characters predecessor and successor.
   *   '''Example''': "a @ b" is treated as a normal comment token where "@" is word start
   *   and "a" and "b" are word parts, but whitespace is no word part.
   *
   * - The characters predecessor and successor both return `true` for
   *   `WordDetector.isWordPart`.
   *   '''Example''': "a@b" is treated as a normal comment token where "@" is word start
   *   and "a" and "b" are word parts.
   */
  override def evaluate(scanner: ICharacterScanner): IToken = {
    scanner.unread()
    val preToken = scanner.read().toChar
    val curToken = scanner.read().toChar

    if (wordDetector.isWordStart(curToken) && !wordDetector.isWordPart(preToken)) {
      val succToken = scanner.read().toChar
      scanner.unread()

      if (!wordDetector.isWordPart(succToken))
        scaladocToken
      else {
        scanner.unread()
        super.evaluate(scanner)
      }
    } else {
      scanner.unread()
      Token.UNDEFINED
    }
  }
}

private class AnnotationDetector extends IdentifierDetector {
  override def isWordStart(c: Char) = c == '@'
}

private class MacroDetector extends IdentifierDetector {
  override def isWordStart(c: Char) = c == '$'
}
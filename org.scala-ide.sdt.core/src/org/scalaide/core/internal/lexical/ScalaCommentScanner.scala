package org.scalaide.core.internal.lexical

import org.scalaide.ui.syntax.ScalaSyntaxClass
import org.eclipse.jdt.core.JavaCore
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text.rules.ICharacterScanner
import org.eclipse.jface.text.rules.IRule
import org.eclipse.jface.text.rules.IToken
import org.eclipse.jface.text.rules.IWordDetector
import org.eclipse.jface.text.rules.RuleBasedScanner
import org.eclipse.jface.text.rules.Token
import org.eclipse.jface.util.PropertyChangeEvent

/**
 * This class works nearly the same way as [[org.eclipse.jdt.internal.ui.text.JavaCommentScanner]],
 * which can't be directly used because it doesn't extend [[AbstractScalaScanner]].
 * It was also not possible to extend `JavaCommentScanner` directly because it loads
 * all color properties from JDT and not from SDT.
 *
 * This class now uses all color properties from SDT but loads the task tags from JDT.
 * This means that all task tags configured for the Java editor also work for the Scala
 * editor and vice versa.
 *
 * @param syntaxClass
 *        the syntax class for comment content. All characters inside a comment
 *        that are not highlighted in a special way are highlighted with this class
 * @param colorManager
 *        the color manager from SDT
 * @param preferenceStore
 *        the preference store from SDT
 * @param javaPreferenceStore
 *        the preference store from JDT
 */
class ScalaCommentScanner(
    val preferenceStore: IPreferenceStore,
    syntaxClass: ScalaSyntaxClass,
    taskTagClass: ScalaSyntaxClass)
      extends RuleBasedScanner with AbstractScalaScanner {

  private val wordMatcher = {
    val taskTags = preferenceStore.getString(JavaCore.COMPILER_TASK_TAGS)
    val isCaseSensitive = preferenceStore.getString(JavaCore.COMPILER_TASK_CASE_SENSITIVE) == JavaCore.ENABLED
    val wm = new WordMatcher
    val cwr = new CombinedWordRule(new IdentifierDetector, wm)

    wm.isCaseSensitive = isCaseSensitive
    addTaskTags(wm, taskTags, getToken(taskTagClass))
    setRules(Array(cwr))
    setDefaultReturnToken(getToken(syntaxClass))

    wm
  }

  /**
   * Append the passed rules to the existing ones.
   *
   * This method differ from `setRules`, which overwrites all existing rules with
   * the passed ones.
   */
  protected def appendRules(rules: Array[IRule]) {
    if (fRules == null)
      fRules = rules.clone()
    else
      fRules ++= rules.clone()
  }

  /**
   * Overwritten because it needs to listen to task tag changes stored in JDT.
   */
  override def adaptToPreferenceChange(event: PropertyChangeEvent) {
    super.adaptToPreferenceChange(event)

    event.getProperty() match {
      case JavaCore.COMPILER_TASK_TAGS =>
        wordMatcher.clearWords()
        addTaskTags(wordMatcher, event.getNewValue().toString(), getToken(taskTagClass))
      case JavaCore.COMPILER_TASK_CASE_SENSITIVE =>
        wordMatcher.isCaseSensitive = event.getNewValue() == JavaCore.ENABLED
      case _ =>
    }
  }

  /*
   * Task tags are stored by JDT as a comma separated string. This function decodes
   * and stores them correctly.
   */
  private def addTaskTags(wordMatcher: WordMatcher, tags: String, token: IToken) {
    tags.split(",") foreach { w =>
      wordMatcher.addWord(w, token)
    }
  }
}

/**
 * Can detect words with help of an [[org.eclipse.jface.text.rules.IWordDetector]],
 * named as `detector` and associates them to a token given by a [[WordMatcher]],
 * named as `matcher`.
 *
 * This word rule allows a word detector to be shared among different word matchers.
 * Its up to the word matchers to decide if a word matches and, in this case,
 * which token is associated with that word.
 *
 * If `matcher` doesn't provide a token for a given word then [[org.eclipse.jface.text.rules.Token.UNDEFINED]]
 * is used.
 *
 * This class is inspired by [[org.eclipse.jdt.internal.ui.text.CombinedWordRule]],
 * which can't be reused because it triggers the loading of UI classes and therefore
 * would prevent testing.
 */
private class CombinedWordRule(
    detector: IWordDetector,
    matcher: WordMatcher
) extends IRule {

  def evaluate(scanner: ICharacterScanner): IToken = {
    val wordStart = scanner.read().toChar
    if (detector.isWordStart(wordStart)) {
      val word = wordStart +: Iterator
          .continually(scanner.read().toChar)
          .takeWhile(c => c != ICharacterScanner.EOF && detector.isWordPart(c))
          .mkString
      scanner.unread()
      val tok = matcher.evaluate(word)
      if (!tok.isUndefined()) tok
      else {
        for (_ <- word) scanner.unread()
        Token.UNDEFINED
      }
    } else {
      scanner.unread()
      Token.UNDEFINED
    }
  }
}

/**
 * Matches words that are associated with an [[org.eclipse.jface.text.rules.IToken]].
 *
 * The idea of this class came from [[org.eclipse.jdt.internal.ui.text.JavaCommentScanner.TaskTagMatcher]],
 * which can't be reused because it is private.
 *
 * Internally this class relies on mutable state because it may be shared with
 * [[org.eclipse.jface.text.rules.RuleBasedScanner]] and its subclasses.
 */
private class WordMatcher {

  private var words = Map[String, IToken]()

  /**
   * Determines if task tags should be highlighted case sensitive. Because this value
   * is read for each character a comment contains, it is more efficient to cache it
   * here instead of reading it on each access from the slower preference store.
   */
  var isCaseSensitive = true

  /**
   * Adds a given word associated with a token to this matcher.
   *
   * If [[isCaseSensitve]] is not set the word is stored in a way that case
   * sensitivity doesn't matter.
   */
  def addWord(word: String, token: IToken) {
    words += (if (isCaseSensitive) word else word.toUpperCase()) -> token
  }

  /**
   * Clears all the containing words.
   */
  def clearWords() {
    words = Map()
  }

  /**
   * Checks if a given word can be matched by this matcher and returns the token
   * associated with this word if that is the case.
   *
   * The value of [[isCaseSensitve]] is considered during the word check.
   */
  def evaluate(word: String): IToken =
    words.getOrElse(if (isCaseSensitive) word else word.toUpperCase(), Token.UNDEFINED)
}

/**
 * This is more or less a direct copy of [[org.eclipse.jdt.internal.ui.text.JavaCommentScanner.AtJavaIdentifierDetector]],
 * which can't be reused because it is private.
 */
private class IdentifierDetector extends IWordDetector {

  private val specialSigns = """$@!%&*+-<=>?\^|~/""".toSet

  def isWordStart(c: Char) =
    Character.isJavaIdentifierStart(c) || specialSigns(c)

  def isWordPart(c: Char) =
    c != '$' && Character.isJavaIdentifierPart(c)
}

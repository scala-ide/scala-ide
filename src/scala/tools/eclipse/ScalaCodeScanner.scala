/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import java.{ util => ju }

import org.eclipse.core.runtime.Assert
import org.eclipse.jdt.core.JavaCore
import org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlightings
import org.eclipse.jdt.internal.ui.text.{ AbstractJavaScanner, CombinedWordRule, JavaWhitespaceDetector, JavaWordDetector, ISourceVersionDependent }

import org.eclipse.jdt.ui.PreferenceConstants
import org.eclipse.jdt.ui.text.{ IColorManager, IJavaColorConstants }
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.util.PropertyChangeEvent
import org.eclipse.jface.text.rules.{ ICharacterScanner, IRule, IToken, IWhitespaceDetector, IWordDetector, SingleLineRule, Token, WhitespaceRule }

object ScalaCodeScanner {

  /**
   * Rule to detect Scala operators.
   *
   * @since 3.0
   */
  class OperatorRule(fToken : IToken) extends IRule {

    /** Scala operators */
    val JAVA_OPERATORS = Array(';', '.', '=', '/', '\\', '+', '-', '*', '<', '>', ':', '?', '!', ',', '|', '&', '^', '%', '~')

    /**
     * Is this character an operator character?
     *
     * @param character Character to determine whether it is an operator character
     * @return <code>true</code> iff the character is an operator, <code>false</code> otherwise.
     */
    def isOperator(character : Char) = JAVA_OPERATORS.contains(character)

    /*
     * @see org.eclipse.jface.text.rules.IRule#evaluate(org.eclipse.jface.text.rules.ICharacterScanner)
     */
    override def evaluate(scanner : ICharacterScanner) : IToken = {
      var character : Char = scanner.read.toChar
      if (isOperator(character)) {
        do {
          character = scanner.read.toChar
        } while (isOperator(character))
          
        scanner.unread
        fToken
      } else {
        scanner.unread
        Token.UNDEFINED
      }
    }
  }

  /**
   * Rule to detect java brackets.
   *
   * @since 3.3
   */
  class BracketRule(fToken : IToken) extends IRule {

    /** Java brackets */
    val JAVA_BRACKETS = Array('(', ')', '{', '}', '[', ']')

    /**
     * Is this character a bracket character?
     *
     * @param character Character to determine whether it is a bracket character
     * @return <code>true</code> iff the character is a bracket, <code>false</code> otherwise.
     */
    def isBracket(character : Char) = JAVA_BRACKETS.contains(character)

    /*
     * @see org.eclipse.jface.text.rules.IRule#evaluate(org.eclipse.jface.text.rules.ICharacterScanner)
     */
    override def evaluate(scanner : ICharacterScanner) : IToken = {
      var character : Char = scanner.read.toChar
      if (isBracket(character)) {
        do {
          character = scanner.read.toChar
        } while (isBracket(character))
          
        scanner.unread
        fToken
      } else {
        scanner.unread
        Token.UNDEFINED
      }
    }
  }

  class VersionedWordMatcher(fDefaultToken : IToken, fVersion : String, currentVersion : String) extends CombinedWordRule.WordMatcher with ISourceVersionDependent {
    private var fIsVersionMatch : Boolean = _ 
    setSourceVersion(currentVersion);
    
    /*
     * @see org.eclipse.jdt.internal.ui.text.ISourceVersionDependent#setSourceVersion(java.lang.String)
     */
    override def setSourceVersion(version : String) {
      fIsVersionMatch = fVersion.compareTo(version) <= 0
    }

    /*
     * @see org.eclipse.jdt.internal.ui.text.CombinedWordRule.WordMatcher#evaluate(org.eclipse.jface.text.rules.ICharacterScanner, org.eclipse.jdt.internal.ui.text.CombinedWordRule.CharacterBuffer)
     */
    override def evaluate(scanner : ICharacterScanner, word : CombinedWordRule.CharacterBuffer) : IToken = {
      val token = super.evaluate(scanner, word)

      if (fIsVersionMatch || token.isUndefined())
        token
      else
        fDefaultToken
    }
  }

  object AnnotationRule {
    /**
     * A resettable scanner supports marking a position in a scanner and
     * unreading back to the marked position.
     */
    class ResettableScanner(fDelegate : ICharacterScanner) extends ICharacterScanner {
      private var fReadCount : Int = _
      mark

      def getColumn = fDelegate.getColumn
      def getLegalLineDelimiters = fDelegate.getLegalLineDelimiters

      def read : Int = {
        var ch = fDelegate.read
        if (ch != ICharacterScanner.EOF)
          fReadCount += 1
        ch
      }

      def unread {
        if (fReadCount > 0)
          fReadCount -= 1
        fDelegate.unread
      }

      /**
       * Marks an offset in the scanned content.
       */
      def mark {
        fReadCount = 0
      }

      /**
       * Resets the scanner to the marked position.
       */
      def reset {
        while (fReadCount > 0)
          unread

        while (fReadCount < 0)
          read
      }
    }
  }
  
  /**
   * An annotation rule matches the '@' symbol, any following whitespace and
   * optionally a following <code>interface</code> keyword.
   *
   * It does not match if there is a comment between the '@' symbol and
   * the identifier. See https://bugs.eclipse.org/bugs/show_bug.cgi?id=82452
   *
   * @since 3.1
   */
  class AnnotationRule(fInterfaceToken : IToken, fAtToken : Token, fVersion : String, currentVersion : String) extends IRule with ISourceVersionDependent {
    import AnnotationRule._
    
    private val fWhitespaceDetector= new JavaWhitespaceDetector
    private val fWordDetector = new JavaWordDetector
    private var fIsVersionMatch : Boolean = _

    setSourceVersion(currentVersion)

    /*
     * @see org.eclipse.jface.text.rules.IRule#evaluate(org.eclipse.jface.text.rules.ICharacterScanner)
     */
    def evaluate(scanner : ICharacterScanner) : IToken = {
      if (!fIsVersionMatch)
        Token.UNDEFINED
      else {
        val resettable= new ResettableScanner(scanner)
        if (resettable.read == '@')
          readAnnotation(resettable)
        else {
          resettable.reset
          Token.UNDEFINED;
        }
      }
    }

    def readAnnotation(scanner : ResettableScanner) : IToken = {
      scanner.mark
      skipWhitespace(scanner)
      if (readInterface(scanner)) {
        fInterfaceToken
      } else {
        scanner.reset
        fAtToken
      }
    }

    def readInterface(scanner : ICharacterScanner) : Boolean = {
      var ch = scanner.read.toChar
      var i = 0
      while (i < INTERFACE.length && INTERFACE.charAt(i) == ch) {
        i += 1
        ch = scanner.read.toChar
      }
      if (i < INTERFACE.length)
        false
      else if (fWordDetector.isWordPart(ch))
        false
      else {
        if (ch != ICharacterScanner.EOF)
          scanner.unread
        true
      }
    }

    def skipWhitespace(scanner : ICharacterScanner) : Boolean = {
      while (fWhitespaceDetector.isWhitespace(scanner.read.toChar)) {
        // do nothing
      }

      scanner.unread
      true
    }

    /*
     * @see org.eclipse.jdt.internal.ui.text.ISourceVersionDependent#setSourceVersion(java.lang.String)
     */
    def setSourceVersion(version : String) {
      fIsVersionMatch = fVersion.compareTo(version) <= 0 
    }
  }

  private val SOURCE_VERSION = "2.8.0"

  private val fgKeywords = Array(
    "abstract",
    "case", "catch", "class",
    "def", "do",
    "else", "extends",
    "final", "finally", "for", "forSome",
    "if", "implicit", "import",
    "lazy",
    "match",
    "new",
    "object", "override",
    "package", "private", "protected",
    "requires",
    "sealed", "super",
    "this", "throw", "trait", "try", "type",
    "val", "var",
    "while", "with",
    "yield"
  )

  private val INTERFACE = "interface"
  
  private val RETURN = "return"

  private val fgTypes = Array(
    "Unit", "Boolean", "Char", "Byte", "Short", "Int", "Long", "Float", "Double",
    "Array", "Any", "AnyRef", "AnyVal", "Nothing", "Null", "NotNull")

  private val fgConstants = Array("false", "null", "true")

  private val ANNOTATION_BASE_KEY = PreferenceConstants.EDITOR_SEMANTIC_HIGHLIGHTING_PREFIX + SemanticHighlightings.ANNOTATION
  private val ANNOTATION_COLOR_KEY= ANNOTATION_BASE_KEY + PreferenceConstants.EDITOR_SEMANTIC_HIGHLIGHTING_COLOR_SUFFIX

  private val fgTokenProperties = Array(
    IJavaColorConstants.JAVA_KEYWORD,
    IJavaColorConstants.JAVA_STRING,
    IJavaColorConstants.JAVA_DEFAULT,
    IJavaColorConstants.JAVA_KEYWORD_RETURN,
    IJavaColorConstants.JAVA_OPERATOR,
    IJavaColorConstants.JAVA_BRACKET,
    ANNOTATION_COLOR_KEY
  )
}

/**
 * A Scala code scanner.
 */
class ScalaCodeScanner(manager : IColorManager, store : IPreferenceStore) extends AbstractJavaScanner(manager, store) {
  import ScalaCodeScanner._
  
  private def fVersionDependentRules = new ju.ArrayList[IRule with ISourceVersionDependent](3)

  initialize

  protected def getTokenProperties = fgTokenProperties

  protected def createRules : ju.List[IRule] = {

    val rules = new ju.ArrayList[IRule]

    // Add rule for character constants.
    var token = getToken(IJavaColorConstants.JAVA_STRING)
    rules.add(new SingleLineRule("'", "'", token, '\\'))

    // Add generic whitespace rule.
    rules.add(new WhitespaceRule(new JavaWhitespaceDetector))

    val version = getPreferenceStore.getString(SOURCE_VERSION)

    // Add JLS3 rule for /@\s*interface/ and /@\s*\w+/
    token = getToken(ANNOTATION_COLOR_KEY)
    val atInterfaceRule = new AnnotationRule(getToken(IJavaColorConstants.JAVA_KEYWORD), token, JavaCore.VERSION_1_5, version)
    rules.add(atInterfaceRule)
    fVersionDependentRules.add(atInterfaceRule)

    // Add word rule for new keywords, 4077
    val wordDetector = new JavaWordDetector
    token = getToken(IJavaColorConstants.JAVA_DEFAULT)
    val combinedWordRule = new CombinedWordRule(wordDetector, token)

    // Add rule for operators
    token = getToken(IJavaColorConstants.JAVA_OPERATOR)
    rules.add(new OperatorRule(token))

    // Add rule for brackets
    token = getToken(IJavaColorConstants.JAVA_BRACKET)
    rules.add(new BracketRule(token))

    // Add word rule for keyword 'return'.
    val returnWordRule = new CombinedWordRule.WordMatcher
    token = getToken(IJavaColorConstants.JAVA_KEYWORD_RETURN)
    returnWordRule.addWord(RETURN, token)  
    combinedWordRule.addWordMatcher(returnWordRule)

    // Add word rule for keywords, types, and constants.
    val wordRule = new CombinedWordRule.WordMatcher
    token = getToken(IJavaColorConstants.JAVA_KEYWORD)
    for (kwd <- fgKeywords)
      wordRule.addWord(kwd, token)
    for (typ <- fgTypes)
      wordRule.addWord(typ, token)
    for (cnst <- fgConstants)
      wordRule.addWord(cnst, token)

    combinedWordRule.addWordMatcher(wordRule)

    rules.add(combinedWordRule)

    setDefaultReturnToken(getToken(IJavaColorConstants.JAVA_DEFAULT))
    rules
  }
  
  /*
   * @see org.eclipse.jdt.internal.ui.text.AbstractJavaScanner#getBoldKey(java.lang.String)
   */
  override protected def getBoldKey(colorKey : String) : String = {
    if (ANNOTATION_COLOR_KEY == colorKey)
      ANNOTATION_BASE_KEY + PreferenceConstants.EDITOR_SEMANTIC_HIGHLIGHTING_BOLD_SUFFIX
    else
      super.getBoldKey(colorKey)
  }
  
  /*
   * @see org.eclipse.jdt.internal.ui.text.AbstractJavaScanner#getItalicKey(java.lang.String)
   */
  override protected def getItalicKey(colorKey : String) : String = {
    if (ANNOTATION_COLOR_KEY == colorKey)
      ANNOTATION_BASE_KEY + PreferenceConstants.EDITOR_SEMANTIC_HIGHLIGHTING_ITALIC_SUFFIX
    else
      super.getItalicKey(colorKey)
  }
  
  /*
   * @see org.eclipse.jdt.internal.ui.text.AbstractJavaScanner#getStrikethroughKey(java.lang.String)
   */
  override protected def getStrikethroughKey(colorKey : String) : String = {
    if (ANNOTATION_COLOR_KEY == colorKey)
      ANNOTATION_BASE_KEY + PreferenceConstants.EDITOR_SEMANTIC_HIGHLIGHTING_STRIKETHROUGH_SUFFIX
    else
      super.getStrikethroughKey(colorKey)
  }
  
  /*
   * @see org.eclipse.jdt.internal.ui.text.AbstractJavaScanner#getUnderlineKey(java.lang.String)
   */
  override protected def getUnderlineKey(colorKey : String) : String = {
    if (ANNOTATION_COLOR_KEY == colorKey)
      ANNOTATION_BASE_KEY + PreferenceConstants.EDITOR_SEMANTIC_HIGHLIGHTING_UNDERLINE_SUFFIX
    else 
      super.getUnderlineKey(colorKey)
  }

  /*
   * @see AbstractJavaScanner#affectsBehavior(PropertyChangeEvent)
   */
  override def affectsBehavior(event : PropertyChangeEvent) : Boolean =
    event.getProperty == SOURCE_VERSION || super.affectsBehavior(event)

  /*
   * @see AbstractJavaScanner#adaptToPreferenceChange(PropertyChangeEvent)
   */
  override def adaptToPreferenceChange(event : PropertyChangeEvent) {
    import scala.collection.JavaConversions._
    if (event.getProperty == SOURCE_VERSION) {
      event.getNewValue match {
        case s : String =>
          for (dependent <- fVersionDependentRules)
            dependent.setSourceVersion(s)
      }
    } else if (super.affectsBehavior(event))
      super.adaptToPreferenceChange(event)
  }
}

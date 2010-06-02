/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.lexical

import org.eclipse.jface.text.rules.EndOfLineRule
import org.eclipse.jface.text.rules.ICharacterScanner
import org.eclipse.jface.text.rules.IPredicateRule
import org.eclipse.jface.text.rules.IToken
import org.eclipse.jface.text.rules.IWordDetector
import org.eclipse.jface.text.rules.MultiLineRule
import org.eclipse.jface.text.rules.PatternRule
import org.eclipse.jface.text.rules.RuleBasedPartitionScanner
import org.eclipse.jface.text.rules.SingleLineRule
import org.eclipse.jface.text.rules.Token
import org.eclipse.jface.text.rules.WordRule

import org.eclipse.jdt.ui.text.IJavaPartitions
import org.eclipse.jdt.ui.text.IJavaPartitions._

/**
 * This scanner is inspired by the JavaPartitionScanner from the JDT.
 */
class ScalaPartitionScanner extends RuleBasedPartitionScanner with IJavaPartitions {

  /**
   * Detector for empty comments.
   */
  private class EmptyCommentDetector extends IWordDetector {

    /*
     * @see IWordDetector#isWordStart
     */
    def isWordStart(c : Char) : Boolean = (c == '/')

    /*
     * @see IWordDetector#isWordPart
     */
    def isWordPart(c : Char) : Boolean = (c == '*' || c == '/')
  }


  /**
   * Word rule for empty comments.
   */
  class EmptyCommentRule(
      val successToken : IToken
    ) extends WordRule(new EmptyCommentDetector()) with IPredicateRule {

    addWord("/**/", successToken)

    /*
     * @see IPredicateRule#evaluate(ICharacterScanner, boolean)
     */
    def evaluate(scanner : ICharacterScanner, resume : Boolean) : IToken = evaluate(scanner)
    
    def getSuccessToken : IToken = successToken
  }


  /**
   * Creates the partitioner and sets up the appropriate rules.
   */
  val string = new Token(JAVA_STRING)
  val character = new Token(JAVA_CHARACTER)
  val javaDoc = new Token(JAVA_DOC)
  val multiLineComment = new Token(JAVA_MULTI_LINE_COMMENT)
  val singleLineComment = new Token(JAVA_SINGLE_LINE_COMMENT)

  val result = Array[IPredicateRule](
    // Single line comments.
    new EndOfLineRule("//", singleLineComment) with NoResumeRule,

    // Must come before the single line string rule
    new MultilineStringLiteralRule(string),
    
    // Strings.
    new SingleLineRule("\"", "\"", string, '\\') with NoResumeRule,

    // Must occur before the character literal rule
    new SymbolRule(string),

    // Character constants.
    new SingleLineRule("'", "'", character, '\\') with NoResumeRule,

    new MultilineCommentRule(javaDoc, scalaDoc = true),
    new MultilineCommentRule(multiLineComment, scalaDoc = false)
  )

  setPredicateRules(result)
}
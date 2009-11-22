/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

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

  val tripleQuotes = "\"" * 3
    
  val result = Array[IPredicateRule](
    // Single line comments.
    new EndOfLineRule("//", singleLineComment),

    // Triple quoted strings
    new MultiLineRule(tripleQuotes, tripleQuotes, string, '\\'),
    
    // Strings.
    new SingleLineRule("\"", "\"", string, '\\'),

    // Character constants.
    new SingleLineRule("'", "'", character, '\\'),

    // Special case word rule.
    new EmptyCommentRule(multiLineComment),

    // Add rules for multi-line comments and javadoc.
    new MultiLineRule("/**", "*/", javaDoc),
    new MultiLineRule("/*", "*/", multiLineComment)
  )

  setPredicateRules(result)
}
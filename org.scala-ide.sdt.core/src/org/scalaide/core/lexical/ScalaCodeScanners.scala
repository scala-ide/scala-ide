package org.scalaide.core.lexical

import org.eclipse.jdt.ui.text.IJavaPartitions
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text.IDocument
import org.eclipse.ui.texteditor.ChainedPreferenceStore
import org.scalaide.core.internal.lexical.ScalaCodeScanner
import org.scalaide.core.internal.lexical.ScalaCodeTokenizerScalariformBased
import org.scalaide.core.internal.lexical.ScalaCommentScanner
import org.scalaide.core.internal.lexical.ScaladocTokenScanner
import org.scalaide.core.internal.lexical.SingleTokenScanner
import org.scalaide.core.internal.lexical.StringTokenScanner
import org.scalaide.core.internal.lexical.XmlCDATAScanner
import org.scalaide.core.internal.lexical.XmlCommentScanner
import org.scalaide.core.internal.lexical.XmlPIScanner
import org.scalaide.core.internal.lexical.XmlTagScanner
import org.scalaide.ui.syntax.ScalaSyntaxClass
import org.scalaide.ui.syntax.{ScalaSyntaxClasses => SSC}

import scalariform.ScalaVersion
import scalariform.ScalaVersions

/** Entry point to the Scala code scanners.
 *
 *  Code scanners are made to work on partitions (see [[ScalaCodePartition]]). They parse the content of partitions, and
 *  return a lists of 'token'. A token represent anything remarkable in the code: keyword, braces, id, symbols, ...
 *
 *  The type of the tokens returned by the Scala scanners is defined in [[org.scalaide.ui.syntax.ScalaSyntaxClasses]].
 *
 *  @see org.eclipse.jface.text.rules.ITokenScanner
 *  @see org.scalaide.core.lexical.ScalaCodePartitioner
 */
object ScalaCodeScanners {

  @deprecated("Use the overloaded variant instead", "4.0")
  def codeHighlightingScanners(scalaPreferenceStore: IPreferenceStore, javaPreferenceStore: IPreferenceStore): Map[String, AbstractScalaScanner] = {
    codeHighlightingScanners(new ChainedPreferenceStore(Array(scalaPreferenceStore, javaPreferenceStore)))
  }

  /** Returns a map of all code scanners for Scala code, associated to the partition id.
   */
  def codeHighlightingScanners(combinedPreferenceStore: IPreferenceStore): Map[String, AbstractScalaScanner] =
    Map(
      IDocument.DEFAULT_CONTENT_TYPE -> scalaCodeScanner(combinedPreferenceStore, ScalaVersions.DEFAULT),
      IJavaPartitions.JAVA_DOC -> scaladocScanner(combinedPreferenceStore),
      ScalaPartitions.SCALADOC_CODE_BLOCK -> scaladocCodeBlockScanner(combinedPreferenceStore),
      IJavaPartitions.JAVA_SINGLE_LINE_COMMENT -> scalaSingleLineCommentScanner(combinedPreferenceStore),
      IJavaPartitions.JAVA_MULTI_LINE_COMMENT -> scalaMultiLineCommentScanner(combinedPreferenceStore),
      IJavaPartitions.JAVA_STRING -> stringScanner(combinedPreferenceStore),
      IJavaPartitions.JAVA_CHARACTER -> characterScanner(combinedPreferenceStore),
      ScalaPartitions.SCALA_MULTI_LINE_STRING -> multiLineStringScanner(combinedPreferenceStore),
      ScalaPartitions.XML_TAG -> xmlTagScanner(combinedPreferenceStore),
      ScalaPartitions.XML_COMMENT -> xmlCommentScanner(combinedPreferenceStore),
      ScalaPartitions.XML_CDATA -> xmlCDATAScanner(combinedPreferenceStore),
      ScalaPartitions.XML_PCDATA -> xmlPCDATAScanner(combinedPreferenceStore),
      ScalaPartitions.XML_PI -> xmlPIScanner(combinedPreferenceStore))

  /** Creates a code scanner which returns a single token for the configured region.
   */
  def singleTokenScanner(preferenceStore: IPreferenceStore, syntaxClass: ScalaSyntaxClass): AbstractScalaScanner =
    new SingleTokenScanner(preferenceStore, syntaxClass)

  /** Creates a code scanner for plain Scala code.
   */
  def scalaCodeScanner(preferenceStore: IPreferenceStore, scalaVersion: ScalaVersion): AbstractScalaScanner =
    new ScalaCodeScanner(preferenceStore, scalaVersion)

  /** Creates a code tokenizer for plain Scala code.
   *
   *  This tokenizer has a richer interface, but is not compatible with the Eclipse [[org.eclipse.jface.text.rules.ITokenScanner]].
   *
   *  @see ScalaCodeTokenizer
   */
  def scalaCodeTokenizer(scalaVersion: ScalaVersion): ScalaCodeTokenizer =
    new ScalaCodeTokenizerScalariformBased(scalaVersion)

  private def scalaSingleLineCommentScanner(preferenceStore: IPreferenceStore): AbstractScalaScanner =
    new ScalaCommentScanner(preferenceStore, SSC.SINGLE_LINE_COMMENT, SSC.TASK_TAG)

  private def scalaMultiLineCommentScanner(preferenceStore: IPreferenceStore): AbstractScalaScanner =
    new ScalaCommentScanner(preferenceStore, SSC.MULTI_LINE_COMMENT, SSC.TASK_TAG)

  private def scaladocScanner(preferenceStore: IPreferenceStore): AbstractScalaScanner =
    new ScaladocTokenScanner(preferenceStore, SSC.SCALADOC, SSC.SCALADOC_ANNOTATION, SSC.SCALADOC_MACRO, SSC.TASK_TAG)

  private def scaladocCodeBlockScanner(preferenceStore: IPreferenceStore): AbstractScalaScanner =
    new SingleTokenScanner(preferenceStore, SSC.SCALADOC_CODE_BLOCK)

  private def multiLineStringScanner(preferenceStore: IPreferenceStore): AbstractScalaScanner =
    new SingleTokenScanner(preferenceStore, SSC.MULTI_LINE_STRING)

  private def xmlPCDATAScanner(preferenceStore: IPreferenceStore): AbstractScalaScanner =
    new SingleTokenScanner(preferenceStore, SSC.DEFAULT)

  private def stringScanner(preferenceStore: IPreferenceStore): AbstractScalaScanner =
    new StringTokenScanner(preferenceStore, SSC.ESCAPE_SEQUENCE, SSC.STRING)

  private def characterScanner(preferenceStore: IPreferenceStore): AbstractScalaScanner =
    new StringTokenScanner(preferenceStore, SSC.ESCAPE_SEQUENCE, SSC.CHARACTER)

  private def xmlTagScanner(preferenceStore: IPreferenceStore): AbstractScalaScanner =
    new XmlTagScanner(preferenceStore)

  private def xmlCommentScanner(preferenceStore: IPreferenceStore): AbstractScalaScanner =
    new XmlCommentScanner(preferenceStore)

  private def xmlCDATAScanner(preferenceStore: IPreferenceStore): AbstractScalaScanner =
    new XmlCDATAScanner(preferenceStore)

  private def xmlPIScanner(preferenceStore: IPreferenceStore): AbstractScalaScanner =
    new XmlPIScanner(preferenceStore)

}

/** A tokenizer for plain Scala code. Like the code scanners in [[ScalaCodeScanners]], but returns tokens with a richer interface.
 */
trait ScalaCodeTokenizer {

  import ScalaCodeTokenizer.Token

  /** Tokenizes a string of Scala code.
   *
   *  @param contents the string to tokenize
   *  @param offset If `contents` is a snippet within a larger document, use `offset` to indicate it `contents` offset within the larger document so that resultant tokens are properly positioned with respect to the larger document.
   *  @return an sequence of the tokens for the given string
   */
  def tokenize(contents: String, offset: Int = 0): IndexedSeq[Token]

}

object ScalaCodeTokenizer {

  /** A Scala token.
   *
   *  @param offset the position of the first character of the token
   *  @param length the length of the token
   *  @param syntaxClass the class of the token
   */
  case class Token(offset: Int, length: Int, syntaxClass: ScalaSyntaxClass)
}

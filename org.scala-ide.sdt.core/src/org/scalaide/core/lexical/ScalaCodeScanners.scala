package org.scalaide.core.lexical

import org.eclipse.jface.preference.IPreferenceStore
import org.scalaide.core.internal.lexical.ScalaCodeScanner
import scalariform.ScalaVersion
import org.scalaide.ui.syntax.ScalaSyntaxClass
import org.scalaide.core.internal.lexical.ScalaCommentScanner
import org.scalaide.core.internal.lexical.ScaladocTokenScanner
import org.scalaide.core.internal.lexical.SingleTokenScanner
import org.scalaide.core.internal.lexical.StringTokenScanner
import org.scalaide.core.internal.lexical.XmlCommentScanner
import org.scalaide.core.internal.lexical.XmlCDATAScanner
import org.scalaide.core.internal.lexical.XmlPIScanner
import org.scalaide.ui.syntax.{ ScalaSyntaxClasses => SSC }
import scalariform.ScalaVersions
import org.eclipse.jface.text.IDocument
import org.eclipse.jdt.ui.text.IJavaPartitions
import org.scalaide.core.internal.lexical.XmlTagScanner
import org.scalaide.core.internal.lexical.ScalaCodeTokenizerScalariformBased

/** Entry point for code scanners related to Scala code.
 */
object ScalaCodeScanners {

  /** Returns a map of all code scanners for Scala code, associated to the partition id.
   */
  def codeHighlightingScanners(scalaPreferenceStore: IPreferenceStore, javaPreferenceStore: IPreferenceStore): Map[String, AbstractScalaScanner] =
    Map(
      IDocument.DEFAULT_CONTENT_TYPE -> scalaCodeScanner(scalaPreferenceStore, ScalaVersions.DEFAULT),
      IJavaPartitions.JAVA_DOC -> scaladocScanner(scalaPreferenceStore, javaPreferenceStore),
      ScalaPartitions.SCALADOC_CODE_BLOCK -> scaladocCodeBlockScanner(scalaPreferenceStore),
      IJavaPartitions.JAVA_SINGLE_LINE_COMMENT -> scalaSingleLineCommentScanner(scalaPreferenceStore, javaPreferenceStore),
      IJavaPartitions.JAVA_MULTI_LINE_COMMENT -> scalaMultiLineCommentScanner(scalaPreferenceStore, javaPreferenceStore),
      IJavaPartitions.JAVA_STRING -> stringScanner(scalaPreferenceStore),
      IJavaPartitions.JAVA_CHARACTER -> characterScanner(scalaPreferenceStore),
      ScalaPartitions.SCALA_MULTI_LINE_STRING -> multiLineStringScanner(scalaPreferenceStore),
      ScalaPartitions.XML_TAG -> xmlTagScanner(scalaPreferenceStore),
      ScalaPartitions.XML_COMMENT -> xmlCommentScanner(scalaPreferenceStore),
      ScalaPartitions.XML_CDATA -> xmlCDATAScanner(scalaPreferenceStore),
      ScalaPartitions.XML_PCDATA -> xmlPCDATAScanner(scalaPreferenceStore),
      ScalaPartitions.XML_PI -> xmlPIScanner(scalaPreferenceStore))

  /** Creates a code scanner which returns a single token for the configured region.
   */
  def singleTokenScanner(preferenceStore: IPreferenceStore, syntaxClass: ScalaSyntaxClass): AbstractScalaScanner =
    new SingleTokenScanner(preferenceStore, syntaxClass)

  /** Creates a code scanner for Scala code.
   */
  def scalaCodeScanner(preferenceStore: IPreferenceStore, scalaVersion: ScalaVersion): AbstractScalaScanner =
    new ScalaCodeScanner(preferenceStore, scalaVersion)

  /** Creates a code tokenizer for Scala code
   */
  def scalaCodeTokenizer(scalaVersion: ScalaVersion): ScalaCodeTokenizer =
    new ScalaCodeTokenizerScalariformBased(scalaVersion)

  private def scalaSingleLineCommentScanner(
    preferenceStore: IPreferenceStore,
    javaPreferenceStore: IPreferenceStore): AbstractScalaScanner =
    new ScalaCommentScanner(preferenceStore, javaPreferenceStore, SSC.SINGLE_LINE_COMMENT, SSC.TASK_TAG)

  private def scalaMultiLineCommentScanner(
    preferenceStore: IPreferenceStore,
    javaPreferenceStore: IPreferenceStore): AbstractScalaScanner =
    new ScalaCommentScanner(preferenceStore, javaPreferenceStore, SSC.MULTI_LINE_COMMENT, SSC.TASK_TAG)

  private def scaladocScanner(preferenceStore: IPreferenceStore, javaPreferenceStore: IPreferenceStore): AbstractScalaScanner =
    new ScaladocTokenScanner(preferenceStore, javaPreferenceStore, SSC.SCALADOC, SSC.SCALADOC_ANNOTATION, SSC.SCALADOC_MACRO, SSC.TASK_TAG)

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

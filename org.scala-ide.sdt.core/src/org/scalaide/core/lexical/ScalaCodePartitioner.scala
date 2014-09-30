package org.scalaide.core.lexical

import org.eclipse.jface.text.IDocumentPartitioner
import org.scalaide.core.internal.lexical.ScalaDocumentPartitioner
import org.scalaide.core.internal.lexical.ScalaPartitionTokeniser
import org.eclipse.jface.text.IDocumentPartitionerExtension
import org.eclipse.jface.text.ITypedRegion

/** Entry point to Scala sources partitioners.
 *
 *  A partitioner takes a complete Scala source, and returns regions of the different parts
 *  of the source like plain code, comments, strings, xml, ...
 *  The partition types are defined in [[org.scalaide.core.lexical.ScalaPartitions]] and [[org.eclipse.jdt.ui.text.IJavaPartitions]].
 *
 *  Usually, the partitions are then parsed by a token scanner to extract the different elements (keywords, symbols, ...)
 *
 *  @see org.eclipse.jface.text.IDocumentPartitioner
 *  @see org.scalaide.core.lexical.ScalaCodeScannners
 */
object ScalaCodePartitioner {

  /** Provides a document partitioner for Scala sources.
   */
  def documentPartitioner(conservative: Boolean = false): IDocumentPartitioner with IDocumentPartitionerExtension =
    new ScalaDocumentPartitioner(conservative)

  /** Partitions the given text as a Scala source.
   */
  def partition(text: String): List[ITypedRegion] = ScalaPartitionTokeniser.tokenise(text)

}
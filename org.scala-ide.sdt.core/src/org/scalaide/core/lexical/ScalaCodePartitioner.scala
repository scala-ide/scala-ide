package org.scalaide.core.lexical

import org.eclipse.jface.text.IDocumentPartitioner
import org.scalaide.core.internal.lexical.ScalaDocumentPartitioner
import org.scalaide.core.internal.lexical.ScalaPartitionTokeniser
import org.eclipse.jface.text.IDocumentPartitionerExtension
import org.eclipse.jface.text.ITypedRegion

/** Entry point to partition Scala sources.
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
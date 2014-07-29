package org.scalaide.ui.internal.editor

import org.eclipse.core.filebuffers.IDocumentSetupParticipant
import org.eclipse.jdt.internal.ui.JavaPlugin
import org.eclipse.jface.text.IDocument
import org.scalaide.core.internal.lexical.ScalaPartitions

/**
 * The behavior of this class is adapted to [[JavaDocumentSetupParticipant]],
 * which can't be used because it calls [[JavaTextTools]] with a Java
 * partitioning.
 */
class ScalaDocumentSetupParticipant extends IDocumentSetupParticipant {
  override def setup(doc: IDocument) = {
    val tools = JavaPlugin.getDefault().getJavaTextTools()
    tools.setupJavaDocumentPartitioner(doc, ScalaPartitions.SCALA_PARTITIONING)
  }
}
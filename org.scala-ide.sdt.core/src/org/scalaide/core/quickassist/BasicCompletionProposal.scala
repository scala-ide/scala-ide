package org.scalaide.core.quickassist

import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.contentassist.IContextInformation
import org.eclipse.swt.graphics.Image
import org.eclipse.swt.graphics.Point
import org.scalaide.core.internal.ScalaPlugin
import org.scalaide.core.internal.statistics.Features._

/**
 * Models an entry in the quick assist proposal.
 *
 * @param feature
 *        The association with the statistic tracking system of the IDE.
 * @param relevance
 *        Denotes at which position in among all available entries this entry
 *        occurs. A higher value means a better position.
 * @param displayString
 *        A text of explanation shown in the completion proposal.
 * @param image
 *        An image shown beside the `displayString`. If this is `null` no image
 *        is shown.
 */
abstract class BasicCompletionProposal(val feature: Feature, relevance: Int, displayString: String, image: Image = null)
    extends IJavaCompletionProposal {

  @deprecated("use primary constructor instead", "4.3")
  def this(relevance: Int, displayString: String) =
    this(NotSpecified, relevance, displayString, null)

  @deprecated("use primary constructor instead", "4.3")
  def this(relevance: Int, displayString: String, image: Image) =
    this(NotSpecified, relevance, displayString, image)

  override def getRelevance(): Int = relevance
  override def getDisplayString(): String = displayString
  override def getSelection(document: IDocument): Point = null
  override def getAdditionalProposalInfo(): String = null
  override def getImage(): Image = image
  override def getContextInformation(): IContextInformation = null

  /** Override [[applyProposal]] instead. */
  // TODO make final after source compatibility with 4.3 is dropped.
  /*final*/ override def apply(doc: IDocument): Unit = {
    ScalaPlugin().statistics.incUsageCounter(feature)
    applyProposal(doc)
  }

  /**
   * This method is automatically called by the IDE, whenever the proposal needs
   * to be applied to `doc`.
   */
  def applyProposal(doc: IDocument): Unit
}

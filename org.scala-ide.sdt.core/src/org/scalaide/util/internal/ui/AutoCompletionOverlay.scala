package org.scalaide.util.internal.ui

import org.eclipse.jface.bindings.keys.KeyStroke
import org.eclipse.jface.fieldassist.ContentProposal
import org.eclipse.jface.fieldassist.ContentProposalAdapter
import org.eclipse.jface.fieldassist.IContentProposal
import org.eclipse.jface.fieldassist.IContentProposalListener2
import org.eclipse.jface.fieldassist.IContentProposalProvider
import org.eclipse.jface.fieldassist.TextContentAdapter
import org.eclipse.swt.widgets.Control

/**
 * Opens an auto completion popup that acts as an overlay over a component `c`.
 * To set contents to the popup, use the [[setProposals]] method. It is possible
 * to check whether the popup is opened with the [[isPopupOpened]].
 *
 * The completion popup does not show completion entries that don't match the
 * content of the underlying component `c`. If the completion entry matches the
 * content exactly, then it is also not shown.
 *
 * The implementation of this method is adapted from
 * [[org.eclipse.jface.fieldassist.AutoCompleteField]].
 */
final class AutoCompletionOverlay(c: Control) {
  private var isOpened = false
  private var proposals = Seq[String]()

  private val pp = new IContentProposalProvider {
    override def getProposals(contents: String, position: Int): Array[IContentProposal] = {
      def isValid(p: String) =
        p.length() > contents.length() && p.substring(0, contents.length()).equalsIgnoreCase(contents)

      proposals.filter(isValid).map(new ContentProposal(_)).toArray
    }
  }

  private val pa = new ContentProposalAdapter(
      c, new TextContentAdapter, pp,
      KeyStroke.getInstance("Ctrl+Space"), "./".toArray)
  pa.setPropagateKeys(true)
  pa.setFilterStyle(ContentProposalAdapter.FILTER_NONE)
  pa.setProposalAcceptanceStyle(ContentProposalAdapter.PROPOSAL_REPLACE)
  pa.addContentProposalListener(new IContentProposalListener2 {
    override def proposalPopupOpened(a: ContentProposalAdapter) =
      isOpened = true
    override def proposalPopupClosed(a: ContentProposalAdapter) =
      isOpened = false
  })

  def isPopupOpened: Boolean =
    isOpened

  def setProposals(proposals: Seq[String]): Unit =
    this.proposals = proposals
}

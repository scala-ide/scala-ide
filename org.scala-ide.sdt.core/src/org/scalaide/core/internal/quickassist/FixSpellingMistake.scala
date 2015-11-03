package org.scalaide.core.internal.quickassist

import org.eclipse.jdt.internal.ui.text.spelling.WordQuickFixProcessor
import org.scalaide.core.internal.statistics.Features.FixSpellingMistake
import org.scalaide.core.quickassist.BasicCompletionProposal
import org.scalaide.core.quickassist.InvocationContext
import org.scalaide.core.quickassist.QuickAssist

/**
 * Creates quick assists for spelling mistakes.
 */
class FixSpellingMistake extends QuickAssist {

  override def compute(ctx: InvocationContext): Seq[BasicCompletionProposal] = {
    val jctx = new JavaInvocationContextAdapter(ctx)
    val p = new WordQuickFixProcessor()
    Option(p.getCorrections(jctx, jctx.javaProblemLocations)).map{cs =>
      cs.map(JavaProposalAdapter(FixSpellingMistake, _)): Seq[BasicCompletionProposal]
    }.getOrElse(Seq())
  }

}

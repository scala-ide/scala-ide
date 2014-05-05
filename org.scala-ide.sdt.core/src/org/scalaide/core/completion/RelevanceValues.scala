package org.scalaide.core.completion

object RelevanceValues {

  final val ProposalRefactoringHandlerAdapter = 100

  final val ExpandingProposalBase = 100

  final val ImportCompletionProposal = 100

  /**
   * Relevance should be less than [[ImportCompletionProposal]] since import quick
   * fix should be first.
   */
  final val CreateClassProposal = 90

  /**
   * Should be higher than most others, because you probably want to correct the
   * method/field name rather than create a new one.
   */
  final val ChangeCaseProposal = 95
}

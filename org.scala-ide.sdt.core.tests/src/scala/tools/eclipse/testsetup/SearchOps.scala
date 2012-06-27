package scala.tools.eclipse.testsetup

import scala.collection.JavaConverters.asScalaBufferConverter
import org.eclipse.jdt.core.IJavaElement
import org.eclipse.jdt.core.search.IJavaSearchConstants
import org.eclipse.jdt.core.search.SearchEngine
import org.eclipse.jdt.core.search.SearchMatch
import org.eclipse.jdt.core.search.SearchParticipant
import org.eclipse.jdt.core.search.SearchPattern
import org.eclipse.jdt.internal.corext.util.SearchUtils
import org.eclipse.jface.text.IRegion
import org.eclipse.jdt.core.search.SearchRequestor
import scala.collection.mutable.ListBuffer

object SearchOps {
  def searchType(typeName: String): List[SearchMatch] = {
    val pattern = SearchPattern.createPattern(typeName,
      IJavaSearchConstants.CLASS,
      IJavaSearchConstants.REFERENCES,
      SearchPattern.R_EXACT_MATCH | SearchPattern.R_CASE_SENSITIVE)

    searchWorkspaceFor(pattern)
  }

  def findReferences(element: IJavaElement, wordRegion: IRegion): List[SearchMatch] = {
    val pattern = SearchPattern.createPattern(element,
      IJavaSearchConstants.REFERENCES,
      SearchUtils.GENERICS_AGNOSTIC_MATCH_RULE)

    searchWorkspaceFor(pattern)
  }

  private def searchWorkspaceFor(pattern: SearchPattern): List[SearchMatch] = {
    val engine = new SearchEngine
    val participants = Array[SearchParticipant](SearchEngine.getDefaultSearchParticipant)
    val scope = SearchEngine.createWorkspaceScope()
    val requestor = new UnfilteredSearchRequestor
    engine.search(pattern, participants, scope, requestor, null)

    requestor.results
  }

  private class UnfilteredSearchRequestor extends SearchRequestor {
    private val searchResults: ListBuffer[SearchMatch] = new ListBuffer

    override def acceptSearchMatch(matched: SearchMatch): Unit = searchResults += matched

    def results: List[SearchMatch] = searchResults.toList
  }
}

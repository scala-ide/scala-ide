package org.scalaide.core.internal

import scala.util.matching.Regex

import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.jdt.core.IJavaElement
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.search.IJavaSearchConstants
import org.eclipse.jdt.core.search.SearchEngine
import org.eclipse.jdt.core.search.TypeNameMatch
import org.scalaide.core.quickassist.BasicCompletionProposal
import org.scalaide.util.eclipse.TypeNameMatchCollector

package object quickassist {

  val TypeNotFoundError: Regex = "not found: type (.*)".r
  val ValueNotFoundError: Regex = "not found: value (.*)".r
  val XXXXXNotFoundError: Regex = "not found: (.*)".r
  val ValueNotAMember: Regex = "value (.*) is not a member of (.*)".r
  val ValueNotAMemberOfObject: Regex = "value (.*) is not a member of object (.*)".r
  val TypeMismatchError: Regex = """type mismatch;\s*found\s*: (\S*)\s*required: (.*)""".r

  def matchTypeNotFound(problemMessage: String, suggest: String => Seq[BasicCompletionProposal]): Seq[BasicCompletionProposal] = {
    problemMessage match {
      case TypeNotFoundError(missingType)   => suggest(missingType)
      case ValueNotFoundError(missingValue) => suggest(missingValue)
      case XXXXXNotFoundError(missing)      => suggest(missing)
      case _                                => Nil
    }
  }

  def searchForTypes(project: IJavaProject, name: String): Seq[TypeNameMatch] = {
    val resultCollector = new java.util.ArrayList[TypeNameMatch]
    val scope = SearchEngine.createJavaSearchScope(Array[IJavaElement](project))
    val typesToSearch = Array(name.toArray)
    new SearchEngine().searchAllTypeNames(
        null,
        typesToSearch,
        scope,
        new TypeNameMatchCollector(resultCollector),
        IJavaSearchConstants.WAIT_UNTIL_READY_TO_SEARCH,
        new NullProgressMonitor)

    import scala.collection.JavaConverters._
    resultCollector.asScala
  }
}

package scala.tools.eclipse.quickfix

// Java imports
import java.util.regex.Pattern

/**
 * @author Ivan Kuraj
 * This object is used for applying code transformations based on the found and required type
 * extract from the annotation message (such as quick fix message) and the expression in the source code.
 * The object arguments are: found type string, required type string and annotation string, respectively and
 * the result is a list of strings which should replace the annotation string
 */
object TypeMismatchQuickFixProcessor extends
  ((String, String, String) => List[String]) {

  /** list containing all type mismatch quick fix cases that this object should go through */
  val cases: List[TypeMismatchQuickFixCase] =
    List(
      // "type mismatch: List[T] but found List[List[T]]
      FoundToRequiredTypeCase(
        List("%s.flatten", "%s.head", "%s.last"),
        Pattern.compile("List\\[List\\[(.*)\\]\\]"), Pattern.compile("List\\[(.*)\\]"), Pattern.compile("^(.*)$")
      ),
      // "type mismatch: Array[T] but found List[T]
      FoundToRequiredTypeCase(
        List("%s.toArray"),
        Pattern.compile("List\\[(.*)\\]"), Pattern.compile("Array\\[(.*)\\]"), Pattern.compile("^(.*)$")
      ),
      // "type mismatch: found T; required Option[T]" -> suggest to wrap the result in Some()
      FoundToRequiredTypeCase(
        List("Option(%s)", "Some(%s)"),
        Pattern.compile("(.*)"), Pattern.compile("Option\\[(.*)\\]"), Pattern.compile("^(.*)$")
      )
      // TODO: compiler does not return annotations properly, uncomment this and tests when it is fixed
      // "type mismatch: found BasicType(T); required Option[T]" -> suggest to wrap the result in Some()
//      ,
//      FoundToRequiredTypeCase(
//        List("Option(%s)", "Some(%s)"),
//        Pattern.compile("(?:java\\.lang\\.)([a-zA-Z&&[^\\(\\)]]+)\\(.*\\)"), Pattern.compile("Option\\[(.*)\\]"), Pattern.compile("^(.*)$")
//      )
    )

  /**
   * apply method for getting list of replacement strings
   * @param foundType extracted found type string
   * @param requiredType extracted required type string
   * @param annotationString extracted expression string from the source code
   * @return list of strings which should replace the annotation string
   */
  def apply(foundType: String, requiredType: String, annotationString: String): List[String] =
    // go through all cases and collect lists of replacement strings
    (List[String]() /: cases) {
      case (list, ftrtc:FoundToRequiredTypeCase) => {
         list ++ ftrtc.apply(foundType, requiredType, annotationString)
      }
    }

}

/** trait marking all type mismatch quick fix cases */
trait TypeMismatchQuickFixCase

/**
 * class which is to be inherited if quick fix simply injects a sequence of strings into a format strings of
 * form "... %s... %s..."
 */
abstract class SimpleFormatQuickFixCase(formatStrings: List[String]) extends TypeMismatchQuickFixCase {
  def apply(listOfInjectStrings: Seq[String]*) =
    for (
      // iterate through all sequences of strings to inject
      injectString <- listOfInjectStrings;
      // iterate through all given format
      formatString <- formatStrings
      // yield a string when inject strings are applied to the format string
    ) yield { formatString.format( injectString:_* ) }
}

/**
 * class which checks whether found type string and required type string match - it does by
 * capturing all groups according to the found and required patterns and compares them for match -
 * if all match, the replacement is proceeded by extracting inject strings from the annotation pattern
 * and applying them to SimpleFormatQuickFixCase
 *
 * found and required patterns should extract the same number of groups
 * annotation string should extract required number of groups to feed the given format string
 */
case class FoundToRequiredTypeCase(formatStrings: List[String],
    found: Pattern, required: Pattern, annotationExtract: Pattern) extends SimpleFormatQuickFixCase(formatStrings) {
  def apply(foundType: String, requiredType: String, annotationString: String): Seq[String] = {
    // get matchers
    val foundMatcher = found.matcher(foundType)
    val requiredMatcher = required.matcher(requiredType)

    // if both matched
    // NOTE (we expect only a single match)
    if (foundMatcher.find && requiredMatcher.find) {
      // check if all groups match
      if (
        // fold all groups and compare them - capturing group count must be the same for both patterns
        (true /: (1 to foundMatcher.groupCount()) ) {
          case (false, _) => false
          case (result, ind) => foundMatcher.group(ind) == requiredMatcher.group(ind)
        }
      ) {
        // get annotation matcher
        val annotationMatcher = annotationExtract.matcher(annotationString)
        // check if find can pass (only single match is expected)
        if (annotationMatcher.find) {
          // get injection strings
          val injectStrings =
            for (ind <- 1 to annotationMatcher.groupCount()) yield { annotationMatcher.group(ind) }
          // apply them to the format string
          super.apply(injectStrings)
        // in case annotation matcher cannot find
        } else Nil
      }
      // in case groups don't match
      else Nil
    // in case matchers fail
    } else Nil
  }
}
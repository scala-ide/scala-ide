package org.scalaide.core.internal.launching

import scala.collection.mutable
import org.eclipse.core.runtime.SubProgressMonitor
import org.eclipse.jdt.core.search._
import org.eclipse.core.resources.IResource
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jdt.core._
import org.eclipse.jdt.internal.junit.util.CoreTestSearchEngine
import org.eclipse.jdt.internal.junit.JUnitCorePlugin
import org.eclipse.jdt.core.dom.IAnnotationBinding
import org.eclipse.jdt.core.dom.ITypeBinding
import scala.annotation.tailrec
import org.scalaide.logging.HasLogger

/** This class re-implements the logic of {{{org.eclipse.jdt.internal.junit.launcher.JUnit4TestFinder}}}, with one
 *  limitation:
 *
 *   - no JUnit 3.8 support (extending `TestCase`) or `suite()` methods. The original code is commented out in
 *  method `javaMatches`.
 *
 *  The original code organization is preserved, but Scala-fied.
 */
class JavaJUnit4TestFinder extends HasLogger {
  import JavaJUnit4TestFinder._

  /** Return all Java JUnit 4 tests found in the given sources.  */
  def javaMatches(javaProject: IJavaProject, sources: Seq[IResource], pm: IProgressMonitor): Seq[IType] = try {
    val region = JavaCore.newRegion()
    sources.foreach(res => Option(JavaCore.create(res)).map(region.add))

    val hierarchy = JavaCore.newTypeHierarchy(region, null, new SubProgressMonitor(pm, 1))
    val allClasses = hierarchy.getAllClasses()

    // search for all types with references to RunWith and Test and all subclasses
    val candidates = new mutable.HashSet[IType]
    val result = new mutable.HashSet[IType]
    val requestor = new AnnotationSearchRequestor(hierarchy, candidates)

    val scope = SearchEngine.createJavaSearchScope(allClasses.asInstanceOf[Array[IJavaElement]], IJavaSearchScope.SOURCES)
    val matchRule = SearchPattern.R_EXACT_MATCH | SearchPattern.R_CASE_SENSITIVE
    val runWithPattern = SearchPattern.createPattern(RUN_WITH.name, IJavaSearchConstants.ANNOTATION_TYPE, IJavaSearchConstants.ANNOTATION_TYPE_REFERENCE, matchRule)
    val testPattern = SearchPattern.createPattern(TEST.name, IJavaSearchConstants.ANNOTATION_TYPE, IJavaSearchConstants.ANNOTATION_TYPE_REFERENCE, matchRule)

    val annotationsPattern = SearchPattern.createOrPattern(runWithPattern, testPattern)
    val searchParticipants = Array(SearchEngine.getDefaultSearchParticipant())
    new SearchEngine().search(annotationsPattern, searchParticipants, scope, requestor, new SubProgressMonitor(pm, 2))

    // find all classes in the region
    for (curr <- candidates) {
      if (CoreTestSearchEngine.isAccessibleClass(curr) && !Flags.isAbstract(curr.getFlags()) && region.contains(curr)) {
        result.add(curr)
      }
    }

    // add all classes implementing JUnit 3.8's Test interface in the region
    //    val testInterface = javaProject.findType(JUnitCorePlugin.TEST_INTERFACE_NAME)
    //    if (testInterface != null) {
    //      CoreTestSearchEngine.findTestImplementorClasses(hierarchy, testInterface, region, result);
    //    }

    //JUnit 4.3 can also run JUnit-3.8-style public static Test suite() methods:
    //      CoreTestSearchEngine.findSuiteMethods(element, result, new SubProgressMonitor(pm, 1));
    result.toSeq
  } catch {
    case e: Exception =>
      logger.info("Java Test Finder crashed while looking for Java matches.", e)
      Seq()
  } finally {
    pm.done()
  }

  private class AnnotationSearchRequestor(hierarchy: ITypeHierarchy, result: mutable.Set[IType]) extends SearchRequestor {
    def acceptSearchMatch(smatch: SearchMatch) {
      if (smatch.getAccuracy() == SearchMatch.A_ACCURATE && !smatch.isInsideDocComment()) {
        smatch.getElement() match {
          case tpe: IType    => addTypeAndSubtypes(tpe)
          case meth: IMethod => addTypeAndSubtypes(meth.getDeclaringType())
        }
      }
    }

    private def addTypeAndSubtypes(tpe: IType) {
      if (result.add(tpe))
        hierarchy.getSubclasses(tpe).foreach(addTypeAndSubtypes)
    }
  }
}

object JavaJUnit4TestFinder {
  private final val RUN_WITH = new Annotation("org.junit.runner.RunWith")
  private final val TEST = new Annotation("org.junit.Test")

  private class Annotation(val name: String) {
    private def annotates(annotations: Array[IAnnotationBinding]): Boolean = {
      annotations exists { annot =>
        val annotationType = annot.getAnnotationType()
        (annotationType != null && (annotationType.getQualifiedName().equals(name)))
      }
    }

    @tailrec
    private def annotatesTypeOrSuperTypes(tpe: ITypeBinding): Boolean = {
      (tpe != null) &&
        (annotates(tpe.getAnnotations)
          || annotatesTypeOrSuperTypes(tpe.getSuperclass))
    }

    @tailrec
    private def annotatesAtLeastOneMethod(tpe: ITypeBinding): Boolean = {
      (tpe != null) &&
        (tpe.getDeclaredMethods.exists(m => annotates(m.getAnnotations))
          || annotatesAtLeastOneMethod(tpe.getSuperclass))
    }
  }
}

package scala.tools.eclipse.quickfix

import org.eclipse.ui.IEditorPart
import org.eclipse.jface.text.source.Annotation
import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitDocumentProvider.ProblemAnnotation
import org.eclipse.jdt.ui.JavaUI
import org.eclipse.core.runtime.CoreException
import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.jdt.core.search.IJavaSearchConstants
import org.eclipse.jdt.internal.codeassist.ISearchRequestor
import org.eclipse.jdt.internal.compiler.env.{ AccessRestriction, INameEnvironment }
import org.eclipse.jdt.internal.core.{ DefaultWorkingCopyOwner, JavaProject }
import org.eclipse.jdt.internal.ui.text.correction.{ SimilarElement, SimilarElementsRequestor }
import org.eclipse.jdt.ui.text.java._
import org.eclipse.core.resources.IMarker
import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.eclipse.util.FileUtils
import scala.util.matching.Regex

class ScalaQuickFixProcessor extends IQuickFixProcessor {
  private val typeNotFoundError = new Regex("not found: type (.*)")
  private val valueNotFoundError = new Regex("not found: value (.*)")
  private val xxxxxNotFoundError = new Regex("not found: (.*)")
  
  /**
   * Checks if the processor has any corrections.
   * 
   * Currently this always returns true. At some point it may be worthwhile
   * to expend some effort on implementing this properly to make the plug-in
   * slightly more responsive.
   */
  def hasCorrections(unit : ICompilationUnit, problemId : Int) : Boolean = true

  
  /**
   * Collects corrections or code manipulations for the given context.
   *
   * @param context Defines current compilation unit, position and a shared AST
   * @param locations Problems are the current location.
   * @return the corrections applicable at the location or <code>null</code> if no proposals
   *      can be offered
   * @throws CoreException CoreException can be thrown if the operation fails
   */
  def getCorrections(context : IInvocationContext, locations : Array[IProblemLocation]) : Array[IJavaCompletionProposal] =
    context.getCompilationUnit match {
      case ssf : ScalaSourceFile => {
    	val editor = JavaUI.openInEditor(context.getCompilationUnit)
        var corrections : List[IJavaCompletionProposal] = Nil
        for (location <- locations)
        	for (ann <- getAnnotationsAtOffset(editor, location.getOffset)) {
         	  val fix = suggestFix(context.getCompilationUnit(), ann.getText)
              corrections = corrections ++ fix
        	}
        corrections match {
          case Nil => null
          case l => l.distinct.toArray
        }
      }
      case _ => null
  }
  
  private def getAnnotationsAtOffset(part: IEditorPart, offset: Int): List[Annotation] = {
	  import ScalaQuickFixProcessor._ 
	  
	  var ret = List[Annotation]() 
	  val model = JavaUI.getDocumentProvider().getAnnotationModel(part.getEditorInput())
	  val iter = model.getAnnotationIterator
	  while (iter.hasNext()) {
	 	val ann: Annotation = iter.next().asInstanceOf[Annotation]
	 	val pos = model.getPosition(ann)
	 	if (isInside(offset, pos.offset, pos.offset + pos.length))
	 	    ret = ann :: ret
	  }
	  return ret
  }
  
  private class Requestor(val typeToFind : String) extends ISearchRequestor {
    val typesFound = scala.collection.mutable.ListBuffer[String]()
    
    def acceptConstructor(
        modifiers : Int, simpleTypeName : Array[Char], parameterCount : Int, signature : Array[Char], parameterTypes : Array[Array[Char]], parameterNames : Array[Array[Char]], 
        typeModifiers : Int, packageName : Array[Char], extraFlags : Int, path : String, access : AccessRestriction) = {

      // Ignore constructors
    }
  
    /**
     * One result of the search consists of a new type.
     *
     * NOTE - All package and type names are presented in their readable form:
     *    Package names are in the form "a.b.c".
     *    Nested type names are in the qualified form "A.I".
     *    The default package is represented by an empty array.
     */
    def acceptType(packageName : Array[Char], typeNameChars : Array[Char], enclosingTypeNames : Array[Array[Char]], modifiers : Int, accessRestriction : AccessRestriction) = {
      // If the type matches what we were looking for then it add it to the list of those found
      val typeName = new String(typeNameChars)
      if (typeName == typeToFind) {
        val enclosingTypeNamesAsStrings = new String(packageName) :: enclosingTypeNames.map(new String(_)).toList
        val enclosingTypeNamesString = 
          enclosingTypeNamesAsStrings.mkString(".").replaceAll("\\$", "")
        typesFound += enclosingTypeNamesString + "." + typeName      
      }
    }
    
    
    /**
     * One result of the search consists of a new package.
     *
     * NOTE - All package names are presented in their readable form:
     *    Package names are in the form "a.b.c".
     *    The default package is represented by an empty array.
     */
    def acceptPackage(packageName : Array[Char]) = {
      // Ignore packages
    }
  }

  private
  def suggestFix(compilationUnit : ICompilationUnit, problemMessage : String) : List[IJavaCompletionProposal] = {
    /**
     * Import a type could solve several error message :
     * 
     * * "not found : type  Xxxx"
     * * "not found : value Xxxx" in case of java static constant/method like Xxxx.ZZZZ or Xxxx.zzz()
     * * "not found : Xxxx" in case of new Xxxx.eee (IMO (davidB) a better suggestion is to insert (), to have new Xxxx().eeee )
     */
    def suggestImportType(missingType : String) : List[IJavaCompletionProposal] = {
      // Get similar types
      val project = compilationUnit.asInstanceOf[ScalaSourceFile].getJavaProject()
      val ne = project.asInstanceOf[JavaProject].newSearchableNameEnvironment(DefaultWorkingCopyOwner.PRIMARY)
      val requestor = new Requestor(missingType)
      ne.findTypes(missingType.toCharArray(), true, false, IJavaSearchConstants.TYPE, requestor)
      // Return the types found
      requestor.typesFound.map({ typeFound => new ImportCompletionProposal(typeFound) }).toList
    }
    
    return problemMessage match {
      case typeNotFoundError(missingType) => suggestImportType(missingType)
      case valueNotFoundError(missingValue) => suggestImportType(missingValue)
      case xxxxxNotFoundError(missing) => suggestImportType(missing)
      case _ => Nil
    }
  }
}

object ScalaQuickFixProcessor {
	private def isInside(offset: Int, start: Int,end: Int): Boolean = {
		return offset == start || offset == end || (offset > start && offset < end); // make sure to handle 0-length ranges
    }
}


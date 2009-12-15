package scala.tools.eclipse

import org.eclipse.jdt.ui.text.java._

import org.eclipse.jdt.internal.compiler.env.INameEnvironment
import org.eclipse.core.runtime.CoreException
import org.eclipse.jdt.core.ICompilationUnit
import scala.tools.eclipse.javaelements.ScalaSourceFile
import org.eclipse.jdt.core.compiler.IProblem
import scala.util.matching.Regex
import scala.tools.eclipse.quickfix.proposal._
import org.eclipse.jdt.internal.ui.text.correction.SimilarElementsRequestor
import org.eclipse.jdt.internal.ui.text.correction.SimilarElement
import org.eclipse.jdt.internal.core.JavaProject
import org.eclipse.jdt.internal.core.DefaultWorkingCopyOwner
import org.eclipse.jdt.core.search.IJavaSearchConstants
import org.eclipse.jdt.internal.codeassist.ISearchRequestor
import org.eclipse.jdt.internal.compiler.env.AccessRestriction

class ScalaQuickFixProcessor extends IQuickFixProcessor {
  val typeNotFoundError = new Regex("not found: type (.*)")
  
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
  def getCorrections(context : IInvocationContext, locations : Array[IProblemLocation]) : Array[IJavaCompletionProposal] = {
    val problems = context.getCompilationUnit().asInstanceOf[ScalaSourceFile].getProblems()
    val corrections = scala.collection.mutable.ListBuffer[IJavaCompletionProposal]()

    // Go over each location and suggest fixes for each matching problem
    locations.foreach { location =>
      problems.foreach { problem =>
        if (location.getOffset() == problem.getSourceStart()
            && location.getLength() == (1 + problem.getSourceEnd() - problem.getSourceStart())) {
         
          // Suggest a fix!
          val fix = suggestFix(context.getCompilationUnit(), problem)
          if (fix != null) {
            corrections ++ fix
          }
        }
      }
    }
    
    corrections.toArray
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

  def suggestFix(compilationUnit : ICompilationUnit, problem : IProblem) : List[IJavaCompletionProposal] = {
    
    return problem.getMessage() match {
      case typeNotFoundError(missingType) =>
        
        // Get similar types
        val project = compilationUnit.asInstanceOf[ScalaSourceFile].getJavaProject()
        val ne = project.asInstanceOf[JavaProject].newSearchableNameEnvironment(DefaultWorkingCopyOwner.PRIMARY)
        val requestor = new Requestor(missingType)
        ne.findTypes(missingType.toCharArray(), true, false, IJavaSearchConstants.TYPE, requestor)
        
        // Return the types found
        requestor.typesFound.map({ typeFound => new ImportCompletionProposal(typeFound) }).toList

      case _ => null
    }
  }
}




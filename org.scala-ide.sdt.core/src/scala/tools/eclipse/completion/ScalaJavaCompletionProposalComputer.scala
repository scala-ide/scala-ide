package scala.tools.eclipse.completion

import org.eclipse.jdt.ui.text.java.IJavaCompletionProposalComputer
import org.eclipse.jdt.ui.text.java.ContentAssistInvocationContext
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import java.util.Collections.{ emptyList => javaEmptyList }
import org.eclipse.jdt.core._
import scala.tools.eclipse.ScalaPlugin
import org.eclipse.jface.text.Document
import scala.tools.eclipse.ScalaImages
import scala.tools.eclipse.ui.ScalaCompletionProposal
import org.eclipse.jdt.core.dom.ASTParser
import org.eclipse.jdt.core.dom.AST
import org.eclipse.jdt.core.dom.ASTVisitor
import org.eclipse.jdt.core.dom.ASTNode
import org.eclipse.jdt.core.dom.SimpleName
import scala.annotation.target.getter
import org.eclipse.jdt.core.dom.QualifiedName
import org.eclipse.jdt.core.dom.MethodInvocation
import org.eclipse.jdt.core.compiler.IProblem
import org.eclipse.jdt.core.dom.CompilationUnit
import org.eclipse.jdt.core.dom.Block
import org.eclipse.jdt.core.dom.TypeDeclaration
import org.eclipse.jface.text.ITextViewer
import org.eclipse.jface.viewers.ISelectionProvider
import org.eclipse.jdt.core.dom.Assignment
import org.eclipse.jdt.core.dom.ITypeBinding
import org.eclipse.jdt.core.dom.Expression
import org.eclipse.jdt.core.dom.ExpressionStatement
import org.eclipse.jdt.core.dom.Comment
import org.eclipse.jdt.core.dom.FieldDeclaration
import org.eclipse.jdt.core.dom.VariableDeclarationFragment
import org.eclipse.jdt.core.dom.Statement
import org.eclipse.jdt.internal.compiler.ast.FieldReference
import org.eclipse.jdt.core.dom.FieldAccess
import org.eclipse.jdt.core.dom.VariableDeclarationStatement
import org.eclipse.jdt.core.dom.ClassInstanceCreation

import org.eclipse.jface.text.contentassist.ICompletionProposal

/** A completion proposal for Java sources. This adds mixed-in concrete members to scope
 *  completions in Java.
 *  
 *  Although the ScalaStructureBuilder reports traits and classes correctly, the Java 
 *  completion proposal is confused by concrete trait members. Specifically, given 
 *  a Scala trait T with a concrete method 'foo', and a Scala class C that extends T,
 *  all Java classes that extend C won't see 'foo' in the completion proposal. The reason
 *  is that the Java completion proposal won't collect inherited /interface/ methods from
 *  concrete types, considering that all interface methods have to be implemented in one
 *  of the concrete classes seen so far. Obviously, this doesn't hold for concrete
 *  trait members.
 *  
 *  This class only proposes concrete methods coming from Scala traits.
 * 
 * The //test comments reference the test cases from <code>ScalaJavaCompletionTests</code>
 */
class ScalaJavaCompletionProposalComputer extends IJavaCompletionProposalComputer {
  def sessionStarted() {}
  def sessionEnded() {}
  def getErrorMessage() = null

  def computeContextInformation(context: ContentAssistInvocationContext, monitor: IProgressMonitor) = 
    javaEmptyList()
    
  def computeCompletionProposals(context: ContentAssistInvocationContext, monitor: IProgressMonitor): java.util.List[ICompletionProposal] = {
    context match {
      case jc: JavaContentAssistInvocationContext => 
        if (ScalaPlugin.plugin.isScalaProject(jc.getProject()))
          jc.getCompilationUnit match {
          case scu: ScalaCompilationUnit => javaEmptyList()
          case _ => mixedInCompletions(jc.getCompilationUnit(), jc.getInvocationOffset(), jc.getViewer().getSelectionProvider(), monitor)
        } else
          javaEmptyList()
      case _ => javaEmptyList()
    }
  }

  /**
   * Return completion proposals for mixed-in methods.
   */
  def mixedInCompletions(unit: ICompilationUnit, invocationOffset: Int, selectionProvider: ISelectionProvider, monitor: IProgressMonitor): java.util.List[ICompletionProposal] = {
    // make sure the unit is consistent with the editor buffer
    unit.makeConsistent(monitor)
    // ask for the Java AST of the source
    val ast = unit.reconcile(AST.JLS3,
      ICompilationUnit.FORCE_PROBLEM_DETECTION | // force the resolution of the type bindings
        ICompilationUnit.ENABLE_STATEMENTS_RECOVERY | // try to make sense of malformed statements
        ICompilationUnit.ENABLE_BINDINGS_RECOVERY, // try to guess binding even if the code is not fully valid
      new CompletionWorkingCopyOwner,
      monitor)

    // go through the Java AST to find the completion prefix and the referenced type at
    // the invocation location
    val astVisitor = new JavaASTVisitor(unit, invocationOffset)
    ast.accept(astVisitor)

    // could not find the referenced type because it is not possible to have completion
    // at the invocation location. Bailing out.
    if (astVisitor.referencedTypeBinding == null) {
      return javaEmptyList()
    }

    val referencedTypeName = astVisitor.referencedTypeBinding.getQualifiedName()
    // element to complete
    val prefix = astVisitor.contextString
    // offset of the start of the element to complete
    val start = invocationOffset - prefix.length

    val prj = ScalaPlugin.plugin.getScalaProject(unit.getJavaProject.getProject)
    val completionProposals = prj.withPresentationCompiler { compiler =>
      import compiler._

      def mixedInMethod(sym: Symbol): Boolean =
        (sym.isMethod &&
          !sym.isDeferred &&
          sym.owner.isTrait &&
          !sym.isConstructor &&
          !sym.isPrivate)

      compiler.askOption { () =>
        val currentClass = definitions.getClass(referencedTypeName.toTypeName)
        val proposals = currentClass.info.members.filter(mixedInMethod)

        for (sym <- proposals if sym.name.startsWith(prefix)) yield {
          val prop = compiler.mkCompletionProposal(start, sym = sym, tpe = sym.info, inherited = true, viaView = NoSymbol)
          new ScalaCompletionProposal(prop, selectionProvider)
        }
      }.getOrElse(Nil)
    }(Nil)

    import scala.collection.JavaConversions._

    completionProposals: java.util.List[ICompletionProposal]
  }

}

/**
 * WorkingCopyOwner created to get a fully resolved Java AST.
 * The only thing it does is returning a IProblemRequestor which answers
 * <code>true</code> when asked <code>#isActive()</code>
 */
private class CompletionWorkingCopyOwner extends WorkingCopyOwner {

  val problemRequestor = new IProblemRequestor() {
    def acceptProblem(problem: IProblem) = None
    def beginReporting() = None
    def endReporting() = None
    def isActive() = true
  }

  override def getProblemRequestor(compilationUnit: ICompilationUnit): IProblemRequestor = problemRequestor
}

/**
 * Visitor used to find the the deepest AST node containing the invocation offset.
 * From it, it extracts the binding of the type referenced by the string to complete,
 * and the string to complete.
 */
private class JavaASTVisitor(unit: ICompilationUnit, offset: Int) extends ASTVisitor {

  // the string to complete
  var contextString: String = null
  // the binding of the type referenced by the string to complete
  var referencedTypeBinding: ITypeBinding = null

  // the node containing the offset
  var enclosingNode: ASTNode = null

  /*
   * This method is called before visiting each node.
   * It blocks or allows visiting the node.
   * Each node to be visited is stored as the enclosing node
   * until it finds the deepest one.
   */
  override def preVisit2(node: ASTNode): Boolean = {
    if (containsOffset(node)) {
      enclosingNode = node
      return true
    }
    return false
  }

  /*
   * Get the string to complete, and the referenced type form the 
   * found enclosing node
   */
  override def endVisit(compilationUnit: CompilationUnit) {
    enclosingNode match {
      case block: Block =>
        contextString = ""
        referencedTypeBinding = lookForIncompleteStatementAtTheEndOfBlock(block)
      case fieldAccess: FieldAccess => // test: foo11
        contextString = ""
        referencedTypeBinding = fieldAccess.getExpression().resolveTypeBinding()
      case fieldDeclaration: FieldDeclaration => // test: var11
        contextString = ""
        referencedTypeBinding = getDeclaringTypeBinding(fieldDeclaration)
      case qualifiedName: QualifiedName =>
        contextString = ""
        referencedTypeBinding = qualifiedName.getQualifier().resolveTypeBinding()
      case simpleName: SimpleName =>
        setContextStringFrom(simpleName)
        referencedTypeBinding = getTypeBindingFromParent(simpleName)
      case statement: Statement =>
        // somewhere in a statement, but not on a name. Check if it is an incomplete statement
        contextString = ""
        referencedTypeBinding = lookForIncompleteStatement(statement)
      case expression: Expression => // test: bar9
        // somewhere in an expression, but not on a name. Just use the declaring type
        contextString = ""
        referencedTypeBinding = getDeclaringTypeBinding(expression)
      case _ => // test: oracleOutsideTypeDeclaration
    }
  }

  /**
   * Return <code>true</code> if the offset is contained in the node
   * source range.
   */
  private def containsOffset(node: ASTNode): Boolean = {
    val startOffset = node.getStartPosition()
    startOffset <= offset && offset <= startOffset + node.getLength()
  }

  /**
   * Return the binding of the type referenced by the simpleName, like the callee for a method call
   * or the object for a field access.
   */
  private def getTypeBindingFromParent(simpleName: SimpleName): ITypeBinding = simpleName.getParent match {
    case assignment: Assignment =>
      /*
       * For some reason, a statement like:
       *   a.ge[caret]
       * is returned an assignment:
       *   a.ge=$missing$;
       * with '$missing$' being an empty SimpleName.  
       *   
       * To find this case, we check the size of the assignment and of the left hand side. If they
       * are equal, it means that the '=' is missing, so it is not an assignment.
       */
      if (assignment.getLength() == assignment.getLeftHandSide().getLength()) {
        getTypeBindingForSecondToLastSegment(assignment.getLeftHandSide())
      } else {
        getDeclaringTypeBinding(simpleName) // test: bar4, bar 6
      }
    case expressionStatement: ExpressionStatement => // test: bar5
      getDeclaringTypeBinding(simpleName)
    case fieldAccess: FieldAccess => // test: foo13
      fieldAccess.getExpression().resolveTypeBinding()
    case methodInvocation: MethodInvocation =>
      if (methodInvocation.getExpression != null && (methodInvocation.getName() eq simpleName)) {
        // it is the name of a method called on an expression
        methodInvocation.getExpression.resolveTypeBinding // test: var3, foo1, foo5
      } else {
        getDeclaringTypeBinding(simpleName) // test: var13, bar8, bar10
      }
    case qualifiedName: QualifiedName =>
      if (qualifiedName.getName() eq simpleName) {
        qualifiedName.getQualifier.resolveTypeBinding // test: var2, foo4
      } else {
        getDeclaringTypeBinding(simpleName) // test: bar11
      }
    case variableDeclarationFragment: VariableDeclarationFragment => // test: var12
      getDeclaringTypeBinding(simpleName)
    case _ =>
      null
  }

  /**
   * If the offset is not part of any statement of the block, it might be because it is right after the last statement,
   * which is incomplete.
   * Check for that.
   */
  private def lookForIncompleteStatementAtTheEndOfBlock(block: Block): ITypeBinding = {
    val statements = block.statements()
    if (!statements.isEmpty()) { // contains some statements
      lookForIncompleteStatement(statements.get(statements.size() - 1).asInstanceOf[Statement])
    } else {
      getDeclaringTypeBinding(block)
    }
  }

  /**
   * Try as it can, the Java AST parser is not always about generate 'good' AST node for incomplete statements.
   * This method try to support some cases.
   *
   * If the statement look like this:
   *   a.[caret]
   * It might be returned as an assignment, in an expression statement, omitting the '.':
   *   a=$missing$;
   *
   * If the statement look like this:
   *   get().[caret]
   * It might be returned as only the method invocation, in an expression statement, omitting the '.':
   *   get();
   *
   * If the statement look like this:
   *   String s= a.[caret]
   * It might be returned as a complete variable declaration statement, omitting the '.':
   *   String s= a;
   *
   * To find one of these case, we extract the expression from the statement and check if it is an incomplete
   * expression.
   */

  private def lookForIncompleteStatement(statement: Statement): ITypeBinding = {
    statement match {
      case expressionStatement: ExpressionStatement =>
        // check the expression of the expression statement
        return lookForIncompleteExpression(expressionStatement.getExpression())
      case variableDeclarationStatement: VariableDeclarationStatement =>
        // check the initializer of the last fragment of the variable declaration statement, if it exists
        val fragments = variableDeclarationStatement.fragments()
        if (!fragments.isEmpty()) {
          val initializer = fragments.get(fragments.size() - 1).asInstanceOf[VariableDeclarationFragment].getInitializer()
          if (initializer != null) {
            return lookForIncompleteExpression(initializer)
          }
        }
      case _ =>
    }
    // every other case, return the declaring type. test: bar1, bar3, bar7
    getDeclaringTypeBinding(statement)
  }

  /**
   * Incorrectly constructed incomplete expressions have in common that the string between the end of the reported node
   * and the offset contains only a '.'.
   *
   * This method check for this condition, then extract the right type binding.
   */
  private def lookForIncompleteExpression(expression: Expression): ITypeBinding = {
    if (expression.getStartPosition() + expression.getLength() < offset) { // offset is after the statement
      val traillingString = getTrimmedSourceWithoutComments(expression.getStartPosition() + expression.getLength(), offset)
      if ("." == traillingString) { // the source between the end of the last statement and the offset contains only a '.'
        expression match {
          case assignment: Assignment => // test: foo2
            return assignment.getLeftHandSide().resolveTypeBinding()
          case classInstanceCreation: ClassInstanceCreation => // test: foo12
            return classInstanceCreation.resolveTypeBinding()
          case methodInvocation: MethodInvocation => // test: foo8
            return methodInvocation.resolveTypeBinding()
          case simpleName: SimpleName => // test: foo9
            return simpleName.resolveTypeBinding()
          case _ =>
        }
      }
    }
    getDeclaringTypeBinding(expression) // test: foo6
  }

  /**
   * Return the binding of the type referenced by the second to last segment of this expression,
   * like the callee for a method call or the object for a field access.
   */
  private def getTypeBindingForSecondToLastSegment(expression: Expression): ITypeBinding = expression match {
    case simpleName: SimpleName => // test: bar2
      setContextStringFrom(simpleName)
      getDeclaringTypeBinding(expression)
    case qualifiedName: QualifiedName => // test: foo3
      setContextStringFrom(qualifiedName.getName())
      qualifiedName.getQualifier.resolveTypeBinding
    case fieldAccess: FieldAccess => // test: foo7
      setContextStringFrom(fieldAccess.getName())
      fieldAccess.getExpression().resolveTypeBinding()
    case _ =>
      null
  }

  /**
   * Set the value of contextString from a SimpleName node containing
   * the offset.
   */
  private def setContextStringFrom(simpleName: SimpleName) {
    contextString = simpleName.getIdentifier().substring(0, offset - simpleName.getStartPosition())
  }

  /**
   * Return the piece of source contained between the two offset, with the
   * comments removed, and trimmed.
   */
  private def getTrimmedSourceWithoutComments(startOffset: Int, endOffset: Int): String = {
    // The raw piece of source
    var source = unit.getSource().substring(startOffset, endOffset)

    // going through the list of comments contained in the compilation unit
    // and remove the ones contained in the piece of code
    var shiftedStartOffset = startOffset
    import scala.collection.JavaConversions._
    for (c <- getCompilationUnit(enclosingNode).getCommentList()) {
      val comment = c.asInstanceOf[Comment]
      val commentStartPosition = comment.getStartPosition()
      if (startOffset <= commentStartPosition && commentStartPosition < endOffset) {
        source = source.substring(0, commentStartPosition - shiftedStartOffset) + source.substring(commentStartPosition + comment.getLength() - shiftedStartOffset)
        shiftedStartOffset += comment.getLength()
      }
    }

    // and trim
    source.trim
  }

  /**
   * Return the CompilationUnit node containing the given node.
   */
  private def getCompilationUnit(node: ASTNode): CompilationUnit = {
    // recursively go through the node's parent until finding the CompilationUnit node.
    node match {
      case compilationUnit: CompilationUnit =>
        compilationUnit
      case _ =>
        getCompilationUnit(node.getParent())
    }
  }

  /**
   * Return the binding of the type declaration containing the given node.
   */
  private def getDeclaringTypeBinding(node: ASTNode): ITypeBinding = {
    // recursively go through the node's parent until finding a TypeDeclaration node.
    node match {
      case typeDeclaration: TypeDeclaration =>
        typeDeclaration.resolveBinding()
      case compilationUnit: CompilationUnit =>
        // no point to call this method in this case, but better be safe
        null
      case _ =>
        getDeclaringTypeBinding(node.getParent())
    }

  }

}

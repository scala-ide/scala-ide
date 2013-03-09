package scala.tools.eclipse

import org.eclipse.core.resources.IFile
import org.eclipse.jdt.core.compiler.IProblem
import scala.tools.nsc.util.BatchSourceFile
import scala.tools.nsc.util.SourceFile
import scala.tools.nsc.interactive.Response
import scala.tools.nsc.io.AbstractFile
import org.eclipse.jdt.core.WorkingCopyOwner
import org.eclipse.jdt.internal.core.DefaultWorkingCopyOwner
import org.eclipse.jdt.internal.core.SearchableEnvironment
import org.eclipse.jdt.internal.core.JavaProject

/** A Scala compilation unit. It can be backed up by a `ScalaCompilationUnit` in usual
 *  Scala projects, or any other implementation (such as a specialized Scala DSL, a
 *  Script file, an Sbt build file, etc.).
 *
 *  Implementations are expected to be thread-safe.
 */
trait InteractiveCompilationUnit {

  /** The `AbstractFile` that the Scala compiler uses to read this compilation unit. */
  def file: AbstractFile

  /** The workspace file corresponding to this compilation unit. */
  def workspaceFile: IFile

  /** Does this unit exist in the workspace? */
  def exists(): Boolean

  /** The Scala project to which this compilation unit belongs. */
  def scalaProject: ScalaProject

  /** Return a compiler `SourceFile` implementation with the given contents. The implementation decides
   *  if this is a batch file or a script/other kind of source file.
   */
  def sourceFile(contents: Array[Char] = getContents): SourceFile

  /** Reconcile the unit. Return all compilation errors.
   *
   *  Blocks until the unit is type-checked.
   */
  def reconcile(newContents: String): List[IProblem]

  /** Return all compilation errors from this unit. Waits until the unit is type-checked.
   *  It may be long running, but it won't force retype-checking. If the unit was already typed,
   *  the answer is fast.
   */
  def currentProblems(): List[IProblem]

  /** Return the current contents of this compilation unit. */
  def getContents(): Array[Char]

  /** Perform a side-effecting operation on the source file, with the current presentation compiler. */
  def doWithSourceFile(op: (SourceFile, ScalaPresentationCompiler) => Unit) {
    scalaProject.withSourceFile(this)(op)(())
  }

  /** Perform an operation on the source file, with the current presentation compiler.
   *
   *  @param op The operation to be performed
   *  @param orElse A recovery option in case the presentation compiler is not available (for instance, if it cannot be
   *                started because of classpath issues)
   */
  def withSourceFile[T](op: (SourceFile, ScalaPresentationCompiler) => T)(orElse: => T = scalaProject.defaultOrElse): T = {
    scalaProject.withSourceFile(this)(op)(orElse)
  }

  /** Schedule the unit for reconciliation. Not blocking. Used by the usual Scala editor to signal a need for `askReload`,
   *  ensuring faster response when calling `getProblems`.
   */
  def scheduleReconcile(): Response[Unit]

  /** Returns a new search name environment for the scala project.
   *
   *  @param workingCopyOwner The owner of an this Compilation Unit in working copy mode.
   */
  def newSearchableEnvironment(workingCopyOwner : WorkingCopyOwner = DefaultWorkingCopyOwner.PRIMARY) : SearchableEnvironment = {
    val javaProject = scalaProject.javaProject.asInstanceOf[JavaProject]
    javaProject.newSearchableNameEnvironment(workingCopyOwner)
  }

}
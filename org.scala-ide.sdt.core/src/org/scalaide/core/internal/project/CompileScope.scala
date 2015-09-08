package org.scalaide.core.internal.project

import java.io.File.separator

import org.eclipse.core.runtime.IPath
import org.eclipse.core.runtime.Path

/**
 * Used to carry information about scope of sources compilation in given project.
 *
 * Compilation scope is a set of sources which are compiled together and
 * a product of compilation is usually stored in scope specific output folder.<br/>
 *
 * There are defined (as a convention) 3 scopes (can be changed in future so following
 * can be treated as example in scopes defining):
 *  - macros with default source path: <project root>/src/macros* and
 *    especially for Play projects <project root>/conf*
 *  - tests with default source path: <project root>/src/test* and (because of
 *    Play projects) <project root>/test*
 *  - main which gets all source paths which don't match macros and tests scopes
 *    (* is a wildcard so for example to tests scope are assigned sources from
 *    folders src/test, src/test-my-special)
 *
 * The scopes are compiled in order: macros, main, tests. The compilation is
 * conditional so if any scope compilation fails then further scopes are not
 * compiled. So when macros compilation fails then neither main nor tests is
 * compiles. When macros compiles successfully and main fails then tests scope is not compiled.
 *
 * When given eclipse project (let name it B) depends on other project (let
 * call it A) then there are introduced additional dependencies between
 * projects' scopes:
 *  - macros of B depends on macros and main of A. So macros of B requires the
 *    correct compilations of macros and main scopes in project A
 *  - main of B depends on macros and main of A
 *  - tests of B depends on macros, main and tests of A
 *
 * Refer to [[SbtScopesBuildManager]] implementation for
 * implementation details.
 *
 * The feature is controlled by 'useScopesCompiler' exposed on 'build manager' tab in
 * Scala->Compiler preferences menu. If this flag is not set then compilation process
 * falls to old approach implemented in [[ProjectsDependentSbtBuildManager]].
 */
sealed trait CompileScope {

  /** Can be human readable name. */
  def name: String

  /** Says about what scopes kinds in upstream projects have impact on this one. */
  def dependentScopesInUpstreamProjects: Seq[CompileScope]

  /** `true` if source belongs to this compilation scope. */
  def isValidSourcePath(projectRelativePath: IPath): Boolean
  protected def isInPath(that: IPath, pathPatterns: IPath*): Boolean =
    pathPatterns.exists { _.isPrefixOf(that) }
  protected def toIPath(path: String): IPath = new Path(path)
}

case object CompileMacrosScope extends CompileScope {
  override def name: String = "macros"
  override def dependentScopesInUpstreamProjects: Seq[CompileScope] = Seq(CompileMacrosScope, CompileMainScope)
  override def isValidSourcePath(path: IPath): Boolean = isInPath(path, default, play)
  private val default = toIPath(s"src${separator}macros")
  private val play = toIPath("conf")
}

case object CompileMainScope extends CompileScope {
  override def name: String = "main"
  override def dependentScopesInUpstreamProjects: Seq[CompileScope] = Seq(CompileMacrosScope, CompileMainScope)
  override def isValidSourcePath(path: IPath): Boolean =
    !CompileMacrosScope.isValidSourcePath(path) && !CompileTestsScope.isValidSourcePath(path)
}

case object CompileTestsScope extends CompileScope {
  override def name: String = "tests"
  override def dependentScopesInUpstreamProjects: Seq[CompileScope] =
    Seq(CompileMacrosScope, CompileMainScope, CompileTestsScope)
  override def isValidSourcePath(path: IPath): Boolean = isInPath(path, default, play)
  private val default = toIPath(s"src${separator}test")
  private val play = toIPath("test")
}

object CompileScope {
  def scopesInCompileOrder: Seq[CompileScope] = Seq(CompileMacrosScope, CompileMainScope, CompileTestsScope)
}

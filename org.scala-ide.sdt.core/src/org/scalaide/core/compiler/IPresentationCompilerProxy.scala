package org.scalaide.core.compiler

/** A handle to the presentation compiler that abstracts compiler lifecycle management.
 *
 *  The Scala compiler is a managed resource. It may be shutdown when the classpath changes, etc.
 */
trait IPresentationCompilerProxy {
  /** Ask to restart the presentation compiler before processing the next request. */
  def askRestart(): Unit

  def foreach(f: IScalaPresentationCompiler => Unit): Unit = {
    apply(f)
  }

  /** Executes the passed `op` on the presentation compiler.
   *
   *  @return `None` if `op` returns `null`, `Some(value)` otherwise.
   */
  def apply[U](op: IScalaPresentationCompiler => U): Option[U]

  /** Shutdown the presentation compiler '''without''' scheduling a reconcile for the opened files.
   *
   *  In general, `shutdown` should be called only in rare occasions as, for instance, when a `project` is being deleted or closed.
   *  In fact, mind that any work item that may have been queued in the presentation compiler is effectively dropped.
   *
   *  @note If you need the presentation compiler to be re-initialized (because, for instance, you have changed the project's classpath), use `askRestart`.
   */
  def shutdown(): Unit
}
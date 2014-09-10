package org.scalaide.core
package internal.compiler

import org.scalaide.logging.HasLogger
import scala.tools.nsc.Settings
import java.util.concurrent.atomic.AtomicBoolean
import scala.reflect.internal.MissingRequirementError
import scala.reflect.internal.FatalError
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.MultiStatus
import org.eclipse.debug.core.DebugPlugin
import org.eclipse.core.runtime.Status
import org.scalaide.core.compiler._
import org.scalaide.core.compiler.IScalaPresentationCompiler
import org.scalaide.core.compiler.InteractiveCompilationUnit
import org.scalaide.ui.internal.editor.ScalaEditor

/** Holds a reference to a 'live' presentation compiler and manages its lifecycle.
  *
  * @note This class is thread-safe.
  */
final class PresentationCompilerProxy(name: String, initializeSettings: () => Settings) extends IPresentationCompilerProxy with HasLogger {

  private lazy val activityListener =
    new PresentationCompilerActivityListener(project.underlying.getName, ScalaEditor.projectHasOpenEditors(project), shutdown)

  /** Current 'live' instance of the presentation compiler.
    *
    * @note Can be `null` if no presentation compiler instance should exist for the `project`.
    * @note All access are guarded by `pcLock`.
    */
  private var pc: ScalaPresentationCompiler = null

  /** All accesses to `pc` must be guarded by this lock.
    *
    * @note Be very careful in using this lock, as it might lead to a deadlock. See #1001875 for an example,
    * which could still occur if the Eclipse workspace lock, and the `pcLock` are taken in different order.
    * This issue should be resolved, and the main idea for how to do so it's explained in #1001911.
    */
  private val pcLock = new Object

  /** Is the presentation compiler undergoing initialization.
    *
    * @note This is an internal state variable used only for easing debugging and catching early incorrect
    * changes of the presentation compiler initialization logic (see method `initialize`).
    *
    * @note All access are guarded by `pcLock`.
    */
  private var isInitializing: Boolean = false

  /** Signal that the presentation compiler should be restarted before processing the next request. */
  @volatile private var restartNextTime = false

  /** Ask to restart the presentation compiler before processing the next request. */
  def askRestart(): Unit = { restartNextTime = true }

  override def apply[U](op: IScalaPresentationCompiler => U): Option[U] =
    internal(op)

  /** This method gives access to the full Scala Presentation compiler. This should be used by
   *  internal code only.
   *
   *  Executes the passed `op` on the presentation compiler.
   *
   *  @return `None` if `op` returns `null`, `Some(value)` otherwise.
   */
  private[scalaide] def internal[U](op: ScalaPresentationCompiler => U): Option[U] = {
    def obtainPc(): ScalaPresentationCompiler = {
      var unitsToReload: List[InteractiveCompilationUnit] = Nil
      pcLock.synchronized {
        /* If `restartNextTime` is set to `true`, either initializing or restarting the presentation compiler is enough to fulfill the contract.
         * This also guarantees that if a `askRestart` call happens while `initialize` or `restart` is executed, the presentation compiler will
         * be re-initialized before serving the next `apply` call. While it is still possible to have a race-condition if a thread calls
         * `askRestart` while `initialize` is being executed in a different thread, the current implementation ensures that any subsequent call
         * to `apply` will operate on a up-to-date presentation compiler instance.
         */
        val shouldRestart = restartNextTime
        restartNextTime = false

        if (pc eq null) initialize()
        else if (shouldRestart) {
          // Before restarting, keep track of the compilation units managed by the current presentation compiler
          unitsToReload = Option(pc).map(_.compilationUnits).getOrElse(Nil)
          logger.debug("Restarting presentation compiler. The following units will be reloaded: " + unitsToReload.map(_.file.name))
          restart()
        }
      }
      /* If the pc was restarted, reload all units managed by the old pc.
       * This is done outside of the synchronized block to prevent deadlocking (see #1002003).
       *
       * However, by doing so we are introducing a race-condition: the compilation units that were managed by the former presentation compiler
       * may not be loaded in the new presentation compiler. This can happen if a concurrent presentation compiler restart request is handled
       * before the current presentation compiler was given a change to load the `unitsToReload`.
       */
      if((pc ne null) && unitsToReload.nonEmpty) pc.askReload(unitsToReload).get
      pc
    }

    activityListener.noteActivity()
    Option(obtainPc()) flatMap { pc =>
      val result = Option(op(pc))
      activityListener.noteActivity()
      result
    }
  }

  /** Updates `pc` with a new Presentation Compiler instance.
    *
    * @note Precondition: Expects `pc` to be `null`.
    */
  private def initialize(): Unit = pcLock.synchronized {
    assert(pc eq null, "Initialize should only be called when the Presentation Compiler is `null`. Otherwise, you should call `restart`.")
    /* Safety check to fail fast if we recursively call into this method */
    if (isInitializing)
      throw new RuntimeException("Recursive call during Presentation Compiler initialization")

    isInitializing = true
    try pc = create()
    finally isInitializing = false
  }

  /** Shutdown the presentation compiler, and force a re-initialization. */
  private def restart(): Unit = pcLock.synchronized {
    shutdown()
    assert(pc eq null, "There must be a race-condition if the presentation compiler instance isn't `null` right after calling `shutdown`.")
    initialize()
  }

  /** Shutdown the presentation compiler '''without''' scheduling a reconcile for the opened files.
    *
    * In general, `shutdown` should be called only in rare occasions as, for instance, when a `project` is being deleted or closed.
    * In fact, mind that any work item that may have been queued in the presentation compiler is effectively dropped.
    *
    * @note If you need the presentation compiler to be re-initialized (because, for instance, you have changed the project's classpath), use `askRestart`.
    */
  def shutdown(): Unit = {
    activityListener.stop()
    val oldPc = pcLock.synchronized {
      val temp = pc
      pc = null
      temp
    }

    if (oldPc ne null) oldPc.destroy()
  }

  /** Creates a presentation compiler instance.
   *
   *  @note Should not throw.
   */
  private def create(): ScalaPresentationCompiler = {
    pcLock.synchronized {
      try {
        val pc = new ScalaPresentationCompiler(name, initializeSettings())
        logger.debug("Presentation compiler classpath: " + pc.classPath)
        activityListener.start()
        pc
      } catch {
        case ex @ MissingRequirementError(required) =>
          eclipseLog.error(ex)
          null
        case ex @ FatalError(required) if required.startsWith("package scala does not have a member") =>
          eclipseLog.error(ex)
          null
        case ex: Throwable =>
          eclipseLog.error("Error thrown while initializing the presentation compiler.", ex)
          shutdown()
          null
      }
    }
  }
}

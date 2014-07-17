package org.scalaide.core.compiler

import org.scalaide.logging.HasLogger
import scala.tools.nsc.Settings
import java.util.concurrent.atomic.AtomicBoolean
import org.scalaide.util.internal.ui.DisplayThread
import org.scalaide.util.internal.Utils
import org.scalaide.core.internal.project.ScalaProject
import org.scalaide.core.ScalaPlugin
import scala.reflect.internal.MissingRequirementError
import scala.reflect.internal.FatalError
import java.util.Collections.synchronizedList
import org.scalaide.core.internal.project.Nature
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.MultiStatus
import org.eclipse.debug.core.DebugPlugin
import org.eclipse.core.runtime.Status

/** Holds a reference to the currently 'live' presentation compiler.
  *
  * @note Instance of this class should only be created by `ScalaProject` (in the future, we may implement
  * this restriction in the code itself, so you have been warned).
  *
  * @note This class is thread-safe.
  */
final class ScalaPresentationCompilerProxy(val project: ScalaProject) extends HasLogger {

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

  /** Executes the passed `op` on the presentation compiler.
   *
   *  @return `None` if `op` returns `null`, `Some(value)` otherwise.
   */
  def apply[U](op: ScalaPresentationCompiler => U): Option[U] = {
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
          logger.debug("Restarting presentation compiler. The following units will be reloaded: " + unitsToReload)
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

    Option(obtainPc()) flatMap (pc => Option(op(pc)))
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
    val oldPc = pcLock.synchronized {
      val temp = pc
      pc = null
      temp
    }

    if (oldPc ne null) oldPc.destroy()
  }

  private val pcInitMessageShown: AtomicBoolean = new AtomicBoolean(false)
  /** Creates a presentation compiler instance.
   *
   *  @note Should not throw.
   */
  private def create(): ScalaPresentationCompiler = {
    val pcScalaMissingStatuses = new scala.collection.mutable.ListBuffer[IStatus]()
    pcLock.synchronized {
      def updatePcStatus(msg: String, ex: Throwable) = {
        pcScalaMissingStatuses += new Status(IStatus.ERROR, ScalaPlugin.plugin.pluginId, org.scalaide.ui.internal.handlers.MissingScalaRequirementHandler.STATUS_CODE_SCALA_MISSING, msg, ex)
      }

      try {
        val settings = ScalaPlugin.defaultScalaSettings()
        project.initializeCompilerSettings(settings, isPCSetting(settings))
        val pc = new ScalaPresentationCompiler(project, settings)
        logger.debug("Presentation compiler classpath: " + pc.classPath)
        pc
      } catch {
        case ex @ MissingRequirementError(required) =>
          updatePcStatus("could not find a required class: " + required, ex)
          eclipseLog.error(ex)
          null
        case ex @ FatalError(required) if required.startsWith("package scala does not have a member") =>
          updatePcStatus("could not find a required class: " + required, ex)
          eclipseLog.error(ex)
          null
        case ex: Throwable =>
          logger.info("Throwable when intializing presentation compiler!!! " + ex.getMessage)
          ex.printStackTrace()
          if (project.underlying.isOpen) {
            updatePcStatus("error initializing the presentation compiler: " + ex.getMessage(), ex)
          }
          shutdown()
          eclipseLog.error(ex)
          null
      } finally {
        val messageShown = pcInitMessageShown.getAndSet(true)
        if (!messageShown && pcScalaMissingStatuses.nonEmpty) {
          val firstStatus = pcScalaMissingStatuses.head
          val statuses: Array[IStatus] = pcScalaMissingStatuses.tail.toArray
          val status = new MultiStatus(ScalaPlugin.plugin.pluginId, org.scalaide.ui.internal.handlers.MissingScalaRequirementHandler.STATUS_CODE_SCALA_MISSING, statuses, firstStatus.getMessage(), firstStatus.getException())
          val handler = DebugPlugin.getDefault().getStatusHandler(status)
          // Don't allow asyncExec bec. of the concurrent nature of this call,
          // we're create()-ing instances repeatedly otherwise
          if (handler != null) handler.handleStatus(status, this)
        }
      }
    }
  }

  /** Compiler settings that are honored by the presentation compiler. */
  private def isPCSetting(settings: Settings): Set[Settings#Setting] = {
    import settings.{ plugin => pluginSetting, _ }
    Set(deprecation,
      unchecked,
      pluginOptions,
      verbose,
      Xexperimental,
      future,
      Ylogcp,
      pluginSetting,
      pluginsDir,
      YpresentationDebug,
      YpresentationVerbose,
      YpresentationLog,
      YpresentationReplay,
      YpresentationDelay)
  }

}

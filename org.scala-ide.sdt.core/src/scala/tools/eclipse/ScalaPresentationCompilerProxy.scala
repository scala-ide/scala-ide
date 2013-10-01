package scala.tools.eclipse

import scala.tools.eclipse.logging.HasLogger
import scala.reflect.internal.MissingRequirementError
import scala.tools.nsc.Settings
import java.util.concurrent.atomic.AtomicBoolean
import scala.tools.eclipse.ui.DisplayThread
import scala.tools.eclipse.util.Utils

/** Holds a reference to the currently 'live' presentation compiler.
  *
  * @note Instance of this class should only be created by `ScalaProject` (in the future, we may implement
  * this restriction in the code itself, so you have been warned).
  *
  * @note This class is thread-safe.
  */
class ScalaPresentationCompilerProxy(project: ScalaProject) extends HasLogger {

  /** Current 'live' instance of the presentation compiler.
    *
    * @note Can be `null` if no presentation compiler instance should exist for the `project`.
    * @note All access are guarded by `pcLock`.
    */
  private var pc: ScalaPresentationCompiler = null

  /** All accesses to `pc` must be guarded by this lock.
    *
    * @note Be very careful in using this lock, as it might lead to a deadlock. See #1001875 for an example,
    * which could still occur if the Eclipse workspace lock, and the `pcLock` are taken in different order
    * (of course, this should not happen at the moment, but there is no way we can prevent future evolution
    * of this class to break this implicit contract).
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

  /** Executes the passed `op` on the presentation compiler. */
  def apply[U](op: ScalaPresentationCompiler => U): Option[U] = {
    def obtainPc(): ScalaPresentationCompiler = {
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
        else if (shouldRestart) restart()

        pc
      }
    }

    Option(obtainPc()) map op
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

  /** Shutdown the presentation compiler, and force a re-initialization but asking to reconcile all
    * compilation units that were serviced by the previous instance of the presentation compiler.
    */
  private def restart(): Unit = pcLock.synchronized {
    val oldPc = pc
    shutdown()
    assert(pc eq null, "There must be a race-condition if the presentation compiler instance isn't `null` right after calling `shutdown`.")
    val units = Option(oldPc).map(_.compilationUnits).getOrElse(Nil)
    initialize()
    pc.askReload(units).get
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

  /** Creates a presentation compiler instance.
    *
    * @note Should not throw.
    */
  private def create(): ScalaPresentationCompiler = pcLock.synchronized {
    try {
      val settings = ScalaPlugin.defaultScalaSettings()
      project.initializeCompilerSettings(settings, isPCSetting(settings))
      val pc = new ScalaPresentationCompiler(project, settings)
      logger.debug("Presentation compiler classpath: " + pc.classPath)
      pc.askOption(() => pc.initializeRequiredSymbols())
      pc
    }
    catch {
      case ex @ MissingRequirementError(required) =>
        failedCompilerInitialization("could not find a required class: " + required)
        eclipseLog.error(ex)
        null
      case ex: Throwable =>
        logger.info("Throwable when intializing presentation compiler!!! " + ex.getMessage)
        ex.printStackTrace()
        if (project.underlying.isOpen)
          failedCompilerInitialization("error initializing Scala compiler")
        eclipseLog.error(ex)
        null
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

  private val messageShowed: AtomicBoolean = new AtomicBoolean(false)

  // FIXME: This should really be moved somewhere else!
  private def failedCompilerInitialization(msg: String) {
    logger.debug("failedCompilerInitialization: " + msg)
    val messageAlreadyShown = messageShowed.getAndSet(true)
    if(!messageAlreadyShown && !ScalaPlugin.plugin.headlessMode) {
      DisplayThread.asyncExec {
        import org.eclipse.jface.dialogs.MessageDialog
        val doAdd = MessageDialog.openQuestion(ScalaPlugin.getShell, "Add Scala library to project classpath?",
          ("There was an error initializing the Scala compiler: %s. \n\n"+
           "The editor compiler will be restarted when the project is cleaned or the classpath is changed.\n\n" +
           "Add the Scala library to the classpath of project %s?")
          .format(msg, project.underlying.getName))
        if (doAdd) Utils.tryExecute {
          Nature.addScalaLibAndSave(project.underlying)
        }
      }
    }
  }
}
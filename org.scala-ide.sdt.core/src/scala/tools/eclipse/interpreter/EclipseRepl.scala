package scala.tools.eclipse
package interpreter

import scala.actors.Actor
import scala.collection.mutable.ListBuffer
import scala.tools.nsc.Interpreter
import scala.tools.nsc.InterpreterResults.Result
import scala.tools.nsc.Settings

/** An `EclipseRepl` is a simple Finite State Machine with 4 states based on
  * whether the REPL is running/not and whether the history `isEmpty`/not.
  *   - Z = {stopped,isEmpty} (the zero state)
  *   - R = {started,isEmpty} (running, but that's all)
  *   - H = {stopped,history} (history, but that's all)
  *   - B = {started,history} (the both state)
  *
  * Each of these transition requests affects at most one of the two axes:
  *   - `Init` and `Stop` affect the running/not axis.
  *   - `Exec` and `Drop` affect the history contents.
  * The `Quit` request can affect both axes.
  *
  * The actions performed during state transitions are composed of calls to
  * these six primitives (which are private methods on the `EclipsRepl`):
  *   - `boot` and `halt` start/stop the REPL
  *   - `add` and `zap` alter the history
  *   - `doit` feeds a line of code to the interpreter
  *   - `redo` calls `doit` for each line of code in the history
  */
object EclipseRepl
{
  /** Requests the REPL start over again using the given settings.
    * Always calls `boot`, which calls `redo` if there is any history.
    * {{{ (Z|R)->{boot}->R. (H|B)->{boot;redo}->B. }}}
    * WARNING: `Settings` will be changed to `ImmutableSettings` ASAP.
    * Until then it is up to the caller to avoid race conditions (e.g. by
    * copying the settings before sending and never changing them after).
    */
  type Init = Settings //TODO when ImmutableSettings gets written...

  /** Requests the REPL shut down, allowing it to be garbage collected.
    * {{{ Z->Z. H->H. R->{halt}->Z. B->{halt}->H. }}}
    */
  val Stop = new Object { override def toString = "Stop" }

  /** Requests the REPL run a line of code (now if running, or later as
    * part of replaying history if not).
    * Always calls `add`, which calls `doit` if the REPL is running.
    * {{{ (Z|H)->{add}->H. (R|B)->{add;doit}->B. }}}
    */
  type Exec = String

  /** Requests the REPL history be erased.
    * {{{ Z->Z. R->R. H->{zap}->Z. B->{zap}->R. }}}
    */
  val Drop = new Object { override def toString = "Drop" }

  /** Performs `Stop` and `Drop`, then de-schedules the `Actor`, allowing
    * it to be garbage collected.
    */
  val Quit = new Object { override def toString = "Quit" }

  /** Almost a `Listener`. Status calls are dispatched on the `Actor`'s thread,
    * so implementors must be careful to avoid race conditions. If the `Client`
    * is SWT-based it must use something like `SWTUtils.asyncExec`. If the
    * `Client` is another `Actor` it could send messages to itself.
    */
  trait Client
  {
    /** Sent on entry to `boot`. */
    def starting(init: Init) {}

    /** Sent at successful exit from `boot`. */
    def started(init: Init) {}

    /** Sent by `halt`. */
    def stopped() {}

    /** Sent on entry to `redo`. */
    def replaying() {}

    /** Sent at exit from `redo`. */
    def replayed() {}

    /** Sent by `zap`. */
    def dropped() {}

    /** Sent by `add`. */
    def added(exec: Exec) {}

    /** Sent on entry to `doit`. */
    def doing(exec: Exec) {}

    /** Sent at exit from `doit`. */
    def done(exec: Exec, result: Result, output: String) {}

    /** Sent at exit from the handler for `Quit`. */
    def terminating() {}

    /** Sent when something goes wrong.
      *
      * `boot` can fail if the settings are bad, in which case the REPL
      * goes to the not running state (obviously without calling `redo`).
      * {{{ (Z|R)->{boot_fail}->Z. (H|B)->{boot_fail}->H. }}}
      *
      * None of the other actions should fail under normal circumstances.
      * For example, the interpreter catches exceptions and returns a
      * `Result` of type `Error`, which `doit` reports via `done`.
      *
      * @param request the `Init`, `Exec`, etc. message
      */
    def failed(request: Any, thrown: Throwable, output: String) {}
  }

  // implementation note: request and status messages were originally like:
  //   trait Request ; trait Status
  //   case class Init(settings:Settings) extends Request
  //   case object Stop extends Request
  //   case class Starting(init:Init) extends Status
  //   case object Stopped extends Status
  // etc., etc., with `type Client = (Status => Unit)`
  // it was all very pretty, but the resulting bytecode was too bloated.
}

import EclipseRepl._

/** Wraps a Scala interpreter in an `Actor` so it can work on a background
  * thread. Because the results from executing a line of code depend upon the
  * previous lines, each `EclipseRepl` should be used only by a single entity.
  * Typically that entity is the `Client` passed to the constructor.
  * 
  * The constructor calls `Actor.start`.
  * 
  * `EclipseRepl` uses `Actor.react`, allowing it to share threads with other
  * `Actor`s. Because the interpreter might take an arbitrary amount of time to
  * calculate results this may have a negative impact on other `Actor`s. The
  * `Actor` API can be used to control thread sharing as needed.
  * 
  * These five convenience methods are available: `init`, `exec`, `drop`,
  * `stop`, and `quit`. All they do is send the corresponding request message.
  */
class EclipseRepl(client: Client) extends Actor
{
  def init(settings: Init) { this ! settings }
  def exec(line: Exec) { this ! line }
  def drop() { this ! Drop }
  def stop() { this ! Stop }
  def quit() { this ! Quit }

  import java.io.{ByteArrayOutputStream => BAOS}

  private var intp: Interpreter = null

  private def boot(i: Init, b: BAOS) {
    client.starting(i)
    intp = new Interpreter(i)
    intp.initializeSynchronous()
    redo(i, b)
    client.started(i)
  }
  private def boot_fail() {
    intp = null
  }

  private def halt(r: Any, b:BAOS) {
    if (intp != null) {
      intp = null
      client.stopped()
    }
  }

  private val hist = new ListBuffer[Exec]

  private def add(e:Exec, b: BAOS) {
    hist += e
    client.added(e)
    doit(e, b)
  }

  private def zap(r: Any, b:BAOS) {
    if (!hist.isEmpty) {
      hist.clear()
      client.dropped()
    }
  }

  private def redo(r: Any, b: BAOS) {
    if (!hist.isEmpty) {
      client.replaying()
      hist foreach { doit(_, b) }
      client.replayed()
    }
  }

  private def doit(e: Exec, b: BAOS) {
    if (intp != null) {
      client.doing(e)
      val r = intp.interpret(e)
      intp.reporter.flush()
      client.done(e, r, b.toString)
      b.reset()
    }
  }

  private def haltZap(r: Any, b:BAOS) {
    halt(r, b)
    zap(r, b)
  }

  private def recovery_unnecessary() {}

  private def safely[T](work: (T, BAOS) => Unit, r: T, recover: => Unit) {
    val b = new BAOS
    try { Console.withOut(b) { Console.withErr(b) {
      work(r, b)
    }}} catch { case t =>
      recover
      client.failed(r, t, b.toString)
    }
  }

  def act() { react {
    case e: Exec =>
      safely(add, e, recovery_unnecessary)
      act()
    case i: Init =>
      safely(boot, i, boot_fail)
      act()
    case Stop =>
      safely(halt, Stop, recovery_unnecessary)
      act()
    case Drop =>
      safely(zap, Drop, recovery_unnecessary)
      act()
    case Quit =>
      safely(haltZap, Quit, recovery_unnecessary)
      client.terminating()
      // no act() here
  }}

  this.start()
}

package scala.tools.eclipse.interpreter

import scala.actors.Actor
import scala.collection.mutable.ListBuffer
import scala.tools.nsc.interpreter.Results.Result
import scala.tools.nsc.Settings

// Unit tests found in org.scala-ide.sdt.core.tests/src:
//   scala.tools.eclipse.interpreter.EclipseReplTest

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
  /** Requests the REPL start over again using the given settings. Calls `halt`
    * if needed, then calls `boot`, which calls `redo` if there is any history.
    * {{{ Init: (R{halt}|Z)->{boot}->R. (B{halt}|H)->{boot;redo}->B. }}}
    * WARNING: `Settings` will be changed to `ImmutableSettings` ASAP.
    * Until then it is up to the caller to avoid race conditions (e.g. by
    * copying the settings before sending and never changing them after).
    */
  type Init = Settings //TODO when ImmutableSettings gets written...

  /** Requests the REPL shut down, allowing it to be garbage collected.
    * {{{ Stop: Z->Z. H->H. R->{halt}->Z. B->{halt}->H. }}}
    */
  val Stop = new Object { override def toString = "Stop" }

  /** Requests the REPL run a line of code (now if running, or later as
    * part of replaying history if not).
    * Always calls `add`, which calls `doit` if the REPL is running.
    * {{{ Exec: (Z|H)->{add}->H. (R|B)->{add;doit}->B. }}}
    */
  type Exec = String

  /** Requests the REPL history be erased.
    * {{{ Drop: Z->Z. R->R. H->{zap}->Z. B->{zap}->R. }}}
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
    /** Called just before initialization of a new Interpreter. */
    def starting(init: Init) {} // after `halt` on entry to `boot` : `Init`

    /** Called after successful initialization of a new Interpreter. */
    def started(init: Init) {} // at successful exit from `boot` : `Init`

    /** Called whenever an Interpreter is stopped. */
    def stopped() {} // by `halt` : `Init`, `Stop`, `Quit`

    /** Called after initialization before replaying of the history. */
    def replaying() {} // on entry to `redo` : `Init`

    /** Called upon completion of the replaying of the history. */
    def replayed() {} // at exit from `redo` : `Init`

    /** Called whenever the history is cleared. */
    def dropped() {} // by `zap` : `Drop`, `Quit`

    /** Called when a line of code is added to the history. */
    def added(exec: Exec) {} // by `add` : `Exec`

    /** Called just before the interpretation of a line of code. */
    def doing(exec: Exec) {} // on entry to `doit` : `Init`, `Exec`

    /** Called after interpretation of a line of code. */
    def done(exec: Exec, result: Result, output: String) {}
    // at exit from `doit` : `Init`, `Exec`

    /** Called just before the `Actor` enters its `Terminated` state. */
    def terminating() {} // at exit from the handler for `Quit`, in catch

    /** Called for any unrecognized message. */
    def unknown(request:Any) {}

    /** Called when something is thrown. The `Actor` responds to any exception
      * by behaving as if `Quit` had been called.
      *
      * `boot` can fail if the settings are bad.
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

  /** The API we use from the Scala Interpreter. @see Builder */
  trait Interpreter
  {
    /** prints the output to `Console.out` */
    def interpret(e: Exec):Result
  }

  /** Allows plugging in alternate (i.e. test) `Interpreter`s. */
  trait Builder
  {
    /** Called by `EclipseRepl` constructor, calls its `Actor.start`. */
    def constructed(a: EclipseRepl) { a.start() }

    /** Returns a new `Interpreter`. */
    def interpreter(i: Init): Interpreter
  }

  /** Returns instances of the Scala Interpreter. */
  object DefaultBuilder extends Builder
  {
    def interpreter(i: Init) = new Interpreter
    {
      val intp = new scala.tools.nsc.interpreter.IMain(i)
      intp.initializeSynchronous()

      def interpret(e: Exec) = {
        val r = intp.interpret(e)
        intp.reporter.flush()
        r }
    }
  }
}

import EclipseRepl._

/** Wraps a Scala interpreter in an `Actor` so it can work on a background
  * thread. Because the results from executing a line of code depend upon the
  * previous lines, each `EclipseRepl` should be used only by a single entity.
  * Typically that entity is the `Client` passed to the constructor.
  *
  * The default `Builder` calls `Actor.start` as the last step of the
  * `EclipseRepl` constructor.
  *
  * `EclipseRepl` uses `Actor.react`, allowing it to share threads with other
  * `Actor`s. Because the interpreter might take an arbitrary amount of time to
  * calculate results this may have a negative impact on other `Actor`s. The
  * `Actor` API can be used to control thread sharing as needed.
  *
  * These five convenience methods are available: `init`, `exec`, `drop`,
  * `stop`, and `quit`. All they do is send the corresponding request message.
  */
class EclipseRepl(client: Client, builder: Builder) extends Actor
{
  def this(client: Client) = this(client, DefaultBuilder)

  def init(settings: Init) { this ! settings }
  def exec(line: Exec) { this ! line }
  def drop() { this ! Drop }
  def stop() { this ! Stop }
  def quit() { this ! Quit }

  import java.io.{ByteArrayOutputStream => BAOS}

  private var intp: Interpreter = null

  private def boot(i: Init, b: BAOS) {
    halt(i, b)
    client.starting(i)
    intp = builder.interpreter(i)
    intp.getClass // throw new NullPointerException
    redo(b)
    client.started(i)
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

  private def redo(b: BAOS) {
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
      val o = b.toString ; b.reset()
      client.done(e, r, o)
    }
  }

  private def term(r: Any, b: BAOS) {
    halt(r, b)
    zap(r, b)
    client.terminating()
  }

  private def unkn(r: Any, b: BAOS) {
    client.unknown(r)
  }

  private def safely[T](work: (T, BAOS) => Unit, r: T, loop: Boolean) {
    val b = new BAOS
    Console.withOut(b) { Console.withErr(b) {
      try work(r, b)
      catch { case t: Throwable =>
        try {
          val o = b.toString ; b.reset()
          client.failed(r, t, o)
          term(r, b)
        } finally
          throw t // actor framework will print t
      }
    }}
    if (loop) act() // must be outside try/catch
  }

  def act() { react {
    case e: Exec => safely(add, e, true)
    case i: Init => safely(boot, i, true)
    case Stop =>    safely(halt, Stop, true)
    case Drop =>    safely(zap, Drop, true)
    case Quit =>    safely(term, Quit, false)
    case u =>       safely(unkn, u, true)
  }}

  builder.constructed(this)
}

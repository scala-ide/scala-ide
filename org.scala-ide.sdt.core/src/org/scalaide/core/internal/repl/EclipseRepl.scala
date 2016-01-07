package org.scalaide.core.internal.repl

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

import scala.collection.JavaConverters
import scala.concurrent.ExecutionContext.Implicits
import scala.concurrent.Future
import scala.tools.nsc.Settings
import scala.tools.nsc.interpreter.Results.Result

import org.scalaide.logging.HasLogger

import EclipseRepl._

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

  /** Performs `Stop` and `Drop`, then de-schedules the `Future`, allowing
    * it to be garbage collected.
    */
  val Quit = new Object { override def toString = "Quit" }

  /** Almost a `Listener`. Status calls are dispatched on the `EclipseRepl` `Future`'s thread,
    * so implementors must be careful to avoid race conditions. If the `Client`
    * is SWT-based it must use something like `SWTUtils.asyncExec`.
    */
  trait Client
  {
    /** Called just before initialization of a new Interpreter. */
    def starting(init: Init): Unit = {} // after `halt` on entry to `boot` : `Init`

    /** Called after successful initialization of a new Interpreter. */
    def started(init: Init): Unit = {} // at successful exit from `boot` : `Init`

    /** Called whenever an Interpreter is stopped. */
    def stopped(): Unit = {} // by `halt` : `Init`, `Stop`, `Quit`

    /** Called after initialization before replaying of the history. */
    def replaying(): Unit = {} // on entry to `redo` : `Init`

    /** Called upon completion of the replaying of the history. */
    def replayed(): Unit = {} // at exit from `redo` : `Init`

    /** Called whenever the history is cleared. */
    def dropped(): Unit = {} // by `zap` : `Drop`, `Quit`

    /** Called when a line of code is added to the history. */
    def added(exec: Exec): Unit = {} // by `add` : `Exec`

    /** Called just before the interpretation of a line of code. */
    def doing(exec: Exec): Unit = {} // on entry to `doit` : `Init`, `Exec`

    /** Called after interpretation of a line of code. */
    def done(exec: Exec, result: Result, output: String): Unit = {}
    // at exit from `doit` : `Init`, `Exec`

    /** Called just before the `Future` enters its `Terminated` state. */
    def terminating(): Unit = {} // at exit from the handler for `Quit`, in catch

    /** Called for any unrecognized message. */
    def unknown(request:Any): Unit = {}

    /** Called when something is thrown. The `EclipseRepl` `Future` responds to any exception
      * by behaving as if `Quit` had been called.
      *
      * `boot` can fail if the settings are bad.
      * None of the other actions should fail under normal circumstances.
      * For example, the interpreter catches exceptions and returns a
      * `Result` of type `Error`, which `doit` reports via `done`.
      *
      * @param request the `Init`, `Exec`, etc. message
      */
    def failed(request: Any, thrown: Throwable, output: String): Unit = {}
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
    /** Called by `EclipseRepl` constructor, calls its `Worker.start`. */
    def constructed(a: EclipseRepl): Unit = {}

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

/** Wraps a Scala interpreter in an `Future` so it can work on a background
  * thread. Because the results from executing a line of code depend upon the
  * previous lines, each `EclipseRepl` should be used only by a single entity.
  * Typically that entity is the `Client` passed to the constructor.
  *
  * These five convenience methods are available: `init`, `exec`, `drop`,
  * `stop`, and `quit`. All they do is send the corresponding request message.
  */
class EclipseRepl(client: Client, builder: Builder) extends HasLogger
{
  import scala.concurrent.ExecutionContext.Implicits.global

  def this(client: Client) = this(client, DefaultBuilder)

  def init(settings: Init): Future[Unit] = safely(boot, settings)
  def exec(line: Exec): Future[Unit] = safely(add, line)
  def drop(): Future[Unit] = safely(zap, Drop)
  def stop(): Future[Unit] = safely(halt, Stop)
  def quit(): Future[Unit] = safely(term(notifyClient = true), Quit)
  def unknown(un: Any): Future[Unit] = safely(unkn, un)

  import java.io.{ByteArrayOutputStream => BAOS}

  private val intpRef: AtomicReference[Interpreter] = new AtomicReference
  private def intp = intpRef.get

  private def unkn(un: Any, b: BAOS): Unit =
    client.unknown(un)

  private def boot(i: Init, b: BAOS): Unit = {
    halt(i, b)
    client.starting(i)
    intpRef.getAndSet(builder.interpreter(i))
    intp.getClass // throw new NullPointerException
    redo(b)
    client.started(i)
  }

  private def halt(r: Any, b:BAOS): Unit = {
    Option(intpRef.getAndSet(null)).foreach { _ =>
      client.stopped()
    }
  }

  import scala.collection.JavaConverters._
  private val hist = (new CopyOnWriteArrayList[Exec]).asScala

  private def add(e:Exec, b: BAOS): Unit = {
    hist += e
    client.added(e)
    doit(e, b)
  }

  private def zap(r: Any, b:BAOS): Unit = {
    if (!hist.isEmpty) {
      hist.clear()
      client.dropped()
    }
  }

  private def redo(b: BAOS): Unit = {
    if (!hist.isEmpty) {
      client.replaying()
      hist foreach { doit(_, b) }
      client.replayed()
    }
  }

  private def doit(e: Exec, b: BAOS): Unit = {
    Option(intp).map { interpreter =>
      client.doing(e)
      val r = interpreter.interpret(e)
      val o = b.toString.trim ; b.reset()
      client.done(e, r, o)
    }.orElse {
      logger.debug("interpreter not found")
      None
    }
  }

  private def term(notifyClient: Boolean)(r: Any, b: BAOS): Unit = {
    halt(r, b)
    zap(r, b)
    if (notifyClient)
      client.terminating()
  }

  private def safely[T](work: (T, BAOS) => Unit, r: T): Future[Unit] = Future {
    val b = new BAOS
    Console.withOut(b) { Console.withErr(b) {
      try {
        work(r, b)
      } catch { case t: Throwable =>
        try {
          val o = b.toString.trim ; b.reset()
          client.failed(r, t, o)
          term(notifyClient = false)(r, b)
        } finally
          logger.error("error during command processing", t)
      }
    }}
  }

  builder.constructed(this)
}

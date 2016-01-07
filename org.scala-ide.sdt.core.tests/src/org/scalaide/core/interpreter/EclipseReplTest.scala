package org.scalaide.core.interpreter

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration.DurationInt
import scala.tools.nsc.interpreter.Results.Result
import scala.tools.nsc.interpreter.Results.Success
import scala.util.Try

import org.apache.log4j.BasicConfigurator
import org.junit.Assert.assertEquals
import org.junit.Ignore
import org.junit.Test
import org.scalaide.core.internal.repl.EclipseRepl
import org.scalaide.core.internal.repl.EclipseRepl._

import EclipseReplTest._

// This test is not a regular part of org.scalaide.core.TestsSuite because
// when running under Maven the environment isn't set up quite right for the
// Scala Interpreter's special ClassLoader. At least that's my best guess as
// to the problem so far. Under Maven {new IMain} throws with the message:

// Failed to initialize compiler: object scala not found.
// ** Note that as of 2.8 scala does not assume use of the java classpath.
// ** For the old behavior pass -usejavacp to scala, or if using a Settings
// ** object programatically, settings.usejavacp.value = true.

// Note that the suggested setting does not solve the problem. All the other
// tests pass under Maven. That finicky test passes when you set up Eclipse and
// run it from there. There is another benefit to this approach: Maven isn't
// currently configured to do code coverage analysis of the unit tests. Eclemma
// was quite helpful in writing these tests (warning: Eclemma can't deal with a
// def inside another def).

// Here's an easy way to set up Eclipse: Create a new Scala project. Unzip the
// Scala compiler plugin into a folder there and add the scala-compiler.jar to
// the build path (and scala-compiler-src.jar as source attachment). Create a
// scala.tools.eclipse.interpreter package and copy this file and the
// EclipseRepl source code file to there.

// ----------

// capitalization is important...
// in the EclipseRepl public API:
//  - capitalized {Init,Stop,Exec,Drop,Quit} are message/types sent to the Future
//  - lowercase {starting,...,unknown} are listener methods on the Client
// in this file we need to write test input and expected output values:
//  - lowercase {init,stop,exec,drop,quit} are inputs for the Future functions
//  - capitalized {Starting,...,Unknown} are expected for the Client methods

class EclipseReplTest {
  BasicConfigurator.configure()
  val helloWorld = InOut("println(\"hello world\")", _ => "hello world\n")
  val onePlusOne = InOut("1+1", "res" + _ + ": Int = 2\n")

  def allTransitions: Seq[Expect] = // start in Z, go thru every possible path
    Expect(drop, stop) /*Z*/ ++ execAdded("1") /*H*/ ++ Expect(stop) ++
      execAdded("2") ++ Expect(drop, Dropped) /*Z*/ ++ initStarted /*R*/ ++
      Expect(drop, stop, Stopped) /*Z*/ ++ initStarted /*R*/ ++ Expect(Stopped) ++
      initStarted ++ helloWorld.execDone(0) /*B*/ ++ Expect(stop, Stopped) /*H*/ ++
      onePlusOne.execAdded ++ initReplay(helloWorld, onePlusOne) /*B*/ ++
      onePlusOne.execDone(2) ++ Expect(Stopped) ++
      initReplay(helloWorld, onePlusOne, onePlusOne) ++ Expect(drop, Dropped) /*R*/ ++
      onePlusOne.execDone(3) /*B*/ ++ onePlusOne.execDone(4) ++ Expect(Stopped) ++
      initReplay(onePlusOne, onePlusOne) ++ quitFromB

  def failures1: Seq[Expect] =
    Echopreter.steal(initStarted /*R*/ ++ onePlusOne.execDone(0) /*B*/ ) ++
      Expect(Stopped) ++ Failder.initFailed /*H*/ ++ quitFromH
  def failures2: Seq[Expect] =
    onePlusOne.execAdded ++ Failpreter.initReplayingFailed(onePlusOne) /*B*/ ++
      quitFromB
  def failures3: Seq[Expect] =
    Failpreter.initStarted /*R*/ ++ helloWorld.execFailed ++ quitFromB
  def failures: Seq[Seq[Expect]] =
    Seq(failures1, failures2, failures3) map Echopreter.editOutput

  def unknowns: Seq[Expect] = { // some request messages that aren't recognized
    val anys = Seq[Any](1, 0.0, true, 'J', null, new Object, new Throwable)
    val unkns = (anys map { a => Expect(bad(a), Unknown(a)) }).flatten ++ quitFromZ
    unkns
  }

  def multiple(n: Int): Seq[Seq[Expect]] = { // all the above at once
    val tests = failures ++ failures ++ failures ++ // cheap "load balancing"
      (Seq(allTransitions, unknowns, unknowns, unknowns) map Echopreter.steal)
    val stream = Stream.continually(tests.toStream).flatten
    stream.take(n * tests.size).toSeq
  }

  // start with the Echopreter testing the state machine ...

  @Test def allTransitions_Echopreter(): Unit = {
    test(Echopreter.steal(allTransitions))
  }

  @Test def failures_Failpreter(): Unit = {
    test(failures: _*)
  }

  @Test def unknowns_Echopreter(): Unit = {
    test(Echopreter.steal(unknowns))
  }

  // next use multiple EclipseRepls in parallel to test the Future stuff ...

  @Test def multiple_RTPScheduler(): Unit = {
    test(multiple(6): _*)
  }

  @Test def multiple_FJScheduler(): Unit = {
    test(multiple(8): _*)
  }

  // last rerun allTransitions with the real NSC Interpreter ...

  @Ignore("See description of failure in class head.")
  @Test def allTransitions_RealNSC(): Unit = {
    test(allTransitions)
  }
}

object EclipseReplTest {
  import org.scalaide.util.TestFutureUtil._

  def test(ses: Seq[Expect]*): Unit = {
    val rs = ses map { new Recorder(_) }
    whenReady(tryAsResult(Future.sequence {
      val reqs = rs map { _.sendRequests() }
      reqs
    })) { ignore =>
      rs foreach { _.assertMatches() }
    }
  }

  val TheOneException = new RuntimeException("TheOne") {
    // suppress the useless stack traces...
    setStackTrace(Array.empty[StackTraceElement])
  }

  trait Expect
  def Expect(es: Expect*): Seq[Expect] = es
  def replace(es: Seq[Expect], f: PartialFunction[Expect, Expect]): Seq[Expect] =
    for (e <- es) yield if (f.isDefinedAt(e)) f(e) else e

  trait Request extends Expect { def msg: Any }
  case class init(msg: Init) extends Request
  case object stop extends Request { val msg = Stop }
  case class exec(msg: Exec) extends Request
  case object drop extends Request { val msg = Drop }
  case object quit extends Request { val msg = Quit }
  case class bad(msg: Any) extends Request

  trait Reply extends Expect
  case class Starting(init: Init) extends Reply
  case class Started(init: Init) extends Reply
  case object Stopped extends Reply
  case object Replaying extends Reply
  case object Replayed extends Reply
  case object Dropped extends Reply
  case class Added(exec: Exec) extends Reply
  case class Doing(exec: Exec) extends Reply
  case class Done(exec: Exec, result: Result, output: String) extends Reply
  case object Terminating extends Reply
  case class Failed(request: Any, thrown: Throwable, output: String) extends Reply
  case class Unknown(request: Any) extends Reply

  def quitFromZ = Expect(quit, Terminating)
  def quitFromH = Expect(quit, Dropped, Terminating)
  def quitFromB = Expect(quit, Stopped, Dropped, Terminating)

  def execAdded(line: Exec) =
    Expect(exec(line), Added(line))

  def DoingFailed(line: Exec) =
    Expect(Doing(line), Failed(line, TheOneException, ""))

  case class InOut(in: String, out: (Int => String)) {
    def execAdded = EclipseReplTest.execAdded(in)
    def DoingFailed = EclipseReplTest.DoingFailed(in)
    def execFailed = execAdded ++ DoingFailed

    def DoingDone(n: Int) =
      Expect(Doing(in), Done(in, Success, out(n)))

    def execDone(n: Int) =
      execAdded ++ DoingDone(n)
  }

  trait Initialization {
    def settings: Init

    def initStarting =
      Expect(init(settings), Starting(settings))

    def initStarted =
      initStarting ++ Expect(Started(settings))

    def initReplaying =
      initStarting ++ Expect(Replaying)

    def ReplayedStarted =
      Expect(Replayed) ++ Expect(Started(settings))

    def initFailed =
      initStarting ++ Expect(Failed(settings, TheOneException, ""))

    def initReplay(ios: InOut*): Seq[Expect] =
      initReplaying ++
        ios.zipWithIndex.map { t => t._1.DoingDone(t._2) }.flatten ++
        ReplayedStarted

    def initReplayingFailed(io: InOut) =
      initReplaying ++ blame(io.DoingFailed)

    def blame(es: Seq[Expect]) =
      replace(es, { case Failed(r, t, o) => Failed(settings, t, o) })

    def steal(es: Seq[Expect]) =
      replace(es, {
        case init(_) => init(settings)
        case Starting(_) => Starting(settings)
        case Started(_) => Started(settings)
        case Failed(i: Init, t, o) => Failed(settings, t, o)
      })
  }

  object Unspecified extends Initialization {
    val settings = new Init { override def toString = "Unspecified" }
  }
  def initStarting = Unspecified.initStarting
  def initStarted = Unspecified.initStarted
  def initReplaying = Unspecified.initReplaying
  def ReplayedStarted = Unspecified.ReplayedStarted
  def initFailed = Unspecified.initFailed
  def initReplay(ios: InOut*): Seq[Expect] = Unspecified.initReplay(ios: _*)
  def initReplayingFailed(io: InOut) = Unspecified.initReplayingFailed(io)

  object Echopreter extends Initialization with Interpreter {
    val settings = new Init { override def toString = "Echopreter" }
    // just echo back each line of code as if that were the result
    def interpret(e: String) = { print(e); Success }

    def editOutput(es: Seq[Expect]) =
      replace(es, {
        case Done(e, r, _) => Done(e, r, e)
      })

    override def steal(es: Seq[Expect]) =
      super.steal(editOutput(es))
  }
  object Failpreter extends Initialization with Interpreter {
    def interpret(e: String) = throw TheOneException
    val settings = new Init { override def toString = "Failpreter" }
    override def steal(es: Seq[Expect]) =
      throw new UnsupportedOperationException()
  }
  object Failder extends Initialization {
    val settings = new Init { override def toString = "Failder" }
    override def steal(es: Seq[Expect]) =
      throw new UnsupportedOperationException()
  }

  object TestBuilder extends Builder {
    def interpreter(i: Init) =
      if (i eq Echopreter.settings) Echopreter
      else if (i eq Failpreter.settings) Failpreter
      else if (i eq Failder.settings) throw TheOneException
      else DefaultBuilder.interpreter(i)
  }

  def messageToRequest(msg: Any): Request =
    msg match {
      case i: Init => init(i)
      case Stop => stop
      case e: Exec => exec(e)
      case Drop => drop
      case Quit => quit
      case x => bad(x)
    }

  def filterRequests(es: Seq[Expect]): Seq[Request] =
    es filter { _.isInstanceOf[Request] } map { _.asInstanceOf[Request] }

  def filterReplies(es: Seq[Expect]): Seq[Reply] =
    es filter { _.isInstanceOf[Reply] } map { _.asInstanceOf[Reply] }

  class Recorder(val expected: Seq[Expect]) {
    private val buffer = new collection.mutable.ListBuffer[Expect]
    def add(e: Expect): Unit = { synchronized { buffer += e } }
    def record = { synchronized { buffer.toList } }

    val client = new Client {
      override def starting(i: Init): Unit = { add(Starting(i)) }
      override def started(i: Init): Unit = { add(Started(i)) }
      override def stopped(): Unit = { add(Stopped) }
      override def replaying(): Unit = { add(Replaying) }
      override def replayed(): Unit = { add(Replayed) }
      override def dropped(): Unit = { add(Dropped) }
      override def added(e: Exec): Unit = { add(Added(e)) }
      override def doing(e: Exec): Unit = { add(Doing(e)) }
      override def done(e: Exec, r: Result, o: String): Unit = { add(Done(e, r, o)) }
      override def terminating(): Unit = { add(Terminating) }
      override def failed(r: Any, t: Throwable, o: String): Unit = { add(Failed(r, t, o)) }
      override def unknown(request: Any): Unit = { add(Unknown(request)) }
    }

    val tested = new EclipseRepl(client, TestBuilder)

    def sendRequests(): Future[Unit] = {
      def chainFutures(seq: Seq[Request]): Future[Unit] =
        if (seq.isEmpty)
          Future.successful {}
        else
          dispatch(seq.head.msg).flatMap { unit =>
            chainFutures(seq.tail)
          }.recoverWith {
            case ignoreAllExceptions =>
              if (seq.drop(1).nonEmpty)
                chainFutures(seq.drop(1).tail)
              else chainFutures(Nil)
          }
      chainFutures(filterRequests(expected))
    }

    private def dispatch(msg: Any): Future[Unit] = {
      add(messageToRequest(msg))
      msg match {
        case e: Exec => tested.exec(e)
        case i: Init => tested.init(i)
        case Stop => tested.stop()
        case Drop => tested.drop()
        case Quit => tested.quit()
        case un => tested.unknown(un)
      }
    }

    def assertMatches(): Unit = {
      val actual = record
      val expectedRequests = filterRequests(expected).toList
      val actualRequests = filterRequests(actual).toList
      val expectedReplies = filterReplies(expected).toList
      val actualReplies = filterReplies(actual).toList
      assertEquals("requests", expectedRequests, actualRequests)
      assertEquals("replies", expectedReplies, actualReplies)
    }
  }
}

package org.scalaide.util.internal

import java.io.PrintStream
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jdt.core.IClassFile
import org.eclipse.jdt.core.ICodeAssist
import org.eclipse.jdt.core.IJavaElement
import org.eclipse.jdt.core.WorkingCopyOwner
import org.eclipse.jdt.internal.core.Openable
import java.io.File

/**
 * This type exists only to suppress warnings of scalac. One should try to not
 * add new definitions here, but to remove them and replace them with behavior
 * that doesn't produce warnings. This may not always be possible, however.
 *
 * If one wants to suppress a deprecation warning, one can reference it through
 * `Suppress.DeprecationWarning.ref`, where `ref` is an alias that is not
 * deprecated but refers to a deprecated definition.
 */
object Suppress {

  /*
   * See [[https://issues.scala-lang.org/browse/SI-7934]] for an explanation of
   * this exploit.
   */
  @deprecated("This type allows to suppress deprecation warnings of scalac", "")
  sealed class DeprecatedWarning {
    type IBufferFactory = org.eclipse.jdt.core.IBufferFactory
    type ICodeCompletionRequestor = org.eclipse.jdt.core.ICodeCompletionRequestor
    type ICompletionRequestor = org.eclipse.jdt.core.ICompletionRequestor
    type Actor = scala.actors.Actor
    val Actor = scala.actors.Actor
    type DaemonActor = scala.actors.DaemonActor
    type AbstractActor = scala.actors.AbstractActor
    type Exit = scala.actors.Exit
    val State = scala.actors.Actor.State
    type OutputChannel[A] = scala.actors.OutputChannel[A]
    type IScheduler = scala.actors.IScheduler
    type SingleThreadedScheduler = scala.actors.scheduler.SingleThreadedScheduler
    type ResizableThreadPoolScheduler = scala.actors.scheduler.ResizableThreadPoolScheduler
    type ForkJoinScheduler = scala.actors.scheduler.ForkJoinScheduler
    type Lock = scala.concurrent.Lock
    type SourceCodeEditor = org.scalaide.ui.editor.SourceCodeEditor

    /** Refers to deprecated Eclipse/JDT API */
    def getBufferFactory(o: Openable): IBufferFactory =
      o.getBufferFactory

    /** Refers to deprecated Eclipse/JDT API */
    def codeComplete(o: ICodeAssist, offset: Int, requestor: ICodeCompletionRequestor): Unit =
      o.codeComplete(offset, requestor)

    /** Refers to deprecated Eclipse/JDT API */
    def codeComplete(o: ICodeAssist, offset: Int, requestor: ICompletionRequestor): Unit =
      o.codeComplete(offset, requestor)

    /** Refers to deprecated Eclipse/JDT API */
    def codeComplete(o: ICodeAssist, offset: Int, requestor: ICompletionRequestor, owner: WorkingCopyOwner): Unit =
      o.codeComplete(offset, requestor, owner)

    /** Refers to deprecated Eclipse/JDT API */
    def getWorkingCopy(o: IClassFile, monitor: IProgressMonitor, factory: IBufferFactory): IJavaElement =
      o.getWorkingCopy(monitor, factory)

    def `Console.setOut`(out: PrintStream): Unit =
      Console.setOut(out)

    def `Console.setErr`(out: PrintStream): Unit =
      Console.setErr(out)

    type AggressiveCompile = sbt.compiler.AggressiveCompile
    def aggressivelyCompile(agg: AggressiveCompile)(implicit log: sbt.Logger) = agg.apply _
  }
  object DeprecatedWarning extends DeprecatedWarning
}

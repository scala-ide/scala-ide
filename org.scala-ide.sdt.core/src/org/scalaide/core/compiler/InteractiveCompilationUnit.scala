package org.scalaide.core
package compiler

import org.eclipse.core.resources.IFile
import org.eclipse.jdt.core.compiler.IProblem
import scala.reflect.internal.util.SourceFile
import scala.tools.nsc.interactive.Response
import scala.tools.nsc.io.AbstractFile
import org.scalaide.core.IScalaProject
import org.scalaide.core.extensions.ReconciliationParticipantsExtensionPoint

/** This trait represents a possibly translated Scala source. In the default case,
 *  the original and Scala sources and positions are the same.
 *
 *  This trait allows implementers to specify on-the-fly translations from any source ('original')
 *  to Scala source. For example, Play templates are an HTML-based format with Scala snippets that
 *  are translated to Scala source. If this trait is correctly implemented, the corresponding
 *  compilation unit can perform 'errors-as-you-type', hyperlinking, completions, hovers.
 *
 *  The presentation compiler will rely on this trait to translate offsets or regions to and from
 *  original and Scala sources.
 *
 *  Implementations of this trait should be immutable and thread-safe.
 */
trait ISourceMap {
  /** The original source contents, for example the Play HTML template source */
  def originalSource: Array[Char]

  /** The translated Scala source code, for example the translation of a Play HTML template. */
  def scalaSource: Array[Char] = originalSource

  /** Map from the original source into the corresponding position in the Scala translation. */
  def scalaPos: IPositionInformation

  /** Map from Scala source to its equivalent in the original source. */
  def originalPos: IPositionInformation

  /** Translate the line number from original to target line. Lines are 0-based. */
  def scalaLine(line: Int): Int =
    scalaPos.offsetToLine(scalaPos(originalPos.lineToOffset(line)))

  /** Translate the line number from Scala to original line. Lines are 0-based. */
  def originalLine(line: Int): Int =
    originalPos.offsetToLine(originalPos(scalaPos.lineToOffset(line)))

  /** Return a compiler `SourceFile` implementation with the given contents. The implementation decides
   *  if this is a batch file or a script/other kind of source file.
   */
  def sourceFile: SourceFile
}

object ISourceMap {
  /** A plain Scala source map implementation based on the given file and contents.
   *
   *  This implementation performs no transformation on the given source code.
   */
  def plainScala(file: AbstractFile, contents: Array[Char]): ISourceMap =
    new internal.compiler.PlainScalaInfo(file, contents)
}

/** Position information relative to a source transformation.
 *
 *  This translates sources from an original source to a target source,
 *  and performs offset to line manipulations.
 *
 *  All methods may throw `IndexOutOfBoundsException` if the given input is invalid.
 */
trait IPositionInformation extends (Int => Int) {
  /** Map the given offset to the target offset. */
  def apply(offset: Int): Int

  /** Return the line number corresponding to this offset. */
  def offsetToLine(offset: Int): Int

  /** Return the offset corresponding to this line number. */
  def lineToOffset(line: Int): Int
}

object IPositionInformation {

  /** A plain Scala implementation based on the given source file.
   *
   *  This performs no transformation on positions.
   */
  def plainScala(sourceFile: SourceFile): IPositionInformation =
    new internal.compiler.PlainScalaPosition(sourceFile)
}

/** A Scala compilation unit. It can be backed up by a `ScalaCompilationUnit` in usual
 *  Scala projects, or any other implementation (such as a specialized Scala DSL, a
 *  Script file, an Sbt build file, etc.).
 *
 *  This class is a stable representation of a compilation unit. Its contents may change over time, but
 *  *snapshots* can be obtained through `sourceMap`.
 *
 *  An `ISourceMap` is a translation from any surface language (for example, Play HTML templates) to a
 *  Scala source that can be type-checked by the Scala presentation compiler.
 *
 *  Implementations are expected to be thread-safe.
 */
trait InteractiveCompilationUnit {

  /** The `AbstractFile` that the Scala compiler uses to read this compilation unit. It should not change through the lifetime of this unit. */
  def file: AbstractFile

  /** Return the source info for the given contents. */
  def sourceMap(contents: Array[Char]): ISourceMap

  /** Return the most recent available source map for the current contents. */
  def lastSourceMap(): ISourceMap

  /** Return the current contents of this compilation unit. This is the 'original' contents, that may be
   *  translated to a Scala source using `sourceMap`.
   *
   *  If we take Play templates as an example, this method would return HTML interspersed with Scala snippets. If
   *  one wanted the translated Scala source, he'd have to call `lastSourceMap().scalaSource`
   */
  def getContents(): Array[Char]

  /** The workspace file corresponding to this compilation unit. */
  def workspaceFile: IFile

  /** Does this unit exist in the workspace? */
  def exists(): Boolean

  /** The Scala project to which this compilation unit belongs. */
  def scalaProject: IScalaProject

  /** Schedule this unit for reconciliation with the new contents. This by itself won't start
   *  a new type-checking round, instead marks the current unit as *dirty*. At the next reconciliation
   *  round (typically after 500ms of inactivity), all dirty units are flushed and all managed
   *  units are type-checked again.
   *
   *  @param newContents The new contents of this compilation unit. This is the original source, and
   *                     may not be Scala. This method takes care of translating the contents to Scala
   */
  def scheduleReconcile(newContents: Array[Char]): Unit = {
    scalaProject.presentationCompiler { pc =>
      pc.scheduleReload(this, sourceMap(newContents).sourceFile)
    }
  }

  /** Force a reconciliation round. This involves flushing all pending (dirty) compilation
   *  units and waiting for this compilation unit to be type-checked. It returns all compilation
   *  problems corresponding to this unit.
   *
   *  @note This is usually called from an active editor that needs to update error annotations.
   *        Other code should prefer calling `currentProblems`, which won't interfere with the
   *        reconciliation strategy.
   */
  def forceReconcile(): List[ScalaCompilationProblem] = {
    scalaProject.presentationCompiler(_.flushScheduledReloads())
    currentProblems()
  }

  /** Schedule the unit for reconciliation and add it to the presentation compiler managed units. This should
   *  be called before any other calls to {{{IScalaPresentationCompiler.scheduleReload}}}
   *
   *  This method is the entry-point to the managed units in the presentation compiler: it should perform an initial
   *  askReload and add the unit to the managed set, so from now on `scheduleReload` can be used instead.
   *
   *  This method should not block.
   */
  def initialReconcile(): Response[Unit] = {
    val reloaded = scalaProject.presentationCompiler { compiler =>
      compiler.askReload(this, sourceMap(getContents).sourceFile)
    } getOrElse {
      val dummy = new Response[Unit]
      dummy.set(())
      dummy
    }

    reloaded
  }

  /** Return all compilation errors from this unit. Waits until the unit is type-checked.
   *  It may be long running, but it won't force retype-checking. If the unit was already typed,
   *  the answer is fast.
   *
   *  Compilation errors and warnings are positioned relative to the original source.
   */
  def currentProblems(): List[ScalaCompilationProblem] = {
    import scala.util.control.Exception.failAsValue

    scalaProject.presentationCompiler { pc =>
      val info = lastSourceMap()
      import info._

      val probs = pc.problemsOf(this)
      for (p <- probs) yield {
        p.copy(start = failAsValue(classOf[IndexOutOfBoundsException])(0)(originalPos(p.start)),
          end = failAsValue(classOf[IndexOutOfBoundsException])(1)(originalPos(p.end)),
          lineNumber = failAsValue(classOf[IndexOutOfBoundsException])(1)(originalLine(p.lineNumber - 1)) + 1)
      }
    }.getOrElse(Nil)
  }

  /** Perform an operation on the source file, with the current presentation compiler.
   *
   *  @param op The operation to be performed
   */
  def withSourceFile[T](op: (SourceFile, IScalaPresentationCompiler) => T): Option[T] = {
    scalaProject.presentationCompiler(op(lastSourceMap().sourceFile, _))
  }
}

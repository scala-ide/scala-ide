package scala.tools.eclipse.markoccurrences

import scala.tools.eclipse.InteractiveCompilationUnit
import scala.tools.eclipse.logging.HasLogger
import scala.reflect.internal.util.SourceFile
import scala.tools.refactoring.analysis.GlobalIndexes
import scala.tools.refactoring.implementations.MarkOccurrences
import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.Region
import scala.tools.eclipse.util.Utils
import scala.tools.eclipse.ScalaPresentationCompiler
import scala.ref.WeakReference

case class Occurrences(name: String, locations: List[IRegion])

/** Finds all occurrences of a binding in a Scala source file.
 *
 * Note that the occurrences index is re-computed only if the document's modification timestamp
 * has changed.
 *
 * This class is thread-safe.
 */
class ScalaOccurrencesFinder(unit: InteractiveCompilationUnit) extends HasLogger {
  import ScalaOccurrencesFinder._

  /* IMPORTANT:
   * In the current implementation, all access to `indexCache` are confined to the Presentation
   * Compiler thread (and this is why `indexCache` requires no synchronization policy).
   */
  private var indexCache: Option[TimestampedIndex] = None

  private def getCachedIndex(lastModified: Long, currentCompiler: ScalaPresentationCompiler): Option[MarkOccurrencesIndex] = indexCache match {
    case Some(TimestampedIndex(`lastModified`, WeakReference(index))) if index.global eq currentCompiler => Some(index)
    case _ =>
      logger.info("No valid MarkOccurrences index.")
      None
  }

  /** We store the index in a weak reference. This way we trade-off memory consumption with
   *  speed of execution: if the editor stays open for a long time (for instance, the user is
   *  hyperlinking around), the VM might need more memory and it's a bad idea to hold on to
   *  an index that can be easily recomputed.
   */
  private def cacheIndex(lastModified: Long, index: MarkOccurrencesIndex): Unit = {
    indexCache = Some(TimestampedIndex(lastModified, new WeakReference(index)))
  }

  def findOccurrences(region: IRegion, lastModified: Long): Option[Occurrences] = {
    unit.withSourceFile { (sourceFile, compiler) =>

      def isNotLoadedInPresentationCompiler(source: SourceFile): Boolean =
        !compiler.unitOfFile.contains(source.file)

      if (isNotLoadedInPresentationCompiler(sourceFile)) {
        logger.info("Source %s is not loded in the presentation compiler. Aborting occurrences update." format (sourceFile.file.name))
        None
      } else {
        val occurrencesIndex = getCachedIndex(lastModified, compiler) getOrElse {
          val occurrencesIndex = new MarkOccurrencesIndex {
            val global = compiler
            override val index: IndexLookup = Utils.debugTimed("Time elapsed for building mark occurrences index in source " + sourceFile.file.name) {
              global.loadedType(sourceFile) match {
                case Left(tree) => compiler.askOption { () => GlobalIndex(tree) } getOrElse EmptyIndex
                case Right(ex)  => EmptyIndex
              }
            }
          }
          cacheIndex(lastModified, occurrencesIndex)
          occurrencesIndex
        }
        compiler.askOption { () =>
          val (from, to) = (region.getOffset, region.getOffset + region.getLength)
          val (selectedTree, occurrences) = occurrencesIndex.occurrencesOf(sourceFile.file, from, to)

          Option(selectedTree.symbol) filter (!_.name.isOperatorName) map { sym =>
            val locations = occurrences map { pos =>
              new Region(pos.startOrPoint, pos.endOrPoint - pos.startOrPoint)
            }
            Occurrences(sym.nameString, locations)
          }
        }
      } getOrElse None
    }.flatten
  }

  /** Backported for 2.9 from the 2.10 implementation. TODO: Remove when we drop 2.9 support. */
  private object WeakReference {
    /** Optionally returns the referenced value, or `None` if that value no longer exists */
    def unapply[T <: AnyRef](wr: WeakReference[T]): Option[T] = {
      val x = wr.underlying.get
      if (x != null) Some(x) else None
    }
  }
}

object ScalaOccurrencesFinder {
  private abstract class MarkOccurrencesIndex extends MarkOccurrences with GlobalIndexes
  private case class TimestampedIndex(timestamp: Long, index: WeakReference[MarkOccurrencesIndex])
}
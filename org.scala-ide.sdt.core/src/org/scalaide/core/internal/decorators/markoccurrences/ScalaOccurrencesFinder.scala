package org.scalaide.core.internal.decorators.markoccurrences

import org.scalaide.core.compiler.InteractiveCompilationUnit
import org.scalaide.logging.HasLogger
import scala.reflect.internal.util.SourceFile
import scala.tools.refactoring.analysis.GlobalIndexes
import scala.tools.refactoring.implementations.MarkOccurrences
import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.Region
import org.scalaide.util.Utils
import org.scalaide.core.compiler.IScalaPresentationCompiler.Implicits._

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

  def findOccurrences(region: IRegion, lastModified: Long): Option[Occurrences] = {
    unit.withSourceFile { (sourceFile, compiler) =>

      def isNotLoadedInPresentationCompiler(source: SourceFile): Boolean =
        !compiler.unitOfFile.contains(source.file)

      if (isNotLoadedInPresentationCompiler(sourceFile)) {
        logger.info("Source %s is not loded in the presentation compiler. Aborting occurrences update." format (sourceFile.file.name))
        None
      } else {
        val occurrencesIndex =  {
          val occurrencesIndex = new MarkOccurrencesIndex {
            val global = compiler
            import global.askLoadedTyped
            override val index: IndexLookup = Utils.debugTimed("Time elapsed for building mark occurrences index in source " + sourceFile.file.name) {
              askLoadedTyped(sourceFile, keepLoaded = false).get match {
                case Left(tree) => compiler.asyncExec(GlobalIndex(tree)).getOrElse(EmptyIndex)()
                case Right(ex)  => EmptyIndex
              }
            }
          }
          occurrencesIndex
        }

        compiler.asyncExec {
          val (from, to) = (region.getOffset, region.getOffset + region.getLength)
          val (selectedTree, occurrences) = occurrencesIndex.occurrencesOf(sourceFile.file, from, to)

          Option(selectedTree.symbol).map { sym =>
            val locations = occurrences map { pos =>
              new Region(pos.start, pos.end - pos.start)
            }
            Occurrences(sym.nameString, locations)
          }
        }
      }.getOrElse(None)()
    }.flatten
  }
}

object ScalaOccurrencesFinder {
  private abstract class MarkOccurrencesIndex extends MarkOccurrences with GlobalIndexes
}

package scala.tools.eclipse.markoccurrences

import scala.tools.refactoring.common.Selections
import org.eclipse.jface.text.Region
import org.eclipse.jface.text.ITextSelection
import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.nsc.interactive.Global
import scala.tools.refactoring.analysis.GlobalIndexes

case class Occurrences(name: String, locations: List[Region])

class ScalaOccurrencesFinder(file: ScalaSourceFile, offset: Int, length: Int) {

  def findOccurrences(): Option[Occurrences] = {
    val (from, to) = (offset, offset + length)
    file.withSourceFile { (sourceFile, compiler) =>
      val analysis = new Selections with GlobalIndexes {
        val global = compiler
        val index = GlobalIndex(Nil)
      }
      import analysis._
      compiler.ask { () =>
        if (!compiler.unitOfFile.contains(sourceFile.file)) None else {
          val selection = new FileSelection(sourceFile.file, from, to)
          selection.selectedSymbolTree flatMap { selectedLocal =>
            val symbol = selectedLocal.symbol
            if (symbol.name.isOperatorName)
              None
            else {
              val index = GlobalIndex(global.unitOf(sourceFile).body)
              val positions = index occurences symbol map (_.namePosition) filter (_ != global.NoPosition) map (p => (p.start, p.end - p.start))
              val symbolName = symbol.nameString
              val locations = positions collect { case (offset, length) if length == symbolName.length => new Region(offset, length) }
              Some(Occurrences(symbolName, locations.toList))
            }
          }
        }
      }
    }
  }
}


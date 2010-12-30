package scala.tools.eclipse.markoccurrences

import scala.tools.refactoring.common.Selections
import scala.tools.refactoring.implementations.MarkOccurrences
import org.eclipse.jface.text.Region
import org.eclipse.jface.text.ITextSelection
import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.nsc.interactive.Global
import scala.tools.refactoring.analysis.GlobalIndexes

case class Occurrences(name: String, locations: List[Region])

class ScalaOccurrencesFinder(file: ScalaSourceFile, offset: Int, length: Int) {

  def findOccurrences(): Option[Occurrences] = {

    val (from, to) = (offset, offset + length)
    file.withCompilerResult { crh =>
    
      val mo = new MarkOccurrences with GlobalIndexes {
        val global = crh.compiler
        lazy val index = GlobalIndex(global.unitOf(crh.body.pos.source).body)
      }
      
      val (selectedTree, os) = mo.occurrencesOf(crh.body.pos.source.file, from, to)
      
      val symbol = selectedTree.symbol
      
      if (symbol == null || symbol.name.isOperatorName)
        return None

      val symbolName = selectedTree.symbol.nameString
      
      val positions = os map (p => (p.start, p.end - p.start))
      val locations = positions collect { case (offset, length) if length == symbolName.length => new Region(offset, length) }
      
      Some(Occurrences(symbolName, locations.toList))
    }
  }
}


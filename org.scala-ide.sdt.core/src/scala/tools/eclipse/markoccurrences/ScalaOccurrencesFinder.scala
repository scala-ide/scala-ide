package scala.tools.eclipse.markoccurrences

import scala.tools.refactoring.common.Selections
import scala.tools.refactoring.implementations.MarkOccurrences
import org.eclipse.jface.text.Region
import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.refactoring.analysis.GlobalIndexes
import scala.tools.eclipse.util.Defensive

case class Occurrences(name: String, locations: List[Region])

class ScalaOccurrencesFinder(file: ScalaSourceFile, offset: Int, length: Int) {

  def findOccurrences(): Option[Occurrences] = {
    if (!Defensive.check(offset > -1, "offset(%d) > -1", offset)) return None
    if (!Defensive.check(length > -1, "length(%d) > -1", length)) return None
    val (from, to) = (offset, offset + length)
    file.withSourceFile { (sourceFile, compiler) =>
      compiler.ask { () =>
        Defensive.tryOrLog[Option[Occurrences]](None){
          val mo = new MarkOccurrences with GlobalIndexes {
            val global = compiler
            lazy val index = GlobalIndex(global.body(sourceFile))
          }
  
          if (!compiler.unitOfFile.contains(sourceFile.file)) None else {
            val (selectedTree, os) = mo.occurrencesOf(sourceFile.file, from, to)
            
            val symbol = selectedTree.symbol
            if (symbol == null || symbol.name.isOperatorName) None else {
            	val symbolName = selectedTree.symbol.nameString
            	val positions = os map (p => (p.start, p.end - p.start))
            	val locations = positions collect { case (offset, length) if length == symbolName.length => new Region(offset, length) }
            	Some(Occurrences(symbolName, locations.toList))
            }
          }
        }
      }
    }
  }
}


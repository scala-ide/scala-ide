package scala.tools.eclipse
package markoccurrences

import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.tools.refactoring.analysis.GlobalIndexes
import scala.tools.refactoring.implementations.MarkOccurrences

import org.eclipse.jface.text.Region

case class Occurrences(name: String, locations: List[Region])

class ScalaOccurrencesFinder(file: ScalaCompilationUnit, offset: Int, length: Int) {

  def findOccurrences(): Option[Occurrences] = {
    val (from, to) = (offset, offset + length)
    file.withSourceFile { (sourceFile, compiler) =>
      compiler.askOption { () =>
        val mo = new MarkOccurrences with GlobalIndexes {
          val global = compiler
          lazy val index = GlobalIndex(global.body(sourceFile))
        }

        if (!compiler.unitOfFile.contains(sourceFile.file)) 
          None 
        else {
          val (selectedTree, os) = mo.occurrencesOf(sourceFile.file, from, to)          
          val symbol = selectedTree.symbol
          if (symbol == null || symbol.name.isOperatorName) 
            None 
          else {
          	val symbolName = selectedTree.symbol.nameString
          	val positions = os map (p => (p.start, p.end - p.start))
          	val locations = positions collect { case (offset, length) if length == symbolName.length => new Region(offset, length) }
          	Some(Occurrences(symbolName, locations.toList))
          }
        }
      } getOrElse None
    }(None)
  }
}


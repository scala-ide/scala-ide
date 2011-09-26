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
          val (selectedTree, occurrences) = mo.occurrencesOf(sourceFile.file, from, to)       
          
          Option(selectedTree.symbol) filter (!_.name.isOperatorName) map { sym =>
            val locations = occurrences map { pos => 
              new Region(pos.start, pos.end - pos.start)
            }
            Occurrences(sym.nameString, locations)
          }
        }
      } getOrElse None
    }(None)
  }
}


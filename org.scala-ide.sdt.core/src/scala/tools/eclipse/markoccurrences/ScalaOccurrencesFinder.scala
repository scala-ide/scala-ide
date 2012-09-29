package scala.tools.eclipse
package markoccurrences

import org.eclipse.jface.text.Region

import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.tools.nsc.util.SourceFile
import scala.tools.refactoring.analysis.GlobalIndexes
import scala.tools.refactoring.implementations.MarkOccurrences

case class Occurrences(name: String, locations: List[Region])

object ScalaOccurrencesFinder {
  
  private var indexCache: Option[(SourceFile, Long, MarkOccurrences with GlobalIndexes)] = None
  
  private def getCachedIndex(sourceFile: SourceFile, lastModified: Long) = indexCache match {
    case Some(Triple(`sourceFile`, `lastModified`, index)) => Some(index)
    case _ => None
  }
  
  private def cacheIndex(sourceFile: SourceFile, lastModified: Long, index: MarkOccurrences with GlobalIndexes) {
    indexCache = Some(Triple(sourceFile, lastModified, index))
  }
  
  def findOccurrences(file: ScalaCompilationUnit, offset: Int, length: Int, lastModified: Long): Option[Occurrences] = {
    val (from, to) = (offset, offset + length)
    file.withSourceFile { (sourceFile, compiler) =>
      compiler.askOption { () =>
        
        val occurrencesIndex = getCachedIndex(sourceFile, lastModified) getOrElse {
          val occurrencesIndex = new MarkOccurrences with GlobalIndexes {
            val global = compiler
            lazy val index = GlobalIndex(global.body(sourceFile))
          }
          cacheIndex(sourceFile, lastModified, occurrencesIndex)
          occurrencesIndex
        }
        
        if (!compiler.unitOfFile.contains(sourceFile.file)) 
          None 
        else {
          val (selectedTree, occurrences) = occurrencesIndex.occurrencesOf(sourceFile.file, from, to)       
          
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


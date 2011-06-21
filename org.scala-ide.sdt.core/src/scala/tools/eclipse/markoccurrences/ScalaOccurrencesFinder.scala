package scala.tools.eclipse
package markoccurrences

import scala.tools.nsc.interactive.FreshRunReq
import scala.tools.refactoring.common.Selections
import scala.tools.refactoring.implementations.MarkOccurrences
import org.eclipse.jface.text.Region
import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.refactoring.analysis.GlobalIndexes
import scala.collection.mutable.WeakHashMap

case class Occurrences(name: String, locations: List[Region])

class ScalaOccurrencesFinder(file: ScalaSourceFile, offset: Int, length: Int) {

  def findOccurrences(): Option[Occurrences] = {
    val (from, to) = (offset, offset + length)
    file.withSourceFile { (sourceFile, compiler) =>
      compiler.askOption { () =>
        if (!compiler.unitOfFile.contains(sourceFile.file)) 
          None 
        else {
                  
          val tree = compiler.body(sourceFile)
          
          val markOccurrences = if(cachedMarkOccurrences.contains(tree)) {
            cachedMarkOccurrences(tree)
          } else {
            val markOccurrences = new MarkOccurrences with GlobalIndexes {
              val global: compiler.type = compiler
              lazy val index = GlobalIndex(tree)
            }     
            cachedMarkOccurrences.put(tree, markOccurrences)
            markOccurrences
          }
          
          val (selectedTree, os) = markOccurrences.occurrencesOf(sourceFile.file, from, to)          
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

private [markoccurrences] object cachedMarkOccurrences {
  private val cache = new WeakHashMap[tools.nsc.Global#Tree, MarkOccurrences with GlobalIndexes]()
  def contains(t: tools.nsc.Global#Tree) = cache.contains(t)
  def apply(t: tools.nsc.Global#Tree) = cache(t)
  def put(t: tools.nsc.Global#Tree, mo: MarkOccurrences with GlobalIndexes) = cache.put(t, mo)
}

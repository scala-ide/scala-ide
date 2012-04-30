package scala.tools.eclipse
package refactoring
package rename

import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.ltk.core.refactoring.participants.{ CheckConditionsContext, RenameParticipant => LtkRenameParticipant }
import org.eclipse.ltk.core.refactoring.{ Change, RefactoringStatus }
import org.eclipse.core.resources.IFile
import scala.tools.eclipse.javaelements.ScalaSourceFile
import tools.nsc.util.{ NoPosition, Position, RangePosition }
import org.eclipse.ltk.core.refactoring.CompositeChange
import scala.tools.eclipse.util.FileUtils
import scala.tools.refactoring.common.TextChange

/**
 * This rename participant hooks into the JDT's Rename File refactoring and renames the
 * class inside the file using the Scala Rename refactoring.
 *
 * The class in the file is only renamed when it is at the top level (i.e. not nested)
 * and has the same name as the file. When the class has a companion object, it is renamed
 * as well, this is already done in the refactoring.
 * 
 */
class RenameParticipant extends LtkRenameParticipant {

  val getName = "Scala Rename Refactoring"
  
  /**
   * This is the first step in the refactoring. We check whether the class should
   * be renamed, and because we already have to search for the class or object
   * definition, we keep its found position so we can use it later when actually
   * performing the refactoring.
   */
  def initialize(element: Object) = element match {
    case file: IFile =>
      getPositionOfClassToRename(file) match {
        case NoPosition => 
          false
        case pos: RangePosition =>
          classBeingRenamed = Some(file -> pos)
          true
      }
    case _ => 
      false
  }

  /** 
   * This is the second step: we generate the changes and check whether any problems occurred.
   */
  def checkConditions(pm: IProgressMonitor, context: CheckConditionsContext): RefactoringStatus = {
    
    def newName = {
      val FileNamePattern = """(.*)\.scala""".r
      val FileNamePattern(name) = getArguments.getNewName
      name
    }
    
    classBeingRenamed flatMap {
      case (file, pos) =>
        ScalaSourceFile.createFromPath(file.getFullPath.toOSString) map { scalaSourceFile =>
          
          // we can reuse the already existing rename action
          val renameRefactoring = {
            val renameAction = new GlobalRenameAction
            new renameAction.RenameScalaIdeRefactoring(pos.start, pos.start + file.getName.length, scalaSourceFile)
          }
          
          import renameRefactoring._
          
          val initialConditions = checkInitialConditions(pm)
          name = newName
          val finalConditions = checkFinalConditions(pm)
          
          if(pm.isCanceled) {
            // when the user cancelled we still want to do the refactoring,
            // but we skip renaming the class in the source files.
            pm.setCanceled(false)
          } else {
            change = new CompositeChange("Rename Scala Class") {
              val changes = performRefactoring() collect {
                case tc: TextChange => tc
              }
              scalaChangesToEclipseChanges(changes) foreach add
            }
          }
          
          renameRefactoring.cleanup()
          
          new RefactoringStatus {
            initialConditions.getEntries ++ finalConditions.getEntries foreach addEntry 
             
            if(!refactoring.isValidIdentifier(newName)) {
              addError("'"+ newName +"' is not a valid Scala identifier name")
            }
          }
        }
    } getOrElse null
  }

  /** 
   * This is the third step. We return the change or null if it couldn't be generated.
   * 
   * The refactoring needs to be executed before the file is renamed, otherwise the 
   * underlying IFile changes and the refactoring is applied to the old file.
   */
  override def createPreChange(pm: IProgressMonitor)= change
  
  def createChange(pm: IProgressMonitor)= null
  
  private var classBeingRenamed: Option[(IFile, RangePosition)] = None

  private var change: Change = null
  
  /**
   * Returns the position of the class we can rename or NoPosition if no
   * suitable class (top-level, same name as the file) can be found. 
   */
  private def getPositionOfClassToRename(file: IFile): Position = {
    ScalaSourceFile.createFromPath(file.getFullPath.toOSString) flatMap {
      _.withSourceFile { (scalaSourceFile, compiler) =>

        import compiler._

        val trees = {
          val typed = new Response[Tree]
          /* we don't need a fully type-checked tree, so this should be enough*/
          askParsedEntered(scalaSourceFile, keepLoaded = false, typed)
          typed.get.left.toOption.toList
        }

        def findTopLevelObjectOrClassDefinition(t: Tree): List[ImplDef] = t match {
          case PackageDef(_, stats) => stats flatMap findTopLevelObjectOrClassDefinition
          case x: ImplDef if implDefSameAsFileName(x) => List(x)
          case _ => Nil
        }

        def implDefSameAsFileName(t: ImplDef) = t.name.toString + ".scala" == file.getName

        trees flatMap findTopLevelObjectOrClassDefinition map (_.pos) headOption

      }(None)
    } getOrElse NoPosition
  }
}

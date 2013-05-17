package scala.tools.eclipse
package refactoring.move

import org.eclipse.core.resources.{IFolder, IFile}
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.ltk.core.refactoring.participants.{MoveParticipant, CheckConditionsContext}
import org.eclipse.ltk.core.refactoring.{RefactoringStatus, CompositeChange, Change}
import org.eclipse.ui.PlatformUI

import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.eclipse.refactoring.ProgressHelpers
import scala.tools.eclipse.ScalaPlugin
import scala.tools.refactoring.common.TextChange

class ScalaMoveParticipant extends MoveParticipant {

  val getName = "Scala Move Participant"

  private var resourceToMove: IFile = _

  private var change: Change = _

  protected def initialize(element: Object) = element match {
    case f: IFile =>
      resourceToMove = f
      f.getName.endsWith("scala")
    case _ => false
  }

  def checkConditions(pm: IProgressMonitor, context: CheckConditionsContext): RefactoringStatus = {

    getArguments.getDestination match {
      case destination: IFolder =>
        val javaProject = ScalaPlugin.plugin.getJavaProject(resourceToMove.getProject)
        val targetPackage = javaProject.findPackageFragment(destination.getFullPath)

        if(targetPackage == null) {
          val msg = "Could not find the target package for "+ destination.getFullPath +". Scala source files will not " +
              "be refactored."
          return RefactoringStatus.createWarningStatus(msg)
        }

        ScalaSourceFile.createFromPath(resourceToMove.getFullPath.toOSString) map { scalaSourceFile =>

          val moveRefactoring = {
            val action = new MoveClassAction
            new action.MoveClassScalaIdeRefactoring(/*selection is unimportant: */ 0, 0, scalaSourceFile)
          }

          var initialConditions: Option[RefactoringStatus] = None

          def runRefactoring(pm: IProgressMonitor) {
            initialConditions = Some(moveRefactoring.checkInitialConditions(pm))
            moveRefactoring.setMoveSingleImpl(false /*move all classes in the file*/)
            moveRefactoring.target = targetPackage

            if(pm.isCanceled) {
              // when the user cancelled we still want to do the refactoring,
              // but we skip our part. Really? Test! Add warning to the status.
              pm.setCanceled(false)
            } else {

              change = new CompositeChange("Move Scala Class") {
                val changes = moveRefactoring.createRefactoringChanges(pm)._1
                moveRefactoring.scalaChangesToEclipseChanges(changes) foreach add
              }
            }
          }

          val isRunAsEclipseMoveResource = {
            // If there's no active workbench window, then we are run as
            // part of Eclipse's generic Move Resource refactoring.
            PlatformUI.getWorkbench.getActiveWorkbenchWindow == null
          }

          if(isRunAsEclipseMoveResource) {
            runRefactoring(pm)
          } else {
            // The drag-and-drop Move refactoring in JDT is so fast that it doesn't
            // need a cancelable progress monitor, so we run the refactoring in our own.
            ProgressHelpers.runInProgressDialogNonblocking(runRefactoring _)
          }

          moveRefactoring.cleanup()

          new RefactoringStatus {
            initialConditions foreach (_.getEntries foreach addEntry)
          }
        } getOrElse null

      case _ => null
    }
  }

  /**
   * The refactoring needs to be executed before the file is moved, otherwise the
   * underlying IFile changes and the refactoring is applied to the old file.
   */
  override def createPreChange(pm: IProgressMonitor)= change

  def createChange(pm: IProgressMonitor) = null
}

/*
 * Copyright 2005-2010 LAMP/EPFL
 */

package scala.tools.eclipse
package refactoring

import org.eclipse.core.runtime.{Status, IStatus, IProgressMonitor, CoreException}
import org.eclipse.ltk.core.refactoring.{RefactoringStatus, Refactoring => LTKRefactoring, CompositeChange}
import org.eclipse.ltk.ui.refactoring.RefactoringWizardPage

import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.eclipse.util.FileUtils
import scala.tools.eclipse.ScalaPresentationCompiler
import scala.tools.eclipse.ScalaPlugin
import scala.tools.nsc.util.SourceFile
import scala.tools.refactoring.common.InteractiveScalaCompiler
import scala.tools.refactoring.common.{TextChange, Change}
import scala.tools.refactoring.MultiStageRefactoring

/**
 * This is the abstract base class for all the concrete refactoring instances.
 *
 * It serves as a bridge between the LTK and a refactoring in the library; it
 * connects the LTK's refactoring with the library's refactorings by creating
 * the change objects for Eclipse (these are not the same as the `Change`
 * objects in the library) and checks the initial and final conditions of a
 * refactoring, i.e., it displays the errors that can be returned by the library's
 * `prepare` and `perform` methods.
 *
 * A refactoring always proceeds in the following way:
 *
 *  - The refactoring is prepared, using the selection.
 *  - Any errors that occurred during preparation are reported
 *    with the `checkInitialConditions` method.
 *  - The refactoring is performed, taking user input into account
 *    (provided by the subclass).
 *  - If any errors occurred, these are reported through the
 *    `checkFinalConditions` method.
 *  - A change object is generated via the `createChange` method.
 *
 * @param getName The displayable name of this refactoring.
 * @param file The file this refactoring started from.
 */
abstract class ScalaIdeRefactoring(val getName: String, val file: ScalaSourceFile, selectionStart: Int, selectionEnd: Int)
  extends LTKRefactoring with UserPreferencesFormatting {

  /**
   * Every refactoring subclass needs to provide a specific refactoring instance.
   */
  val refactoring: MultiStageRefactoring with InteractiveScalaCompiler

  /**
   * The subclass also needs to provide all the parameters that will
   * later be passed to the refactoring library when the refactoring
   * is performed.
   */
  def refactoringParameters: refactoring.RefactoringParameters

  /**
   * The refactoring can optionally provide a list of wizard pages
   * which are then displayed to the user.
   */
  def getPages: List[RefactoringWizardPage] = Nil

  /**
   * Holds the result of preparing this refactoring. We can keep this
   * in a lazy var because it will only be evaluated once.
   */
  private [refactoring] def preparationResult():
    Either[refactoring.PreparationError, refactoring.PreparationResult] = {

    // evaluate the selection in this thread, this
    // will also type-check the current file
    val sel = selection()

    withCompiler{ compiler =>
      compiler.askOption { () =>
        refactoring.prepare(sel)
      } getOrElse fail()
    }
  }

  /**
   * Performs the refactoring and converts the resulting changes to an
   * Eclipse CompositeChange. Note that NewFileChanges are ignored! At
   * the moment, there is only one refactoring (Move Class) that creates
   * these, which overrides this method.
   */
  def createChange(pm: IProgressMonitor): CompositeChange = {
    val changes = performRefactoring() collect {
      case tc: TextChange => tc
    }
    new CompositeChange(getName) {
      scalaChangesToEclipseChanges(changes) foreach add
    }
  }

  def checkInitialConditions(pm: IProgressMonitor): RefactoringStatus = new RefactoringStatus {
    preparationResult().fold(e => addFatalError(e.cause), identity)
  }

  def checkFinalConditions(pm: IProgressMonitor): RefactoringStatus = {
    refactoringError map RefactoringStatus.createErrorStatus getOrElse new RefactoringStatus
  }

  /**
   * Converts the (file, from, to) selection into a proper Refactoring selection.
   *
   * @throws Throws an exception if an error in the compiler occurred.
   */
  private [refactoring] def selection(): refactoring.Selection = {
    withSourceFile { sourceFile =>
      import refactoring.global

      val r = new global.Response[global.Tree]
      global.askLoadedTyped(sourceFile, r)
      r.get.fold(new refactoring.FileSelection(sourceFile.file, _, selectionStart, selectionEnd), throw _)

    }
  }

  /**
   * Creates the Eclipse change objects from this refactoring instance.
   *
   * @throws Throws a CoreException if the IFile for the corresponding AbstractFile can't be found.
   */
  private [refactoring] def scalaChangesToEclipseChanges(changes: List[TextChange]) = {
    changes groupBy (_.sourceFile.file) map {
      case (file, fileChanges) =>
        FileUtils.toIFile(file) map { file =>
          EditorHelpers.createTextFileChange(file, fileChanges)
        } getOrElse {
          val msg = "Could not find the corresponding IFile for "+ file.path
          throw new CoreException(new Status(IStatus.ERROR, ScalaPlugin.plugin.pluginId, 0, msg, null))
        }
    }
  }

  /**
   * Holds a possible error message that occurred while performing the refactoring.
   */
  private[this] var refactoringError = None: Option[String]

  /**
   * Performs this refactoring using the selection and preparation result. If the refactoring fails,
   * the error message is assigned to the `refactoringError` variable.
   *
   * @return The list of changes or an empty list when an error occurred.
   */
  private [refactoring] def performRefactoring(): List[Change] = {

    val params = refactoringParameters
    val sel = selection()

    val result = withCompiler { compiler =>
      compiler.askOption {() =>
        refactoring.perform(sel, preparationResult().right.get, params)
      }
    }

    result match {
      case Some(refactoringResult) =>
        refactoringResult.left.map(e => refactoringError = Some(e.cause)).fold(_ => Nil, identity)
      case _ =>
        refactoringError = Some("An error occurred, please check the log file")
        Nil
    }
  }

  private [refactoring] def withCompiler[T](f: ScalaPresentationCompiler => T): T = {
    file.withSourceFile((_, c) => f(c))(fail())
  }

  private [refactoring] def withSourceFile[T](f: SourceFile => T): T = {
    file.withSourceFile((s, _) => f(s))(fail())
  }

  def fail(msg: String = "Could not get the source file."): Nothing = {
    throw new CoreException(new Status(IStatus.ERROR, ScalaPlugin.plugin.pluginId, msg))
  }
}

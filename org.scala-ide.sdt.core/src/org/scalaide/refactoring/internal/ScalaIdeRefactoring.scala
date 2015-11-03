package org.scalaide.refactoring.internal

import scala.reflect.internal.util.SourceFile
import scala.tools.refactoring.MultiStageRefactoring
import scala.tools.refactoring.ParameterlessRefactoring
import scala.tools.refactoring.common.Change
import scala.tools.refactoring.common.InteractiveScalaCompiler
import scala.tools.refactoring.common.TextChange
import org.eclipse.core.runtime.CoreException
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.Status
import org.eclipse.ltk.core.refactoring.CompositeChange
import org.eclipse.ltk.core.refactoring.{Refactoring => LTKRefactoring}
import org.eclipse.ltk.core.refactoring.RefactoringStatus
import org.eclipse.ltk.ui.refactoring.RefactoringWizardPage
import org.scalaide.core.IScalaPlugin
import org.scalaide.core.compiler.IScalaPresentationCompiler.Implicits._
import org.scalaide.core.internal.compiler.ScalaPresentationCompiler
import org.scalaide.core.internal.jdt.model.ScalaSourceFile
import org.scalaide.util.internal.eclipse.TextEditUtils
import org.scalaide.util.eclipse.FileUtils
import org.scalaide.core.SdtConstants
import scala.tools.refactoring.common.TextChange
import scala.tools.refactoring.common.RenameSourceFileChange
import org.eclipse.ltk.core.refactoring.resource.RenameResourceChange
import org.eclipse.core.runtime.Path
import org.eclipse.core.runtime.IPath
import scala.reflect.io.AbstractFile
import org.eclipse.core.resources.ResourcesPlugin
import org.scalaide.core.internal.statistics.Features.Feature
import org.scalaide.core.internal.ScalaPlugin

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
abstract class ScalaIdeRefactoring(val feature: Feature, override val getName: String, val file: ScalaSourceFile, selectionStart: Int, selectionEnd: Int)
  extends LTKRefactoring with UserPreferencesFormatting {

  /**
   * Every refactoring subclass needs to provide a specific refactoring instance.
   */
  val refactoring: MultiStageRefactoring with InteractiveScalaCompiler

  /**
   * Subclasses have to provide all the parameters that will
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
   * Set this flag to true if you want changes from this refactoring to be marked as dirty in the editor.
   *
   * '''Warning''': Don't set this flag to true for refactorings that might affect files that are not
   * currently open in the editor, as changes for these files would be lost. See ticket #1002079.
   */
  protected def leaveDirty: Boolean = false

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
      compiler.asyncExec {
        refactoring.prepare(sel)
      }.getOption() getOrElse fail()
    }
  }

  /**
   * Performs the refactoring and converts the resulting changes to an
   * Eclipse CompositeChange. Note that NewFileChanges are ignored! At
   * the moment, there is only one refactoring (Move Class) that creates
   * these, which overrides this method.
   */
  override def createChange(pm: IProgressMonitor): CompositeChange = {
    val changes = performRefactoring()
    new CompositeChange(getName) {
      scalaChangesToEclipseChanges(changes) foreach add
    }
  }

  override def checkInitialConditions(pm: IProgressMonitor): RefactoringStatus = new RefactoringStatus {
    preparationResult().left.foreach(e => addFatalError(e.cause))
  }

  override def checkFinalConditions(pm: IProgressMonitor): RefactoringStatus =
    refactoringError map RefactoringStatus.createErrorStatus getOrElse new RefactoringStatus

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
  private [refactoring] def scalaChangesToEclipseChanges(changes: List[Change]) = {
    val textChanges = changes.collect { case tc: TextChange => tc }
    val renameChanges = changes.collect { case r: RenameSourceFileChange => r }

    textChanges.groupBy(_.sourceFile.file).map {
      case (file, fileChanges) =>
        FileUtils.toIFile(file) map { file =>
          TextEditUtils.createTextFileChange(file, fileChanges, leaveDirty)
        } getOrElse {
          val msg = "Could not find the corresponding IFile for "+ file.path
          throw new CoreException(new Status(IStatus.ERROR, SdtConstants.PluginId, 0, msg, null))
        }
    } ++ renameChanges.flatMap { r =>
      FileUtils.toIPath(r.sourceFile).map(path => new RenameResourceChange(path, r.to))
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
    ScalaPlugin().statistics.incUsageCounter(feature)

    val params = refactoringParameters
    val sel = selection()

    val result = withCompiler { compiler =>
      compiler.asyncExec {
        refactoring.perform(sel, preparationResult().right.get, params)
      }.getOption()
    }

    result match {
      case Some(refactoringResult) =>
        refactoringResult.left.map(e => refactoringError = Some(e.cause)).fold(_ => Nil, identity)
      case None =>
        refactoringError = Some("An error occurred, please check the log file")
        Nil
    }
  }

  private [refactoring] def withCompiler[T](f: ScalaPresentationCompiler => T): T = {
    file.scalaProject.presentationCompiler.internal(f) getOrElse fail()
  }

  private [refactoring] def withSourceFile[T](f: SourceFile => T): T = {
    file.withSourceFile((s, _) => f(s)) getOrElse fail()
  }

  def fail(msg: String = "Could not get the source file."): Nothing = {
    throw new CoreException(new Status(IStatus.ERROR, SdtConstants.PluginId, msg))
  }
}

/**
 * Should be extended by Scala IDE refactorings that mixin
 * [[import scala.tools.refactoring.ParameterlessRefactoring]].
 */
abstract class ParameterlessScalaIdeRefactoring(override val feature: Feature, override val getName: String, override val file: ScalaSourceFile, selectionStart: Int, selectionEnd: Int)
  extends ScalaIdeRefactoring(feature, getName, file, selectionStart, selectionEnd) {

  override val refactoring: MultiStageRefactoring with InteractiveScalaCompiler with ParameterlessRefactoring

  override def refactoringParameters = new refactoring.RefactoringParameters
}

package org.scalaide.refactoring.internal

import java.text.Collator
import java.util.Comparator
import scala.tools.refactoring.implementations
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jdt.core.IJavaElement
import org.eclipse.jdt.core.compiler.IProblem
import org.eclipse.jdt.core.search.IJavaSearchConstants
import org.eclipse.jdt.core.search.SearchEngine
import org.eclipse.jdt.core.search.TypeNameMatch
import org.eclipse.jdt.internal.corext.util.QualifiedTypeNameHistory
import org.eclipse.jdt.internal.corext.util.TypeNameMatchCollector
import org.eclipse.jdt.internal.ui.actions.ActionMessages
import org.eclipse.jdt.internal.ui.dialogs.MultiElementListSelectionDialog
import org.eclipse.jdt.internal.ui.util.TypeNameMatchLabelProvider
import org.eclipse.jface.window.Window
import org.scalaide.core.internal.jdt.model.LazyToplevelClass
import org.scalaide.core.internal.jdt.model.ScalaElement
import org.scalaide.core.internal.jdt.model.ScalaSourceFile
import org.scalaide.ui.internal.preferences.OrganizeImportsPreferences._
import org.scalaide.util.eclipse.EditorUtils
import org.scalaide.util.internal.eclipse.TextEditUtils

/**
 * The Scala implemention of Organize Imports.
 *
 * Organize Imports can work in two different modes, depending on whether there are
 * errors in the source file:
 *
 *  - With no errors, the refactoring simply calls the Refactoring Library's Organize Imports with the users' configuration settings.
 *  - When there are errors, specifically missing types, Organize Imports uses a SearchEngine to find the missing types to import. If
 *    there are ambiguities, the user is prompted to select the correct import.
 *
 */
class OrganizeImports extends RefactoringExecutorWithoutWizard {

  override def createRefactoring(selectionStart: Int, selectionEnd: Int, file: ScalaSourceFile) =
    new OrganizeImportsScalaIdeRefactoring(file)

  override def perform(): Unit = {

    /**
     * Returns an array of all the types that are missing in the source file.
     */
    def getMissingTypeErrorsFromFile(file: ScalaSourceFile): Array[String] = {
      val problems = Option(file.getProblems) getOrElse Array[IProblem]()
      val typeNotFoundError = "not found: type ([^\\s]+).*".r
      val valueNotFoundError = "not found: value ([^\\s]+)".r

      val errors = problems filter (_.isError) map (_.getMessage) collect {
        case typeNotFoundError(name) => name
        case valueNotFoundError(name) => name
      }
      errors.distinct
    }

    /**
     * Uses a SearchEngine to find all possible types that match the missing type's names.
     * Only types that are visible are returned, types that are inner classes of other
     * classes are filtered because they cannot be imported at the top level.
     *
     * @return Groups of types that are candidates for a missing type.
     */
    def findSuggestionsForMissingTypes(missingTypes: Array[String], file: ScalaSourceFile, pm: IProgressMonitor): Iterable[Array[TypeNameMatch]] = {
      val resultCollector = new java.util.ArrayList[TypeNameMatch]
      val scope = SearchEngine.createJavaSearchScope(Array[IJavaElement](file.getJavaProject))
      val typesToSearch =  missingTypes map (_.toArray)
      new SearchEngine().searchAllTypeNames(null, typesToSearch, scope, new TypeNameMatchCollector(resultCollector), IJavaSearchConstants.WAIT_UNTIL_READY_TO_SEARCH, pm)
      val allFoundTypes = resultCollector.toArray[TypeNameMatch](Array[TypeNameMatch]())
      val visibleTypes = allFoundTypes filter { typeNameMatch =>
        typeNameMatch.getType match {
          case se: ScalaElement => se.isVisible
          case tpe =>
            // if it's not a ScalaElement, it could still be an inner class,
            // and we cannot import them at the top level. TODO: Is this check enough?
            tpe.getParent match {
              case _: LazyToplevelClass => false // Could the parent be an object?
              case _ => true
            }
        }
      }
      visibleTypes.groupBy(_.getSimpleTypeName).values
    }

    /**
     * Checks if all the problems in the compilation unit have been fixed. If there's no editor,
     * true is returned as well to signal that no further processing needs to be attempted.
     */
    def allProblemsFixed = {
      EditorUtils.withCurrentScalaSourceFile { file =>
        Option(file.getProblems).map(_.isEmpty) getOrElse true
      } getOrElse true // no editor? then we are in trouble and can abort anyway
    }

    /**
     * Adds the imports to current editor's source file. This needs the current
     * editor and source file, we it has to be run in the UI thread. The user's
     * selection will be retained if that's possible.
     *
     * This uses the refactoring library's AddImportStatement refactoring.
     */
    def addImports(imports: Iterable[TypeNameMatch], pm: IProgressMonitor): Unit = {

      /**
       * Creates the change objects that are needed to add the imports to the source file.
       *
       * @return A list of changes or an empty list if the source file cannot be obtained.
       */
      def createChanges(scalaSourceFile: ScalaSourceFile, imports: Iterable[TypeNameMatch], pm: IProgressMonitor) = {
        scalaSourceFile.withSourceFile { (sourceFile, compiler) =>
          import org.scalaide.core.compiler.IScalaPresentationCompiler.Implicits._

          val fullyQualifiedNames = imports map (_.getFullyQualifiedName)

          compiler.asyncExec {
            val refactoring = new implementations.AddImportStatement {
              val global = compiler
            }
            refactoring.addImports(scalaSourceFile.file, fullyQualifiedNames)
          }.getOrElse(Nil)()
        } getOrElse (Nil)
      }

      EditorUtils.withCurrentEditor { editor =>

        pm.subTask("Waiting for the compiler to finish..")

        EditorUtils.withScalaSourceFileAndSelection { (scalaSourceFile, textSelection) =>
          pm.subTask("Applying the changes.")
          val changes = createChanges(scalaSourceFile, imports, pm)
          val document = editor.getDocumentProvider.getDocument(editor.getEditorInput)
          TextEditUtils.applyChangesToFileWhileKeepingSelection(document, textSelection, scalaSourceFile.file, changes, false)
          None
        }
      }
    }

    /**
     * Asks the user to choose between ambiguous missing types, using the same machinery as the JDT.
     *
     * It also updates the QualifiedTypeNameHistory for the chosen types so they will be preferred in
     * subsequent runs.
     */
    def decideAmbiguousMissingTypes(missingTypes: Array[Array[TypeNameMatch]]): Option[Array[TypeNameMatch]] = {

      val typeSearchDialog = {
        val labelProvider = new TypeNameMatchLabelProvider(TypeNameMatchLabelProvider.SHOW_FULLYQUALIFIED)
        new MultiElementListSelectionDialog(ProgressHelpers.shell, labelProvider) {
          setTitle(ActionMessages.OrganizeImportsAction_selectiondialog_title)
          setMessage(ActionMessages.OrganizeImportsAction_selectiondialog_message)
        }
      }

      typeSearchDialog.setElements(missingTypes map (_.map (_.asInstanceOf[Object])))
      typeSearchDialog.setComparator(new TypeSearchComparator)

      if (missingTypes.size > 0 && typeSearchDialog.open() == Window.OK) {
        Some(typeSearchDialog.getResult map {
          case array: Array[_] if array.length > 0 =>
            array(0) match {
              case tpeName: TypeNameMatch =>
                QualifiedTypeNameHistory.remember(tpeName.getFullyQualifiedName)
                tpeName
            }
        })
      } else {
        None
      }
    }

    /**
     * Maps the missing type names to fully qualified names and adds them as imports to the file.
     *
     * If there are still problems remaining after all the imports have been added, the function calls
     * itself until all the missing type errors are gone. At most three passes are performed.
     */
    def addMissingImportsToFile(missingTypes: Array[String], file: ScalaSourceFile, pm: IProgressMonitor): Unit = {

      pm.subTask("Finding suggestions for the missing types..")

      def iterate(missingTypes: Array[String], remainingPasses: Int): Unit = {
        findSuggestionsForMissingTypes(missingTypes, file, pm).partition(_.size <= 1) match {
          case (Nil, Nil) =>

          case (uniqueTypes, ambiguousTypos) =>

            decideAmbiguousMissingTypes(ambiguousTypos.toArray) match {
              case Some(missingTypes) =>
                addImports(uniqueTypes.flatten ++ missingTypes, pm)

                if(!allProblemsFixed && remainingPasses > 0) {
                  // We restart with an updated list of problems, hoping
                  // that some errors have been resolved.
                  iterate(getMissingTypeErrorsFromFile(file), remainingPasses - 1)
                }
              case None =>
                // the user canceled, so we just add the unique types and stop
                addImports(uniqueTypes.flatten, pm)
            }
        }
      }
      iterate(missingTypes, 3)
    }

    EditorUtils.withCurrentScalaSourceFile { file =>
      getMissingTypeErrorsFromFile(file) match {
        case missingTypes if missingTypes.isEmpty =>
          // continue with organizing imports
          runRefactoringInUiJob()
        case missingTypes =>
          ProgressHelpers.runInProgressDialogBlockUi { pm =>
            pm.beginTask("Organizing Imports", 4)
            addMissingImportsToFile(missingTypes, file, pm)
            pm.done
          }
      }
    }
  }

  class OrganizeImportsScalaIdeRefactoring(override val file: ScalaSourceFile) extends ScalaIdeRefactoring("Organize Imports", file, 0, 0) {

    lazy val compilationUnitHasProblems = file.getProblems != null && file.getProblems.exists(_.isError)

    override val refactoring = withCompiler( c => new implementations.OrganizeImports with FormattingOverrides { override val global = c })

    override protected def leaveDirty = true

    override def checkInitialConditions(pm: IProgressMonitor) = {
      val status = super.checkInitialConditions(pm)
      if(compilationUnitHasProblems) {
        status.addWarning("There are errors in the file, organizing imports might produce incorrect results.")
      }
      status
    }

    override def refactoringParameters = {
      val project = file.getJavaProject.getProject
      val organizationStrategy = getOrganizeImportStrategy(project)

      val options = {

        val expandOrCollapse = organizationStrategy match {
          case ExpandImports => List(refactoring.ExpandImports)
          case CollapseImports => List(refactoring.CollapseImports, refactoring.SortImportSelectors)
          case PreserveExistingGroups => Nil // this is not passed as an option
        }

        val wildcards = refactoring.AlwaysUseWildcards(getWildcardImportsForProject(project).toSet)

        val groups = getGroupsForProject(project).toList

        val scalaPackageStrategy = if (shouldOmitScalaPackage(project)){
          refactoring.DropScalaPackage
        } else {
          refactoring.PrependScalaPackage
        }

        expandOrCollapse ::: List(scalaPackageStrategy, wildcards, refactoring.SortImports, refactoring.GroupImports(groups))
      }

      val deps = {
        if(compilationUnitHasProblems) {
          // this is safer when there are problems in the compilation unit
          refactoring.Dependencies.RemoveUnneeded
        } else if (organizationStrategy == PreserveExistingGroups) {
          // preserve the existing grouping of imports, but still remove all unneeded ones
          refactoring.Dependencies.RecomputeAndModify
        } else {
          refactoring.Dependencies.FullyRecompute
        }
      }

      new refactoring.RefactoringParameters(options = options, deps = deps)
    }
  }

  private class TypeSearchComparator extends Comparator[Object] {
    override def compare(o1: Object, o2: Object): Int = o1 match {
      case o1: String if o1 == o2 => 0
      case _ =>
        List(o1, o2) map (QualifiedTypeNameHistory.getDefault.getPosition) match {
          case x :: y :: Nil if x == y => Collator.getInstance.compare(o1, o2)
          case x :: y :: Nil => y - x
          case _ => 0
        }
    }
  }
}

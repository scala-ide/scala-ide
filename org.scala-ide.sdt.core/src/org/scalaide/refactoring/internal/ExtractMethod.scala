package org.scalaide.refactoring.internal

import org.scalaide.core.internal.jdt.model.ScalaSourceFile
import org.scalaide.refactoring.internal.ui.NewNameWizardPage
import scala.tools.refactoring.analysis.GlobalIndexes
import scala.tools.refactoring.analysis.NameValidation
import scala.tools.refactoring.implementations

/**
 * Extracts a series of statements into a new method, passing the needed
 * parameters and return values.
 *
 * The implementation found for example in the JDT offers much more configuration
 * options, for now, we only require the user to provide a name.
 */
class ExtractMethod extends RefactoringExecutorWithWizard {

  def createRefactoring(selectionStart: Int, selectionEnd: Int, file: ScalaSourceFile) = new ExtractMethodScalaIdeRefactoring(selectionStart, selectionEnd, file)

  class ExtractMethodScalaIdeRefactoring(start: Int, end: Int, file: ScalaSourceFile) extends ScalaIdeRefactoring("Extract Method", file, start, end) {

    val refactoring = file.withSourceFile((sourceFile, compiler) => new implementations.ExtractMethod with GlobalIndexes with NameValidation {
      val global = compiler
      val index = {
        val tree = askLoadedAndTypedTreeForFile(sourceFile).left.get
        global.ask(() => GlobalIndex(tree))
      }
    }) getOrElse fail()

    var name = ""

    def refactoringParameters = name

    override def getPages = new NewNameWizardPage(s => name = s, refactoring.isValidIdentifier, "extractedMethod", "refactoring_extract_method") :: Nil

  }
}

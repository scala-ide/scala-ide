/*
 * Copyright 2005-2010 LAMP/EPFL
 */

package scala.tools.eclipse.refactoring

import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.eclipse.refactoring.ui.NewNameWizardPage
import scala.tools.refactoring.analysis.{GlobalIndexes, NameValidation}
import scala.tools.refactoring.implementations.ExtractMethod

/**
 * Extracts a series of statements into a new method, passing the needed
 * parameters and return values.
 *
 * The implementation found for example in the JDT offers much more configuration
 * options, for now, we only require the user to provide a name.
 */
class ExtractMethodAction extends RefactoringAction {

  def createRefactoring(selectionStart: Int, selectionEnd: Int, file: ScalaSourceFile) = new ExtractMethodScalaIdeRefactoring(selectionStart, selectionEnd, file)

  class ExtractMethodScalaIdeRefactoring(start: Int, end: Int, file: ScalaSourceFile) extends ScalaIdeRefactoring("Extract Method", file, start, end) {

    val refactoring = file.withSourceFile((sourceFile, compiler) => new ExtractMethod with GlobalIndexes with NameValidation {
      val global = compiler
      val index = {
        val tree = askLoadedAndTypedTreeForFile(sourceFile).left.get
        global.ask(() => GlobalIndex(tree))
      }
    })()

    var name = ""

    def refactoringParameters = name

    override def getPages = new NewNameWizardPage(s => name = s, refactoring.isValidIdentifier, "extractedMethod", "refactoring_extract_method") :: Nil

  }
}

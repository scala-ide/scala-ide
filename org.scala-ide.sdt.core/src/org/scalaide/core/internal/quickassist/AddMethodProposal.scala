package org.scalaide.core.internal.quickassist

import scala.reflect.internal.util.SourceFile
import scala.tools.nsc.interactive.Global
import scala.tools.refactoring.common.TextChange
import scala.tools.refactoring.implementations.AddField
import scala.tools.refactoring.implementations.AddMethod
import scala.tools.refactoring.implementations.AddMethodTarget
import org.eclipse.jface.text.IDocument
import org.eclipse.text.edits.ReplaceEdit
import org.scalaide.core.compiler.IScalaPresentationCompiler.Implicits._
import org.scalaide.core.compiler.InteractiveCompilationUnit
import org.scalaide.core.internal.quickassist.createmethod.ParameterList
import org.scalaide.core.internal.quickassist.createmethod.ReturnType
import org.scalaide.core.internal.quickassist.createmethod.TypeParameterList
import org.scalaide.core.quickassist.BasicCompletionProposal
import org.scalaide.ui.ScalaImages
import org.scalaide.util.eclipse.EditorUtils
import org.scalaide.core.internal.statistics.Features.Feature

abstract class AddValOrDefProposal(feature: Feature) extends BasicCompletionProposal(
    feature,
    relevance = 90,
    displayString = "",
    image = ScalaImages.ADD_METHOD_PROPOSAL) {
  protected val returnType: ReturnType
  protected val target: AddMethodTarget

  protected val targetSourceFile: Option[InteractiveCompilationUnit]
  protected val className: Option[String]
  protected val defName: String

  override def applyProposal(document: IDocument): Unit = {
    for {
      icu <- targetSourceFile
      //we must open the editor before doing the refactoring on the compilation unit:
      theDocument <- EditorUtils.findOrOpen(icu.workspaceFile)
    } {
      val changes = icu.withSourceFile { (sf, compiler) =>
        compiler.asyncExec(addRefactoring(sf, compiler)).getOrElse(Nil)()
      } getOrElse Nil

      for (change <- changes) {
        val edit = new ReplaceEdit(change.from, change.to - change.from, change.text)
        edit.apply(theDocument)
      }

      //TODO: we should allow them to change parameter names and types by tabbing
      for (change <- changes.headOption) {
        val offset = change.from + change.text.lastIndexOf("???")
        EditorUtils.enterLinkedModeUi(List((offset, "???".length)), selectFirst = true)
      }
    }
  }

  protected def addRefactoring: (SourceFile, Global) => List[TextChange]
}

trait AddFieldProposal {
  self: AddValOrDefProposal =>

  protected val isVar: Boolean

  override protected def addRefactoring = addFieldRefactoring

  protected def addFieldRefactoring =
    (scalaSourceFile: SourceFile, compiler: Global) => {
      val refactoring = new AddField { override val global = compiler }
      //if we're here, className should be defined because of the check in isApplicable
      refactoring.addField(scalaSourceFile.file, className.get, defName, isVar, returnType, target)
    }
}

trait AddMethodProposal {
  self: AddValOrDefProposal =>

  protected val parameters: ParameterList
  protected val typeParameters: TypeParameterList = Nil

  override protected def addRefactoring = addMethodRefactoring

  protected def addMethodRefactoring =
    (scalaSourceFile: SourceFile, compiler: Global) => {
      val refactoring = new AddMethod { override val global = compiler }
      //if we're here, className should be defined because of the check in isApplicable
      refactoring.addMethod(scalaSourceFile.file, className.get, defName, parameters, typeParameters, returnType, target)
    }

  def getDefInfo(parameters: ParameterList, returnType: ReturnType): (String, String) = {
    val prettyParameterList = (for (parameterList <- parameters) yield {
      parameterList.map(_._2).mkString(", ")
    }).mkString("(", ")(", ")")

    val returnTypeStr = returnType.map(": " + _).getOrElse("")
    (prettyParameterList, returnTypeStr)
  }
}

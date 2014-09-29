package org.scalaide.core.internal.quickfix

import org.eclipse.jdt.internal.ui.JavaPluginImages
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.contentassist.IContextInformation
import org.eclipse.swt.graphics.Image
import org.eclipse.swt.graphics.Point
import org.eclipse.text.edits.ReplaceEdit
import org.scalaide.core.internal.quickfix.createmethod.{ ParameterList, ReturnType, TypeParameterList }
import org.scalaide.core.internal.jdt.model.ScalaSourceFile
import org.scalaide.core.internal.jdt.model.ScalaCompilationUnit
import org.scalaide.util.EditorUtils
import scala.tools.refactoring.implementations.{ AddMethod, AddField, AddMethodTarget }
import scala.reflect.internal.util.SourceFile
import tools.nsc.interactive.Global
import scala.tools.refactoring.common.TextChange

trait AddValOrDefProposal extends IJavaCompletionProposal {
  protected val returnType: ReturnType
  protected val target: AddMethodTarget

  protected val targetSourceFile: Option[ScalaSourceFile]
  protected val className: Option[String]
  protected val defName: String

  override def apply(document: IDocument): Unit = {
    for {
      scalaSourceFile <- targetSourceFile
      //we must open the editor before doing the refactoring on the compilation unit:
      theDocument <- EditorUtils().findOrOpen(scalaSourceFile.workspaceFile)
    } {
      val scu = scalaSourceFile.getCompilationUnit.asInstanceOf[ScalaCompilationUnit]
      val changes = scu.withSourceFile(addRefactoring) getOrElse Nil

      for (change <- changes) {
        val edit = new ReplaceEdit(change.from, change.to - change.from, change.text)
        edit.apply(theDocument)
      }

      //TODO: we should allow them to change parameter names and types by tabbing
      for (change <- changes.headOption) {
        val offset = change.from + change.text.lastIndexOf("???")
        EditorUtils().enterLinkedModeUi(List((offset, "???".length)), selectFirst = true)
      }
    }
  }

  protected def addRefactoring: (SourceFile, Global) => List[TextChange]

  override def getRelevance = 90
  override def getSelection(document: IDocument): Point = null
  override def getAdditionalProposalInfo(): String = null
  override def getImage(): Image = JavaPluginImages.DESC_MISC_PUBLIC.createImage()
  override def getContextInformation: IContextInformation = null
}

trait AddFieldProposal {
  self: AddValOrDefProposal =>

  protected val isVar: Boolean

  override protected def addRefactoring = addFieldRefactoring

  protected def addFieldRefactoring =
    (scalaSourceFile: SourceFile, compiler: Global) => {
      val refactoring = new AddField { val global = compiler }
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
      val refactoring = new AddMethod { val global = compiler }
      //if we're here, className should be defined because of the check in isApplicable
      refactoring.addMethod(scalaSourceFile.file, className.get, defName, parameters, typeParameters, returnType, target)
    }

  def getDefInfo(parameters: ParameterList, returnType: ReturnType) = {
    val prettyParameterList = (for (parameterList <- parameters) yield {
      parameterList.map(_._2).mkString(", ")
    }).mkString("(", ")(", ")")

    val returnTypeStr = returnType.map(": " + _).getOrElse("")
    (prettyParameterList, returnTypeStr)
  }
}
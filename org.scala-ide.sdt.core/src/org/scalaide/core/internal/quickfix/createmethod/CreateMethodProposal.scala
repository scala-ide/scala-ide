package scala.tools.eclipse.quickfix.createmethod

import scala.reflect.internal.util.RangePosition
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.eclipse.refactoring.EditorHelpers
import scala.tools.eclipse.util.parsing.ScalariformParser
import scala.tools.eclipse.util.parsing.ScalariformUtils
import scala.tools.refactoring.implementations.AddMethod
import scala.tools.refactoring.implementations.AddMethodTarget

import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.jdt.internal.ui.JavaPluginImages
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.Position
import org.eclipse.jface.text.contentassist.IContextInformation
import org.eclipse.swt.graphics.Image
import org.eclipse.swt.graphics.Point
import org.eclipse.text.edits.ReplaceEdit

case class CreateMethodProposal(fullyQualifiedEnclosingType: Option[String], method: String, target: AddMethodTarget, compilationUnit: ICompilationUnit, pos: Position) extends IJavaCompletionProposal {
  private val UnaryMethodNames = "+-!~".map("unary_" + _)

  private val sourceFile = compilationUnit.asInstanceOf[ScalaSourceFile]
  private val sourceAst = ScalariformParser.safeParse(sourceFile.getSource()).map(_._1)
  private val methodNameOffset = pos.offset + pos.length - method.length

  private def typeAtRange(start: Int, end: Int): String = {
    compilationUnit.asInstanceOf[ScalaCompilationUnit].withSourceFile((srcFile, compiler) => {
      compiler.askOption(() => {
        val length = end - start
        val context = compiler.doLocateContext(new RangePosition(srcFile, start, start, start + length-1))
        val tree = compiler.locateTree(new RangePosition(srcFile, start, start, start + length-1))
        val typer = compiler.analyzer.newTyper(context)
        val typedTree = typer.typed(tree)
        val tpe = typedTree.tpe.resultType.underlying
        if (tpe.isError) None
        else if (tpe.toString == "Null") Some("AnyRef") //there must be a better condition
        else Some(tpe.toString) //do we want tpe.isError? tpe.isErroneous?
      }).flatten.getOrElse("Any")
    }) getOrElse ("Any")
  }

  private val (targetSourceFile, className, targetIsOtherClass) = fullyQualifiedEnclosingType match {
    case Some(otherClass) =>
      val info = new MissingMemberInfo(compilationUnit, otherClass, method, pos, sourceAst.get)
      val targetSourceFile = info.targetElement.collect { case scalaSource: ScalaSourceFile => scalaSource }
      (targetSourceFile, Some(info.className), true)
    case None => {
      val className = sourceAst.map(ScalariformUtils.enclosingClassForMethodInvocation(_, methodNameOffset)).flatten
      (Some(sourceFile), className, false)
    }
  }

  private val (rawParameters: ParameterList, rawReturnType: ReturnType) = sourceAst match {
    case Some(ast) => {
      val scu = compilationUnit.asInstanceOf[ScalaCompilationUnit]
      val paramsAfterMethod = ScalariformUtils.getParameters(ast, methodNameOffset, typeAtRange)
      paramsAfterMethod match {
        case Nil => MissingMemberInfo.inferFromEnclosingMethod(scu, ast, pos.offset)
        case list => (list, None)
      }
    }
    case None => (Nil, None)
  }
  private val parametersWithSimpleName = for (parameterList <- rawParameters)
    yield for ((name, tpe) <- parameterList) yield
      (name, tpe.substring(tpe.lastIndexOf('.') + 1))
  private val parameters = ParameterListUniquifier.uniquifyParameterNames(parametersWithSimpleName)
  private val returnType: ReturnType = if (UnaryMethodNames.contains(method)) className else rawReturnType

  /*
   * if they write "unknown = 3" or "other.unknown = 3", we will be in here since
   * it will result in an error: "not found: value unknown", however we don't
   * want to provide this quickfix
   */
  private val suppressQuickfix = sourceAst.map(ast => ScalariformUtils.isEqualsCallWithoutParameterList(ast, methodNameOffset)).getOrElse(false)

  def isApplicable = !suppressQuickfix && targetSourceFile.isDefined && className.isDefined

  override def apply(document: IDocument): Unit = {
    for {
      scalaSourceFile <- targetSourceFile
      //we must open the editor before doing the refactoring on the compilation unit:
      theDocument <- EditorHelpers.findOrOpen(scalaSourceFile.workspaceFile)
    } {
      val scu = scalaSourceFile.getCompilationUnit.asInstanceOf[ScalaCompilationUnit]
      val changes = scu.withSourceFile { (srcFile, compiler) =>
        val refactoring = new AddMethod { val global = compiler }
        refactoring.addMethod(scalaSourceFile.file, className.get, method, parameters, returnType, target) //if we're here, className should be defined because of the check in isApplicable
      } getOrElse Nil

      for (change <- changes) {
        val edit = new ReplaceEdit(change.from, change.to - change.from, change.text)
        edit.apply(theDocument)
      }

      //TODO: we should allow them to change parameter names and types by tabbing
      for (change <- changes.headOption) {
        val offset = change.from + change.text.lastIndexOf("???")
        EditorHelpers.enterLinkedModeUi(List((offset, "???".length)), selectFirst = true)
      }
    }
  }

  override def getDisplayString(): String = {
    val prettyParameterList = (for (parameterList <- parameters) yield {
      parameterList.map(_._2).mkString(", ")
    }).mkString("(", ")(", ")")

    val returnTypeStr = returnType.map(": " + _).getOrElse("")

    val base = s"Create method '$method$prettyParameterList$returnTypeStr'"
    val inType = if (targetIsOtherClass) s" in type '${className.get}'" else ""
    base + inType
  }

  override def getRelevance = 90
  override def getSelection(document: IDocument): Point = null
  override def getAdditionalProposalInfo(): String = null
  override def getImage(): Image = JavaPluginImages.DESC_MISC_PUBLIC.createImage()
  override def getContextInformation: IContextInformation = null

}

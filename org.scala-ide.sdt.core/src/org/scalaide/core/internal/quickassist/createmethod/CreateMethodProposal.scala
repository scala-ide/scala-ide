package org.scalaide.core.internal.quickassist.createmethod

import scala.reflect.internal.util.RangePosition
import scala.tools.refactoring.implementations.AddMethod
import scala.tools.refactoring.implementations.AddMethodTarget
import org.eclipse.jface.text.Position
import org.scalaide.core.internal.jdt.model.ScalaSourceFile
import org.scalaide.util.internal.scalariform.ScalariformParser
import org.scalaide.util.internal.scalariform.ScalariformUtils
import org.scalaide.core.internal.quickassist.AddMethodProposal
import org.scalaide.core.internal.quickassist.AddValOrDefProposal
import org.scalaide.core.compiler.IScalaPresentationCompiler.Implicits._
import org.scalaide.core.compiler.InteractiveCompilationUnit
import org.scalaide.core.internal.statistics.Features.CreateMethod

case class CreateMethodProposal(
  fullyQualifiedEnclosingType: Option[String], override val defName: String,
  override val target: AddMethodTarget, icu: InteractiveCompilationUnit, offset: Int, length: Int)
    extends AddValOrDefProposal(CreateMethod) with AddMethodProposal {

  private val UnaryMethodNames = "+-!~".map("unary_" + _)

  private val sourceAst = ScalariformParser.safeParse(icu.lastSourceMap().scalaSource.mkString).map(_._1)
  private val methodNameOffset = offset + length - defName.length

  private def typeAtRange(start: Int, end: Int): String = {
    icu.withSourceFile { (srcFile, compiler) =>
      compiler.asyncExec {
        val length = end - start
        val context = compiler.doLocateContext(new RangePosition(srcFile, start, start, start + length-1))
        val tree = compiler.locateTree(new RangePosition(srcFile, start, start, start + length-1))
        val typer = compiler.analyzer.newTyper(context)
        val typedTree = typer.typed(tree)
        val tpe = typedTree.tpe.resultType.underlying
        if (tpe.isError) None
        else if (tpe.toString == "Null") Some("AnyRef") //there must be a better condition
        else Some(tpe.toString) //do we want tpe.isError? tpe.isErroneous?
      }.getOption().flatten.getOrElse("Any")
    } getOrElse ("Any")
  }

  protected val (targetSourceFile, className, targetIsOtherClass) = fullyQualifiedEnclosingType match {
    case Some(otherClass) =>
      val info = new MissingMemberInfo(icu, otherClass, defName, offset, sourceAst.get)
      val targetSourceFile = info.targetElement.collect { case scalaSource: ScalaSourceFile => scalaSource }
      (targetSourceFile, Some(info.className), true)
    case None => {
      val className = sourceAst.map(ScalariformUtils.enclosingClassForMethodInvocation(_, methodNameOffset)).flatten
      (Some(icu), className, false)
    }
  }

  private val (rawParameters: ParameterList, rawReturnType: ReturnType) = sourceAst match {
    case Some(ast) => {
      val paramsAfterMethod = ScalariformUtils.getParameters(ast, methodNameOffset, typeAtRange)
      paramsAfterMethod match {
        case Nil => MissingMemberInfo.inferFromEnclosingMethod(icu, ast, offset)
        case list => (list, None)
      }
    }
    case None => (Nil, None)
  }
  private val parametersWithSimpleName = for (parameterList <- rawParameters)
    yield for ((name, tpe) <- parameterList) yield
      (name, tpe.substring(tpe.lastIndexOf('.') + 1))
  override protected val parameters = ParameterListUniquifier.uniquifyParameterNames(parametersWithSimpleName)
  override protected val returnType: ReturnType = if (UnaryMethodNames.contains(defName)) className else rawReturnType

  /*
   * if they write "unknown = 3" or "other.unknown = 3", we will be in here since
   * it will result in an error: "not found: value unknown", however we don't
   * want to provide this quickassist
   */
  private val suppressQuickfix = sourceAst.map(ast => ScalariformUtils.isEqualsCallWithoutParameterList(ast, methodNameOffset)).getOrElse(false)

  def isApplicable = !suppressQuickfix && targetSourceFile.isDefined && className.isDefined

  override def getDisplayString(): String = {
    val (prettyParameterList, returnTypeStr) = getDefInfo(parameters, returnType)

    val base = s"Create method '$defName$prettyParameterList$returnTypeStr'"
    val inType = if (targetIsOtherClass) s" in type '${className.get}'" else ""
    base + inType
  }
}

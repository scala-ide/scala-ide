package org.scalaide.core.internal.quickfix.abstractimpl

import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal
import org.scalaide.core.internal.jdt.model.ScalaSourceFile
import scala.tools.refactoring.implementations.AddToClosest
import org.scalaide.core.internal.quickfix.createmethod.{ ParameterList, ReturnType, TypeParameterList }
import scala.tools.refactoring.implementations.AddMethodTarget
import org.scalaide.util.internal.eclipse.EditorUtils
import scala.reflect.internal.util.SourceFile
import org.scalaide.core.internal.quickfix.{AddMethodProposal, AddValOrDefProposal}
import org.scalaide.core.internal.quickfix.AddFieldProposal
import scala.collection.immutable
import scala.tools.nsc.interactive.Global

object AbstractMemberProposal {
  def apply(global: Global)(abstrMethod: global.MethodSymbol, impl: global.ImplDef)(
    tsf: Option[ScalaSourceFile], addMethodTarget: AddMethodTarget) = {

    new {
      override val compiler: global.type = global
      override val targetSourceFile: Option[ScalaSourceFile] = tsf
      override val abstractMethod = abstrMethod
      override val implDef = impl
      override val target = addMethodTarget
    } with AbstractMemberProposal
  }
}

trait AbstractMemberProposal extends AddMethodProposal with AddFieldProposal with AddValOrDefProposal {
  protected val compiler: Global
  import compiler._

  val targetSourceFile: Option[ScalaSourceFile]
  protected val abstractMethod: MethodSymbol
  protected val implDef: ImplDef
  protected val target: AddMethodTarget

  private def initValOrDef: (TypeParameterList, ParameterList, ReturnType, Boolean, Boolean) = {
    def processType(tp: Type) =
      (if (tp.isError) None
      else if (tp.toString == "Null") Some("AnyRef")
      // TODO: add sprinter to remove redundant type prefix
      else Option(tp.toString())) getOrElse ("Any")

    val paramss: ParameterList = abstractMethod.paramss map {
      _.zipWithIndex.map { param =>
        // add implicit to first param if required
        ((if (param._1.isImplicit && (param._2 == 0)) s"${compiler.nme.IMPLICITkw} " else "") +
          param._1.name.decode, processType(param._1.tpe.asSeenFrom(implDef.symbol.tpe, abstractMethod.owner)))
      }
    }

    val typeParams: TypeParameterList = abstractMethod.typeParams map (_.tpe.toString)
    val retType: ReturnType = Option(processType(abstractMethod.returnType.asSeenFrom(implDef.symbol.tpe, abstractMethod.owner)))
    val isDef = abstractMethod.isMethod && !abstractMethod.isAccessor

    val absractMethodSetter = abstractMethod.setterIn(abstractMethod.owner)
    val isVar = absractMethodSetter != NoSymbol
    (typeParams, paramss, retType, isDef, isVar)
  }

  val abstrInfo = initValOrDef
  override protected val (typeParameters: TypeParameterList, parameters: ParameterList, returnType: ReturnType, _, isVar) = abstrInfo
  val isDef: Boolean = abstrInfo._4
  val defName = abstractMethod.nameString
  val className = Option(implDef.name.decode)

  override protected def addRefactoring = if (isDef) addMethodRefactoring else addFieldRefactoring

  override def getDisplayString(): String = {
    val (prettyParameterList, returnTypeStr) = getDefInfo(parameters, returnType)
    val typeParametersList = if (!typeParameters.isEmpty) typeParameters.mkString("[", ",", "]") else ""
    s"Implement ${if (isDef) s"${compiler.nme.DEFkw}" else if (isVar) s"${compiler.nme.VARkw}" else s"${compiler.nme.VALkw}"} '${abstractMethod.nameString}$typeParametersList$prettyParameterList$returnTypeStr'"
  }
}
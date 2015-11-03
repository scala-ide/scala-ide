package org.scalaide.core.internal.quickassist.abstractimpl

import scala.tools.refactoring.implementations.AddMethodTarget

import org.scalaide.core.compiler.IScalaPresentationCompiler
import org.scalaide.core.compiler.InteractiveCompilationUnit
import org.scalaide.core.internal.quickassist.AddFieldProposal
import org.scalaide.core.internal.quickassist.AddMethodProposal
import org.scalaide.core.internal.quickassist.AddValOrDefProposal
import org.scalaide.core.internal.quickassist.createmethod.ParameterList
import org.scalaide.core.internal.quickassist.createmethod.ReturnType
import org.scalaide.core.internal.quickassist.createmethod.TypeParameterList
import org.scalaide.core.internal.statistics.Features.CreateMethod

object AbstractMemberProposal {
  def apply(global: IScalaPresentationCompiler)(abstrMethod: global.MethodSymbol, impl: global.ImplDef)(
    icu: Option[InteractiveCompilationUnit], addMethodTarget: AddMethodTarget) = {

    new AbstractMemberProposal {
      override val compiler: global.type = global
      override val targetSourceFile = icu
      override val abstractMethod = abstrMethod
      override val implDef = impl
      override val target = addMethodTarget
    }
  }
}

abstract class AbstractMemberProposal extends AddValOrDefProposal(CreateMethod) with AddMethodProposal with AddFieldProposal {
  protected val compiler: IScalaPresentationCompiler
  import compiler._

  protected val abstractMethod: MethodSymbol
  protected val implDef: ImplDef

  private def initValOrDef: (TypeParameterList, ParameterList, ReturnType, Boolean, Boolean) = {
    def processType(tp: Type) =
      (if (tp.isError) None
      else if (tp.toString == "Null") Some("AnyRef")
      // TODO: add sprinter to remove redundant type prefix
      else Option(compiler.declPrinter.showType(tp))) getOrElse ("Any")

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

  private val abstrInfo = initValOrDef
  override protected val (typeParameters: TypeParameterList, parameters: ParameterList, returnType: ReturnType, _, isVar: Boolean) = abstrInfo
  val isDef: Boolean = abstrInfo._4
  override val defName: String = abstractMethod.nameString
  override val className: Option[String] = Option(implDef.name.decode)

  override protected def addRefactoring = if (isDef) addMethodRefactoring else addFieldRefactoring

  override def getDisplayString(): String = {
    val (prettyParameterList, returnTypeStr) = getDefInfo(parameters, returnType)
    val typeParametersList = if (!typeParameters.isEmpty) typeParameters.mkString("[", ",", "]") else ""
    s"Implement ${if (isDef) s"${compiler.nme.DEFkw}" else if (isVar) s"${compiler.nme.VARkw}" else s"${compiler.nme.VALkw}"} '${abstractMethod.nameString}$typeParametersList$prettyParameterList$returnTypeStr'"
  }
}

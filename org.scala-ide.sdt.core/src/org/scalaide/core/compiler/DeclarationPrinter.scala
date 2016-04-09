package org.scalaide.core.compiler

import org.scalaide.logging.HasLogger
import scala.tools.nsc.interactive.Global
import scala.reflect.internal.Flags

/** A declaration printer for Scala symbols and types. Designed to be extensible, clients
 *  may subclass this class.
 *
 *  The current implementation borrows almost all the code from the Scala compiler's various
 *  `toString` implementations accros the Symbol and Type hierarchy. The only notable
 *  difference is the way type references are printed. Unlike scalac, this printer uses
 *  simple names (no fully qualified names). An alternative implementatinon would need to
 *  override `showSingleType` and `showThisType` in order to reinstate that. For example:
 *
 *  {{{
 *   def foo[T <: File](x: ArrayBuffer[File])
 *   <vs>
 *   def foo[T <: java.io.File](x: scala.collection.mutable.ArrayBuffer[java.io.File])
 *  }}}
 *
 *  @note All methods should be run on the PC thread.
 */
abstract class DeclarationPrinter extends HasLogger {
  val compiler: Global
  import compiler._

  /** Print the given type
   *
   *  @note Should be run on the presentation compiler thread (inside askOption).
   */
  def apply(tpe: Type): String = showType(tpe)

  /** Print the given declaration, potentially seen as a different type.
   *
   *  This method returns a human-readable string of the given symbol,
   *  trying to be as close as possible to real Scala syntax. It prints
   *  modifiers, type and value parameters, bounds or type aliases.
   *
   *  @note Should be run on the presentation compiler thread (inside askOption).
   */
  def defString(sym: Symbol, flagMask: Long = Flags.ExplicitFlags, showKind: Boolean = true)(seenAs: Type = sym.rawInfo): String = {

    def infoString(tp: Type): String = {
      def isStructuralThisType = (
        // prevents disasters like SI-8158
        sym.owner.isInitialized && sym.owner.isStructuralRefinement && tp == sym.owner.tpe)

      if (sym.isType) {
        typeParamsString(tp) + (
          if (sym.isClass) " extends " + showTypes(tp.parents).mkString(" with ")
          else if (sym.isAliasType) " = " + showType(tp.resultType)
          else tp.resultType match {
            case rt @ TypeBounds(_, _) => showType(rt)
            case rt                    => " <: " + showType(rt)
          })
      } else if (sym.isModule) "" //  avoid "object X of type X.type"
      else tp match {
        case PolyType(tparams, res)    => tp.typeParams.map(defString(_, flagMask)()).mkString("[", ", ", "]") + infoString(res)
        case NullaryMethodType(res)    => infoString(res)
        case MethodType(params, res)   => params.map(s => defString(s, flagMask)()).mkString("(", ", ", ")") + infoString(res)
        case _ if isStructuralThisType => ": " + sym.owner.name
        case _                         => ": " + showType(tp)
      }
    }

    /* Inspired from Symbol and HasFlags, but take into account the mask access modifiers.
     * The original would always display access modifiers, even when the mask is `0L`.
     */
    def flagString(sym: Symbol): String = {
      import Flags._
      def showAccessor = (flagMask & AccessFlags | flagMask) != 0
      val access = if (showAccessor) accessString(sym) else ""
      val nonAccess = sym.flagBitsToString(sym.flags & flagMask & ~AccessFlags)

      if (access == "") nonAccess
      else if (nonAccess == "") access
      else nonAccess + " " + access
    }

    def accessString(sym: Symbol): String = {
      import sym._
      import Flags._

      val pw = if (hasAccessBoundary) showSymbolName(privateWithin) else ""

      if (pw == "") {
        if (hasAllFlags(PrivateLocal)) "private[this]"
        else if (hasAllFlags(ProtectedLocal)) "protected[this]"
        else if (hasFlag(PRIVATE)) "private"
        else if (hasFlag(PROTECTED)) "protected"
        else ""
      }
      else if (hasFlag(PROTECTED)) "protected[" + pw + "]"
      else "private[" + pw + "]"
    }

    val gsym = sym.getterIn(sym.owner)

    val hasGetterSetter = (gsym != NoSymbol) && (sym.setterIn(sym.owner) != NoSymbol)

    val name = if (sym.isConstructor) sym.owner.decodedName else sym.nameString
    val flags = flagString(if (gsym != NoSymbol) gsym else sym)

    def keyword = if (hasGetterSetter) "var" else sym.keyString

    compose(
      // TODO: Annotations
      flags,
      if (showKind) keyword else "",
      sym.varianceString + name + infoString(seenAs) // don't force the symbol
    )
  }

  /** Print the given type
   *
   *  @note Should be run on the presentation compiler thread (inside askOption).
   */
  def showType(tpe: Type): String = tpe match {
    case ErrorType                             => showErrorType()
    case WildcardType                          => showWildcardType()
    case NoType                                => showNoType()
    case NoPrefix                              => showNoPrefix()
    case bwt @ BoundedWildcardType(bounds)     => showBoundedWildcardType(bwt)
    case tt @ ThisType(sym)                    => showThisType(tt)
    case st @ SuperType(thistpe, supertpe)     => showSuperType(st)
    case st @ SingleType(pre, sym)             => showSingleType(st)
    case ct @ ConstantType(value)              => showConstantType(ct)
    case tref @ TypeRef(pre, sym, args)        => showTypeRef(tref)
    case refTpe @ RefinedType(parents, defs)   => showRefinedType(refTpe)
    case et @ ExistentialType(tparams, result) => showExistentialType(et)
    case at @ AnnotatedType(annots, tp)        => showAnnotatedType(at)
    case mt @ MethodType(params, resultType)   => showMethodType(mt)
    case NullaryMethodType(underlying)         => showType(underlying)
    case pt @ PolyType(tparams, result)        => showPolyType(pt)
    case tb @ TypeBounds(lo, hi)               => showTypeBounds(tb)
    case cit @ ClassInfoType(_, _, _)          => showClassInfoType(cit)

    case _ =>
      logger.info(s"Unknown type: $tpe of class: ${tpe.getClass}")
      tpe.toString // fallback
  }

  //////////////// Below are helper methods for each type case /////////////////

  def showMethodType(mt: MethodType): String = {
    import mt._

    val paramsStr = for (p <- params) yield s"${p.name}: ${showType(p.tpe)}"
    s"${paramsStr.mkString("(", ", ", ")")}${showType(resultType)}"
  }

  def showTypeRef(tpe: TypeRef): String = {
    import tpe._
    import definitions._

    val noArgsString = showPrefix(tpe)
    sym match {
      case RepeatedParamClass | JavaRepeatedParamClass => showType(args.head) + "*"
      case ByNameParamClass                            => "=> " + showType(args.head)
      case _ =>
        if (isFunctionTypeDirect(tpe))
          showFunction(tpe)
        else if (isTupleTypeDirect(tpe))
          showTuple(tpe)
        else if (sym.isAliasType && prefixChain.exists(_.termSymbol.isSynthetic) && (tpe ne dealias))
          "" + showType(dealias)
        else {
          val argsString = if (tpe.args.isEmpty) "" else s"[${showTypes(tpe.args).mkString(", ")}]"
          noArgsString + argsString
        }
    }
  }

  def showPrefix(tpe: TypeRef): String = {
    if (tpe.termSymbol.hasPackageFlag) ""
    else {
      val prefix = showType(tpe.pre)
      if (prefix != "") prefix + "." + tpe.sym.nameString
      else tpe.sym.nameString
    }
  }

  def showThisType(tt: ThisType) = "" // disable all types of the form Foo.this

  def showFunction(tpe: TypeRef): String = {
    import definitions._

    val noArgsString = s"${showSymbolName(tpe.sym)}"

    // Aesthetics: printing Function1 as T => R rather than (T) => R
    // ...but only if it's not a tuple, so ((T1, T2)) => R is distinguishable
    // from (T1, T2) => R.
    unspecializedTypeArgs(tpe) match {
      // See neg/t588 for an example which arrives here - printing
      // the type of a Function1 after erasure.
      case Nil => noArgsString
      case in :: out :: Nil if !isTupleTypeDirect(in) =>
        // A => B => C should be (A => B) => C or A => (B => C).
        // Also if A is byname, then we want (=> A) => B because => is right associative and => A => B
        // would mean => (A => B) which is a different type
        val in_s = if (isFunctionTypeDirect(in) || isByNameParamType(in)) s"(${showType(in)})" else showType(in)
        val out_s = if (isFunctionTypeDirect(out)) s"(${showType(out)})" else showType(out)
        in_s + " => " + out_s
      case xs =>
        showTypes(xs.init).mkString("(", ", ", ")") + " => " + showType(xs.last)
    }
  }

  def showTuple(tpe: TypeRef): String = {
    import tpe._

    args match {
      case Nil        => s"${showSymbolName(tpe.sym)}"
      case arg :: Nil => s"(${showType(arg)},)"
      case _          => showTypes(args).mkString("(", ", ", ")")
    }
  }

  def showSymbolName(sym: Symbol): String = {
    sym.name.toString
  }

  def showTypes(tpes: List[Type]): List[String] = tpes.map(showType)
  def showSymbolNames(syms: List[Symbol]): List[String] = syms.map(showSymbolName)

  def showExistentialType(tpe: ExistentialType) = {
    import tpe._

    def clauses =
      quantified map (_.existentialToString) mkString (" forSome { ", "; ", " }")

    def wildcardArgsString(qset: Set[Symbol], args: List[Type]): List[String] = args map {
      case TypeRef(_, sym, _) if (qset contains sym) =>
        "_" + sym.infoString(sym.info)
      case arg =>
        showType(arg)
    }

    underlying match {
      case TypeRef(pre, sym, args) if isRepresentableWithWildcards =>
        "" + showType(TypeRef(pre, sym, Nil)) + wildcardArgsString(quantified.toSet, args).mkString("[", ", ", "]")
      case MethodType(_, _) | NullaryMethodType(_) | PolyType(_, _) =>
        "(" + showType(underlying) + ")" + clauses
      case _ =>
        showType(underlying) + clauses
    }
  }

  def showRefinedType(refType: RefinedType): String = {
    s"${showTypes(refType.parents).mkString(" with ")} { .. }"
  }

  def showPolyType(pt: PolyType): String = {
    showSymbolNames(pt.typeParams).mkString("[", ", ", "]") + showType(pt.resultType)
  }

  def showTypeBounds(tb: TypeBounds): String = {
    import tb._

    val emptyLowerBound = (lo.typeSymbolDirect eq definitions.NothingClass) || lo.isWildcard
    val emptyHiBound = (hi.typeSymbolDirect eq definitions.AnyClass) || hi.isWildcard

    if (emptyHiBound && emptyLowerBound) ""
    else if (emptyLowerBound) s" <: ${showType(hi)}"
    else if (emptyHiBound) s" >: ${showType(lo)}"
    else s" >: ${showType(lo)} <: ${showType(hi)}"
  }

  def showClassInfoType(cit: ClassInfoType) = {
    import cit._
    import definitions._

    def formattedToString = parents.mkString("\n        with ") + scopeString
    def scopeString: String = {
      fullyInitializeScope(decls)
        .map(sym => defString(sym, Flags.ExplicitFlags)())
        .mkString(" {\n  ", "\n  ", "\n}")
    }

    if (decls.size > 1) formattedToString else parentsString(parents)
  }

  protected def compose(str: String*) =
    str.filter(_ != "").mkString(" ")

  def showSingleType(st: SingleType) = {
    val ending = ""
    val skippable = (st.sym.hasPackageFlag || st.sym.isPackageObject || st.sym == definitions.PredefModule)
    val name = if (skippable) "" else st.sym.nameString
    showType(st.pre) + name + ending
  }

  def showErrorType(): String =
    "<error>"

  def showWildcardType(): String =
    "_"

  def showBoundedWildcardType(bwt: BoundedWildcardType): String =
    "?" + showType(bwt.bounds)

  def showNoType(): String =
    ""

  def showNoPrefix(): String =
    ""

  def showSuperType(st: SuperType): String = {
    s"${showType(st.thistpe)}.super[${showType(st.supertpe)}]"
  }

  def showConstantType(ct: ConstantType): String =
    s"${ct.value}.type"

  def showAnnotatedType(at: AnnotatedType) = {
    def annotString(a: AnnotationInfo) = {
      val args = a.args.mkString("(", ", ", ")")
      s"showType(a.atp)$args"
    }
    s"${showType(at.underlying)} @${at.annotations.map(annotString)}"
  }

}

package org.scalaide.core.internal.compiler

import org.scalaide.core.compiler.IScalaPresentationCompiler.Implicits._

trait JavaSig { pc: ScalaPresentationCompiler =>

  /** Returns the symbol's `JavaSignature`*/
  def javaSigOf(sym: Symbol): JavaSignature = new JavaSignature(sym)

  /*
   * An utility class that allows to extract information from the `symbol`'s java signature.
   * If the passed `symbol` does not need a Java signature, empty values are returned.
   *
   * @example Given the following method declaration:
   * {{{ def foo[T <: Foo, U <: ArrayList[_ >: Class[T]]](u: U, t: T):T  = t }}}
   *
   * The following are the values computed by JavaSignatureConverter:
   * {{{
   *   sig <- <T:LFoo;U:Ljava.util.ArrayList<-Ljava.lang.Class<TT;>;>;>(TU;TT;)TT;
   *   paramsCount <- 2
   *   paramsType <- [[U], [T]]
   *   paramsTypeSig <-  [TU;, TT;]
   *   typeVars <- [T, U]
   *   typeParamsSig <- [T:LFoo;, U:Ljava.util.ArrayList<-Ljava.lang.Class<TT;>;>;]
   *   typeParamsBounds <- [[LFoo;], [Ljava.util.ArrayList<-Ljava.lang.Class<TT;>;>;]]
   *   typeParamsBoundsReadable <- [[Foo], [java.util.ArrayList<? super java.lang.Class<T>>]]
   *   returnTypeSig <- TT;
   *   returnType <- T
   *   exceptionTypes <- []
   * }}}
   *
   * @example Given the following method declaration:
   * {{{ def foo[U <: List[Array[String]]](u: U, s: Int):U  = u }}}
   *
   * The following are the values computed by JavaSignatureConverter:
   * {{{
   *   sig <- <U:Lscala.collection.immutable.List<[Ljava.lang.String;>;>(TU;I)TU;
   *   paramsCount <- 2
   *   paramsType <- [[U], [i, n, t]]
   *   paramsTypeSig <- [TU;, I]
   *   typeVars <- [U]
   *   typeParamsSig <- [U:Lscala.collection.immutable.List<[Ljava.lang.String;>;]
   *   typeParamsBounds <- [[Lscala.collection.immutable.List<[Ljava.lang.String;>;]]
   *   typeParamsBoundsReadable <- [[scala.collection.immutable.List<java.lang.String[]>]]
   *   returnTypeSig <- TU;
   *   returnType <- U
   *   expceptionTypes <- []
   * }}}
   */
  class JavaSignature(symbol: Symbol) {
    import org.eclipse.jdt.core.Signature

    // Erasure.needsJavaSig was made private in scala/scala ee6f3864
    def rebindInnerClass(pre: Type, cls: Symbol): Type =
      if (cls.isTopLevel || cls.isLocalToBlock) pre else cls.owner.tpe_*

    private object NeedsSigCollector extends TypeCollector(false) {
      def traverse(tp: Type): Unit = {
        if (!result) {
          tp match {
            case st: SubType =>
              traverse(st.supertype)
            case TypeRef(pre, sym, args) =>
              if (sym == definitions.ArrayClass) args foreach traverse
              else if (sym.isTypeParameterOrSkolem || sym.isExistentiallyBound || !args.isEmpty) result = true
              else if (sym.isClass) traverse(rebindInnerClass(pre, sym)) // #2585
              else if (!sym.isTopLevel) traverse(pre)
            case PolyType(_, _) | ExistentialType(_, _) =>
              result = true
            case RefinedType(parents, _) =>
              parents foreach traverse
            case ClassInfoType(parents, _, _) =>
              parents foreach traverse
            case AnnotatedType(_, atp) =>
              traverse(atp)
            case _ =>
              mapOver(tp)
          }
        }
      }
    }

    private def erasureNeedsJavaSig(tp: Type, throwsArgs: List[Type]) = !settings.Ynogenericsig && {
      def needs(tp: Type) = NeedsSigCollector.collect(tp)
      needs(tp) || throwsArgs.exists(needs)
    }

    // see scala/scala commit e5ea3ab
    private val markClassUsed: Symbol => Unit = _ => ()

    private lazy val sig: Option[String] = {
      // make sure to execute this call in the presentation compiler's thread
      pc.asyncExec {
        def needsJavaSig: Boolean = {
          // there is no need to generate the generic type information for local symbols
          !symbol.isLocalToBlock && erasureNeedsJavaSig(symbol.info, throwsArgs = Nil)
        }

        if (needsJavaSig) {
          // it's *really* important we ran pc.atPhase so that symbol's type is updated! (atPhase does side-effects on the type!)
          for (signature <- erasure.javaSig(symbol, pc.enteringPhase(pc.currentRun.erasurePhase)(symbol.info), markClassUsed))
            yield signature.replace("/", ".")
        } else None
      }.getOrElse(None)()
    }

    def isDefined: Boolean = sig.isDefined

    def paramsCount: Int =
      sig.map(Signature.getParameterCount).getOrElse(0)

    def paramsType: Array[Array[Char]] =
      paramsTypeSig.map(p => Signature.toCharArray(p.toArray))

    def paramsTypeSig: Array[String] =
      sig.map(Signature.getParameterTypes).getOrElse(Array.empty)

    def typeVars: Array[String] =
      typeParamsSig.map(Signature.getTypeVariable)

    def typeParamsSig: Array[String] =
      sig.map(Signature.getTypeParameters).getOrElse(Array.empty)

    def typeParamsBounds: Array[Array[String]] =
      typeParamsSig.map(Signature.getTypeParameterBounds)

    def typeParamsBoundsReadable: Array[Array[Array[Char]]] =
      typeParamsBounds.map(_.map(s => Signature.toCharArray(s.toArray)))

    def returnTypeSig: Option[String] =
      sig.map(Signature.getReturnType)

    def returnType: Option[String] =
      returnTypeSig.map(r => Signature.toCharArray(r.toArray).mkString)

    def exceptionTypes: Array[Array[Char]] =
      sig.map(Signature.getThrownExceptionTypes).getOrElse(Array.empty[String]).map(_.toCharArray)
  }
}

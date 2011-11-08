package scala.tools.eclipse

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
   * 	sig <- <T:LFoo;U:Ljava.util.ArrayList<-Ljava.lang.Class<TT;>;>;>(TU;TT;)TT;
   * 	paramsCount <- 2
   * 	paramsType <- [[U], [T]]
   * 	paramsTypeSig <-  [TU;, TT;]
   * 	typeVars <- [T, U]
   * 	typeParamsSig <- [T:LFoo;, U:Ljava.util.ArrayList<-Ljava.lang.Class<TT;>;>;]
   * 	typeParamsBounds <- [[LFoo;], [Ljava.util.ArrayList<-Ljava.lang.Class<TT;>;>;]]
   * 	typeParamsBoundsReadable <- [[Foo], [java.util.ArrayList<? super java.lang.Class<T>>]]
   * 	returnTypeSig <- TT;
   * 	returnType <- T
   * 	exceptionTypes <- []
   * }}}
   *
   * @example Given the following method declaration:
   * {{{ def foo[U <: List[Array[String]]](u: U, s: Int):U  = u }}}
   * 
   * The following are the values computed by JavaSignatureConverter:
   * {{{
   * 	sig <- <U:Lscala.collection.immutable.List<[Ljava.lang.String;>;>(TU;I)TU;
   * 	paramsCount <- 2
   * 	paramsType <- [[U], [i, n, t]]
   * 	paramsTypeSig <- [TU;, I]
   * 	typeVars <- [U]
   * 	typeParamsSig <- [U:Lscala.collection.immutable.List<[Ljava.lang.String;>;]
   * 	typeParamsBounds <- [[Lscala.collection.immutable.List<[Ljava.lang.String;>;]]
   * 	typeParamsBoundsReadable <- [[scala.collection.immutable.List<java.lang.String[]>]]
   * 	returnTypeSig <- TU;
   * 	returnType <- U
   * 	expceptionTypes <- []
   * }}}
   */
  class JavaSignature(symbol: Symbol) {
    import org.eclipse.jdt.core.Signature

    private lazy val sig: Option[String] = {
      // make sure to execute this call in the presentation compiler's thread
      pc.askOption { () =>
        def needsJavaSig: Boolean = {
          // there is no need to generate the generic type information for local symbols
          !symbol.isLocal && erasure.needsJavaSig(symbol.info)
        }
        
        if (needsJavaSig) {
	      // it's *really* important we ran pc.atPhase so that symbol's type is updated! (atPhase does side-effects on the type!)
	      for (signature <- erasure.javaSig(symbol, pc.atPhase(pc.currentRun.erasurePhase)(symbol.info)))
	        yield signature.replace("/", ".")
	    } 
	    else None
      }.getOrElse(None)
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
      sig.map(Signature.getThrownExceptionTypes).getOrElse(Array.empty).map(_.toCharArray)
  }
}
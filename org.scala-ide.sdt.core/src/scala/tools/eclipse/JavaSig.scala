package scala.tools.eclipse

trait JavaSig { pc: ScalaPresentationCompiler =>

  /** Returns the symbol's `JavaSignature`*/
  def javaSigOf(sym: Symbol): JavaSignature = new JavaSignature(sym)

  /*
   * An utility class that allows to extract information from the `symbol`'s java signature.
   * If the passed `symbol` does not need a Java signature, default (empty) values will be 
   * returned where it makes sense.
   * 
   * Examples:
   * (1) Given the following method declaration:
   * <code>
   * 	def foo[T <: Foo, U <: ArrayList[_ >: Class[T]]](u: U, t: T):T  = t
   * </code>
   * 
   * The following are the values computed by JavaSignatureConverter:
   * <code>
   * 	sig <- <T:LFoo;U:Ljava.util.ArrayList<-Ljava.lang.Class<TT;>;>;>(TU;TT;)TT;
   * 	paramsCount <- 2
   * 	paramsType <- [[U], [T]]
   * 	paramsTypeSig <-  [TU;, TT;]
   * 	typeVars <- [T, U]
   * 	typeParamsSig <- [T:LFoo;, U:Ljava.util.ArrayList<-Ljava.lang.Class<TT;>;>;]
   * 	typeParamsBounds <- [[LFoo;], [Ljava.util.ArrayList<-Ljava.lang.Class<TT;>;>;]]
   * 	typeParamsBoundsReadable <- [[F, o, o], [j, a, v, a, ., u, t, i, l, ., A, r, r, a, y, L, i, s, t, <, ?,  , s, u, p, e, r,  , j, a, v, a, ., l, a, n, g, ., C, l, a, s, s, <, T, >, >]]
   * 	returnTypeSig <- TT;
   * 	returnType <- T
   * 	exceptionTypes <- []
   * </code>
   *
   * (2) Given the following method declaration:
   * <code>
   * 	def foo[U <: List[Array[String]]](u: U, s: Int):U  = u
   * </code>
   * 
   * The following are the values computed by JavaSignatureConverter:
   * <code>
   * 	sig <- <U:Lscala.collection.immutable.List<[Ljava.lang.String;>;>(TU;I)TU;
   * 	paramsCount <- 2
   * 	paramsType <- [[U], [i, n, t]]
   * 	paramsTypeSig <- [TU;, I]
   * 	typeVars <- [U]
   * 	typeParamsSig <- [U:Lscala.collection.immutable.List<[Ljava.lang.String;>;]
   * 	typeParamsBounds <- [[Lscala.collection.immutable.List<[Ljava.lang.String;>;]]
   * 	typeParamsBoundsReadable <- [[s, c, a, l, a, ., c, o, l, l, e, c, t, i, o, n, ., i, m, m, u, t, a, b, l, e, ., L, i, s, t, <, j, a, v, a, ., l, a, n, g, ., S, t, r, i, n, g, [, ], >]]
   * 	returnTypeSig <- TU;
   * 	returnType <- U
   * 	expceptionTypes <- []
   * </code>
   */
  class JavaSignature(sym: Symbol) {
    import org.eclipse.jdt.core.Signature

    private lazy val sig: Option[String] = {
      // make sure to execute this call in the presentation compiler's thread
      pc.ask { () =>
	      if (erasure.needsJavaSig(sym.info)) {
	        // it's *really* important we ran pc.atPhase so that symbol's type is updated! (atPhase does side-effects on the type!)
	        for (signature <- erasure.javaSig(sym, pc.atPhase(pc.currentRun.erasurePhase)(sym.info)))
	          yield signature.replace("/", ".")
	      } 
	      else None
      }
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
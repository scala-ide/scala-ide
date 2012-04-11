package scala.tools.eclipse.semantichighlighting.classifier

/**
 * Debugging info about the symbols
 */
trait SymbolClassificationDebugger { self: SymbolClassification =>

  import global._

  def printSymbolInfo() {
    println()
    println()
    println(" -- source -----------------------------------------------------------------------------")
    println(sourceFile.content.mkString)
    println(" -- scalariform ------------------------------------------------------------------------")
    import syntacticInfo._
    println("namedArgs: " + namedArgs)
    println("forVals: " + forVals)
    println("maybeSelfRefs: " + maybeSelfRefs)
    println("maybeClassOfs: " + maybeClassOfs)
    println("annotations: " + annotations)
    println(" -- symbols ----------------------------------------------------------------------------")

    //allSymbols filterNot (_ == NoSymbol) sortBy (_.toString) foreach printSym
  }

  private def printSym(sym: Symbol) {
    def printFlag(prop: String, symPred: Symbol => Boolean) = print(pad(if (symPred(sym)) prop else "", prop.size + 1))
    print(pad(sym, 25))
    //    print(pad(System.identityHashCode(sym), 25))
    print(pad(sym.pos, 45))
    print(pad(sym.name, 14))
    print(pad(sym.getClass.getSimpleName, 16))
    print(pad(getSymbolType(sym), 20))
    printFlag("companionClass.isCaseClass", _.companionClass.isCaseClass)
    printFlag("caseClass", _.isCaseClass)
    printFlag("case", _.isCase)
    printFlag("interface", _.isInterface)
    printFlag("class", _.isClass)
    printFlag("javaDefined", _.isJavaDefined)
    printFlag("javaInterface", _.isJavaInterface)
    printFlag("lazy", _.isLazy)
    printFlag("local", _.isLocal)
    printFlag("method", _.isMethod)
    printFlag("module", _.isModule)
    printFlag("package", _.isPackage)
    printFlag("parameter", _.isParameter)
    printFlag("skolem", _.isSkolem)
    printFlag("sourceMethod", _.isSourceMethod)
    printFlag("synthetic", _.isSynthetic)
    printFlag("term", _.isTerm)
    printFlag("trait", _.isTrait)
    printFlag("type", _.isType)
    printFlag("typeParameter", _.isTypeParameter)
    printFlag("value", _.isValue)
    printFlag("variable", _.isVariable)

    println()
//    for (occurrence <- index.occurences(sym)) {
//      print(pad(" \\-" + occurrence.getClass.getSimpleName, 16))
//      print(pad("   " + occurrence.namePosition, 45))
//      println()
//    }
    for (annotation <- sym.annotations) {
      print(pad(" @-" + annotation, 95))
      val atp = annotation.atp
      val sym = atp.selfsym
      print(pad(" " + sym, 35))
      println()
    }
  }

  def println() { Predef.println() }
  def println(o: => Any) { Predef.println(o) }
  def print(o: => Any) { Predef.print(o) }
  def pad(s: Any, n: Int) = s.toString.take(n).padTo(n, ' ')

}
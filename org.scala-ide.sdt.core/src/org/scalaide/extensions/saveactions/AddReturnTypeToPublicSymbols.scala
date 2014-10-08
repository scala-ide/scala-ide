package org.scalaide.extensions
package saveactions

object AddReturnTypeToPublicSymbolsSetting extends SaveActionSetting(
  id = ExtensionSetting.fullyQualifiedName[AddReturnTypeToPublicSymbols],
  name = "Add return type to public symbols",
  description =
    "Adds the return type to all public symbols when they not yet exist." +
    " The symbols that can be public and can have types are defs, vars and vals.",
  codeExample = """|class X {
                   |  def meth = new java.io.File("")
                   |  val value = new java.io.File("")
                   |  var value = new java.io.File("")
                   |}
                   |""".stripMargin
)

trait AddReturnTypeToPublicSymbols extends SaveAction with CompilerSupport {
  import global._

  override def setting = AddReturnTypeToPublicSymbolsSetting

  override def perform() = {
    val symbolWithoutReturnType = filter {
      case d @ ValOrDefDef(_, _, tpt: TypeTree, _) =>
        def isHidden(s: Symbol): Boolean =
          if (s.isMethod || s.isValue)
            true
          else if (s.isClass)
            if (s.isPublic)
              isHidden(s.owner)
            else
              true
          else
            false

        val o = d.symbol.owner
        if (tpt.symbol.isRefinementClass || isHidden(o))
          false
        else
          d match {
            case d: DefDef =>
               d.symbol.isPublic && !d.symbol.isSynthetic && !d.symbol.isAccessor && tpt.original == null
            case d: ValDef =>
              val getter = d.symbol.getterIn(o)
              getter.isPublic && tpt.original == null
          }
    }

    val validSymbol = filter {
      case d @ ValOrDefDef(_, TermName(name), tpt, _) =>
        val t = tpt.tpe
        !(name == "$init$" || name == "<init>" || t =:= typeOf[Nothing] || t =:= typeOf[Null] || t.isErroneous)
    }

    val addReturnType = transform {
      case d @ ValOrDefDef(_, _, tpt: TypeTree, _) =>
        val newTpt = tpt setOriginal mkReturn(List(tpt.tpe.typeSymbol))
        d match {
          case d: DefDef => d.copy(tpt = newTpt) replaces d
          case d: ValDef => d.copy(tpt = newTpt) replaces d
        }
    }

    val refactoring = topdown {
      matchingChildren {
        symbolWithoutReturnType &> validSymbol &> addReturnType
      }
    }
    transformFile(refactoring)
  }
}

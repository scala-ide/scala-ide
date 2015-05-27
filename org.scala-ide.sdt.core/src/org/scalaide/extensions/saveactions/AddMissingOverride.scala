package org.scalaide.extensions
package saveactions

import scala.tools.nsc.ast.parser.Tokens

object AddMissingOverrideSetting extends SaveActionSetting(
  id = ExtensionSetting.fullyQualifiedName[AddMissingOverride],
  name = "Add missing override keyword (experimental)",
  description =
    """|Adds the override keyword to all symbols that override another symbol.
       |
       |Note: This save action is marked as experimental because it relies on compiler \
       |support. This means that on the one side it may need a lot of time to complete \
       |and on the other side it may introduce compilation errors due to the fact that \
       |it relies on tree refactorings. You can enable this save action but please \
       |consider to disable it again when it interferes in any way with your work \
       |approach. The Scala IDE team would be happy when you also report back any \
       |problems that you have with this save action.
       |""".stripMargin.replaceAll("\\\\\n", ""),
  codeExample = """|trait T {
                   |  def method: Int
                   |  def value: Int
                   |  type Type
                   |}
                   |class C extends T {
                   |  def method = 0
                   |  def value = 0
                   |  type Type = Int
                   |}
                   |""".stripMargin
)

trait AddMissingOverride extends SaveAction with CompilerSupport {
  import global._

  override def setting = AddMissingOverrideSetting

  override def perform() = {
    def canOverride(sym: Symbol) = sym.isOverridingSymbol && !sym.isOverride && !sym.isAbstractOverride

    val symbolWithoutOverride = filter {
      case d: ValOrDefDef if overridesJavaField(d.symbol) ⇒
        false

      case d: ValDef if d.mods.positions.contains(Tokens.VAR) && !overridesVar(d.symbol) ⇒
        false

      case d: MemberDef =>
        canOverride(accessorOf(d.symbol))
    }

    val addOverrideKeyword = transform {
      case d: MemberDef =>
        val mods = d.mods.withFlag(Flag.OVERRIDE)
        d match {
          case d: DefDef =>
            val lazyValMods =
              if (mods.hasFlag(Flag.LAZY))
                if (mods.positions.contains(Flag.LAZY))
                  mods
                else
                  mods.withFlag(Flag.LAZY).withFlag(Tokens.VAL)
              else
                mods

            d.copy(mods = lazyValMods) replaces d

          case d: ValDef =>
            val valMods =
              if (mods.positions.contains(Tokens.VAR) || mods.positions.contains(Tokens.VAL))
                mods
              else
                mods.withFlag(Tokens.VAL)

            d.copy(mods = valMods) replaces d

          case d: TypeDef =>
            d.copy(mods = mods) replaces d
        }
    }

    val refactoring = topdown {
      matchingChildren {
        symbolWithoutOverride &> addOverrideKeyword
      }
    }
    transformFile(refactoring)
  }

  /**
   * Returns the getter of `symbol` or `symbol` itself if no getter exists.
   */
  private def accessorOf(symbol: Symbol): Symbol =
    if (symbol.hasGetter) symbol.getterIn(symbol.owner) else symbol

  /**
   * Returns true if `symbol` overrides a var.
   */
  private def overridesVar(symbol: Symbol): Boolean = {
    val s = superSymbolOf(accessorOf(symbol))
    s.setterIn(s.owner) != NoSymbol
  }

  /**
   * Returns true if `symbol` overrides a field defined with Java.
   */
  private def overridesJavaField(symbol: Symbol): Boolean = {
    val s = superSymbolOf(accessorOf(symbol))
    s.isJava && !s.isMethod
  }

  /**
   * Finds the symbol which is overridden by `symbol`. If there exists more than
   * one (which can be the case in a deep inheritance hierarchy) the first one
   * that is found is returned.
   *
   * Returns `NoSymbol` if no overridden symbol is found.
   */
  private def superSymbolOf(symbol: Symbol): Symbol = {
    val base = symbol.owner
    val baseType = base.toType
    val bcs = base.info.baseClasses.iterator dropWhile (symbol.owner != _) drop 1

    bcs map (symbol.matchingSymbol(_, baseType)) find (_ != NoSymbol) getOrElse NoSymbol
  }
}

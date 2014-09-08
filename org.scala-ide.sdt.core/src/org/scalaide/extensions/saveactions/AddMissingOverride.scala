package org.scalaide.extensions
package saveactions

import scala.tools.nsc.ast.parser.Tokens

object AddMissingOverrideSetting extends SaveActionSetting(
  id = ExtensionSetting.fullyQualifiedName[AddMissingOverride],
  name = "Add missing override keyword",
  description = "Adds the override keyword to all symbols that override another symbol.",
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
      case d: ValDef if d.mods.positions.contains(Tokens.VAR) && !overridesVar(getterOf(d.symbol)) ⇒
        false

      case d: ValDef =>
        val getter = getterOf(d.symbol)
        canOverride(if (getter != NoSymbol) getter else d.symbol)

      case d @ (_: DefDef | _: TypeDef) =>
        canOverride(d.symbol)
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

  private def getterOf(sym: Symbol): Symbol =
    sym.getterIn(sym.owner)

  private def overridesVar(symbol: Symbol): Boolean = {
    val base = symbol.owner
    val baseType = base.toType
    val bcs = base.info.baseClasses dropWhile (symbol.owner != _) drop 1

    bcs exists { sym ⇒
      symbol.matchingSymbol(sym, baseType).setterIn(sym) != NoSymbol
    }
  }
}

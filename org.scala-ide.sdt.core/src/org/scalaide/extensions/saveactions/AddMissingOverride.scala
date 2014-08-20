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
      case d: ValDef =>
        val getter = d.symbol.getterIn(d.symbol.owner)
        canOverride(if (getter != NoSymbol) getter else d.symbol)

      case d @ (_: DefDef | _: TypeDef) =>
        canOverride(d.symbol)
    }

    val addOverrideKeyword = transform {
      case d: MemberDef =>
        val mods = d.mods.withFlag(Flag.OVERRIDE)
        d match {
          case d: DefDef =>
            d.copy(mods = mods) replaces d

          case d: ValDef =>
            val valMods = if (mods.positions.contains(Tokens.VAL)) mods else mods.withFlag(Tokens.VAL)
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
}

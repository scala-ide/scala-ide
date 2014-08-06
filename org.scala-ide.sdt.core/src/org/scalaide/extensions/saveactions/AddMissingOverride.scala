package org.scalaide.extensions
package saveactions

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

/**
 * This save action is not yet completed. It does't add the `override` keyword
 * to vals, only to defs and types.
 */
trait AddMissingOverride extends SaveAction with CompilerSupport {
  import global._

  def setting = AddMissingOverrideSetting

  def perform() = {
    val symbolWithoutOverride = filter {
      case d @ (_: DefDef | _: TypeDef) =>
        d.symbol.isOverridingSymbol && !d.symbol.isOverride
    }

    /**
     * Alternative (preferred) implementation:
     * {{{
     * mods.copy().withPosition(Flag.OVERRIDE, pos)
     * }}}
     * The above does not work due to a limitation in scala-refactoring.
     */
    def modsWithOverrideKeyword(mods: Modifiers, pos: Position): Modifiers =
      mods.copy() setPositions Map(Flag.OVERRIDE -> pos) ++ mods.positions

    val addOverrideKeyword = transform {
      case d: DefDef =>
        d.mods.copy() withPosition(Flag.OVERRIDE, d.pos)
        val mods = modsWithOverrideKeyword(d.mods, d.pos)
        d.copy(mods = mods | Flag.OVERRIDE) replaces d

      case d: TypeDef =>
        val mods = modsWithOverrideKeyword(d.mods, d.pos)
        d.copy(mods = mods | Flag.OVERRIDE) replaces d
    }

    val refactoring = topdown {
      matchingChildren {
        symbolWithoutOverride &> addOverrideKeyword
      }
    }
    transformFile(refactoring)
  }
}
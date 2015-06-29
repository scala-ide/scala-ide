package org.scalaide.core.testsetup

object SdtTestConstants {

  /**
   * Used in JUnit's `@Ignore` annotations. Don't add type here. With no type scala compiler compiles
   * is to `constant type String("TODO - this test...")`.
   */
  final val TestRequiresGuiSupport =
    "TODO - this test triggers an eclipse bundle that requires GUI support, which is not available on the build server"
}

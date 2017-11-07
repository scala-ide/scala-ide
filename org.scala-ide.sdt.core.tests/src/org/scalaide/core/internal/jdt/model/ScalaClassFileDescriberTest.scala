package org.scalaide.core.internal.jdt.model

import org.junit.Test
import org.junit.Assert

class ScalaClassFileDescriberTest {
  import ScalaClassFileDescriber.isScala

  @Test
  def shouldGiveNoneBecauseItIsNotBytecode(): Unit = {
    val fakeBytecode = "surely not bytecode"

    val actual = isScala(fakeBytecode.toCharArray.map(_.toByte))

    Assert.assertTrue(None == actual)
  }
}
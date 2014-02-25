package org.scalaide.core.structurebuilder

private[structurebuilder] trait BaseTestOracle {
  final def expectedFragment = makeOSAgnostic(oracle).trim

  private def makeOSAgnostic(text: String): String = text.replaceAll("\\r\\n","\n")

  protected def oracle: String
}
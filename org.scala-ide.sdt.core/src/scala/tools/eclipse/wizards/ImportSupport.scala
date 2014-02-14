/*
 * Copyright 2010 LAMP/EPFL
 *
 */
package scala.tools.eclipse.wizards

import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.jdt.core.dom.CompilationUnit

import collection.immutable.SortedSet
import collection.mutable.{ Map => MutMap }
import collection.mutable.{ Set => MutSet }

trait ImportSupport extends QualifiedNameSupport with BufferSupport {
  def addImport(qualifiedTypeName: String): String
  def addImports(qualifiedTypeNames: Seq[String]): Unit
  def getImports: List[String]
}

object ImportSupport {

  def apply(packageName: String): ImportSupport = {
    new ImportSupportImpl(Set("scala", "java.lang", packageName))
  }

  private class ImportSupportImpl(ignoredKeys: Set[String])
    extends ImportSupport {

  val importMap: MutMap[String, MutSet[String]] = MutMap.empty

  def addImport(qualifiedTypeName: String): String = {
    val (key, value) = packageAndTypeNameOf(qualifiedTypeName)
    importMap.getOrElseUpdate(key, MutSet(value)) + value
    value
  }

  def addImports(qualifiedTypeNames: Seq[String]) {
    qualifiedTypeNames foreach addImport
  }

  def getImports = {

    def toBuilder(set: MutSet[String]): String =
      if (set.size == 1) set.head else set.mkString("{ ", ", ", " }")

    def thatAreImplicit(key: String) = !ignoredKeys.contains(key)

    def concatKeyWithNames(kv: (String, String)) = kv._1 + "." + kv._2

    importMap.filterKeys(thatAreImplicit).
      mapValues(toBuilder).toList.map(concatKeyWithNames)
  }

  protected def contents(implicit lineDelimiter: String) =
    templates.importsTemplate(getImports)
  }
}

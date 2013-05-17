/*
 * Copyright 2010 LAMP/EPFL
 * @author Tim Clendenen
 *
 */
package scala.tools.eclipse.wizards

object BufferSupport {

  type Buffer = {
    def append(s: String): Unit
    def getLength(): Int
  def replace(offset: Int, length: Int, text: String): Unit
  def getContents(): String
  }

  implicit protected[wizards] def stringBuilderToBuffer(bldr: StringBuilder) =
    new BuilderAdapter(bldr)
}

trait BufferSupport {

  import BufferSupport._

  var offset = -1
  var length = 0

  protected def contents(implicit ld: String): String

  def writeTo(buffer: Buffer)(implicit ld: String) {
    val s = contents

    if(offset == -1) {
    offset = buffer.getLength
    buffer.append(s)
  }
  else {
    buffer.replace(offset, length, s)
  }

  length = s.length
  }
}

private[wizards] class BuilderAdapter(sb: StringBuilder) {
  def append(s: String): Unit = sb.append(s)
  def getLength(): Int = sb.length
  def replace(offset: Int, length: Int, text: String): Unit =
    sb.replace(offset, length, text)
  def getContents() = sb.toString
}

trait SuperTypeSupport {
  type PackageName
  type TypeName
  type Parameters

  type SuperType = Triple[PackageName, TypeName, Parameters]
}

/**
 * Functions for working with qualified types
 */
trait QualifiedNameSupport extends SuperTypeSupport {
  type PackageName = List[String]
  type TypeName = String
  type Parameters = List[String]

  private val toScalaParameters = (c: Char) => c != '['

  private val toJavaParameters = (c: Char) => c != '<'

  private val splitOffParameters = (s: String) =>
    if(s.contains('['))
      s span toScalaParameters
    else if(s.contains('<'))
      s span toJavaParameters
    else
      (s, "")

  private val removeBrackets = (s: String) =>
    if(s.size > 2) s.slice(1, s.length-1) else ""

  private val makeList = (s: String) =>
    if(s.length > 0) s.split(',').toList else Nil

  private val listOf = makeList compose removeBrackets

  val withoutPackage = (st: SuperType) => {
  val st3 = if(st._3.nonEmpty) st._3.mkString("[",",","]") else ""
    st._2 + st3
  }

  val withoutParameters = (st: SuperType) => {
    concat((concat(st._1), st._2))
  }

  val removePackage = (typeDeclaration: String) => {
    val f = withoutPackage compose createSuperType
    f(typeDeclaration)
  }

  val removeParameters = (typeDeclaration: String) => {
    val f = withoutParameters compose createSuperType
    f(typeDeclaration)
  }

  val packageOf = (typeDeclaration: String) => {
  val (pn, tn) = packageAndTypeNameOf(typeDeclaration)
  pn
  }

  val typeNameOf = (typeDeclaration: String) => {
  val (pn, tn) = packageAndTypeNameOf(typeDeclaration)
  tn
  }

  val parametersOf = (typeDeclaration: String) => {
  val (packageAndType, params) = splitOffParameters(typeDeclaration)
  listOf(params)
  }

  val packageAndTypeNameOf = (typeDeclaration: String) => {
    val st = createSuperType(typeDeclaration)
    (concat(st._1), st._2)
  }

  def concat(vals: Any, sep: String = ".") = {
    vals match {
      case l: List[_] => l.mkString(sep)
      case (v1, v2) => v1 + sep + v2
    }
  }

  val createSuperType = (typeDeclaration: String) => {
    val (packageAndType, params) = splitOffParameters(typeDeclaration)
    val splitName = packageAndType.split('.').toList
    new SuperType(splitName.init, splitName.last, listOf(params))
  }
}

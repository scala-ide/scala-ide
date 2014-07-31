package org.scalaide.ui.internal.wizards

object BufferSupport {

  trait Buffer {
    def append(s: String): Unit
    def getLength(): Int
    def replace(offset: Int, length: Int, text: String): Unit
    def getContents(): String
  }

  private[wizards] implicit class BuilderAdapter(private val __sb: StringBuilder) extends AnyVal {
    def append(s: String): Unit = __sb.append(s)
    def getLength(): Int = __sb.length
    def replace(offset: Int, length: Int, text: String): Unit =
      __sb.replace(offset, length, text)
    def getContents() = __sb.toString
  }
}

trait BufferSupport {

  import BufferSupport._

  var offset = -1
  var length = 0

  protected def contents(implicit lineDelimiter: String): String

  def writeTo(buffer: Buffer)(implicit lineDelimiter: String) {
    val s = contents

    if (offset == -1) {
      offset = buffer.getLength
      buffer.append(s)
    } else {
      buffer.replace(offset, length, s)
    }

    length = s.length
  }
}

trait SuperTypeSupport {
  type PackageName
  type TypeName
  type Parameters

  case class SuperType(packageName: PackageName, typeName: TypeName, params: Parameters)
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
    if (s.contains('['))
      s span toScalaParameters
    else if (s.contains('<'))
      s span toJavaParameters
    else
      (s, "")

  private val removeBrackets = (s: String) =>
    if (s.size > 2) s.slice(1, s.length - 1) else ""

  private val makeList = (s: String) =>
    if (s.length > 0) s.split(',').toList else Nil

  private val listOf = makeList compose removeBrackets

  val withoutPackage = (st: SuperType) => {
    val st3 = if (st.params.nonEmpty) st.params.mkString("[", ",", "]") else ""
    st.typeName + st3
  }

  val withoutParameters = (st: SuperType) => {
    concat((concat(st.packageName), st.typeName))
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
    val (pn, _) = packageAndTypeNameOf(typeDeclaration)
    pn
  }

  val typeNameOf = (typeDeclaration: String) => {
    val (_, tn) = packageAndTypeNameOf(typeDeclaration)
    tn
  }

  val parametersOf = (typeDeclaration: String) => {
    val (_, params) = splitOffParameters(typeDeclaration)
    listOf(params)
  }

  val packageAndTypeNameOf = (typeDeclaration: String) => {
    val st = createSuperType(typeDeclaration)
    (concat(st.packageName), st.typeName)
  }

  def concat(vals: Any, separator: String = "."): String = vals match {
    case l: List[_] => l.mkString(separator)
    case (v1, v2)   => v1 + separator + v2
  }

  val createSuperType = (typeDeclaration: String) => {
    val (packageAndType, params) = splitOffParameters(typeDeclaration)
    val splitName = packageAndType.split('.').toList
    new SuperType(splitName.init, splitName.last, listOf(params))
  }
}

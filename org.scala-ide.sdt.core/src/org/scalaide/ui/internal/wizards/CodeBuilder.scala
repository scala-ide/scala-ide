package org.scalaide.ui.internal.wizards

import org.eclipse.jdt.core.Flags
import org.eclipse.jdt.core.IField
import org.eclipse.jdt.core.IMethod
import org.eclipse.jdt.core.IType
import org.eclipse.jdt.core.ITypeHierarchy
import org.eclipse.jdt.core.Signature
import scala.collection.mutable.HashMap
import scala.collection.mutable.ListBuffer
import scala.reflect.NameTransformer
import org.scalaide.core.compiler.ScalaPresentationCompilerProxy

trait CodeBuilder {

  import CodeBuilder._
  import BufferSupport._

  val imports: ImportSupport
  val buffer: Buffer
  val compilerProxy: ScalaPresentationCompilerProxy

  protected val convertAndAdd = (s: String) => {
    imports.addImport(convertSignature(s))
  }

  def append(s: String): CodeBuilder = {
    buffer.append(s)
    this
  }

  def writeImports(implicit lineDelimiter: String) {
    imports.writeTo(buffer)
  }

  def finishReWrites(createdType: IType)(createConstructorsSelected: Boolean)(createInheritedSelected: Boolean)(createMainSelected: Boolean)(implicit lineDelimiter: String) {
    val sb = new StringBuilder

    if (createConstructorsSelected)
      sb.append(unimplemetedConstructors(createdType))

    if (createInheritedSelected)
      sb.append(unimplemetedMethods(createdType))

    if (createMainSelected)
      sb.append(createMain)

    val idx = buffer.getContents.indexOf(templates.bodyStub)
    buffer.replace(idx, templates.bodyStub.length, s" {$lineDelimiter$sb$lineDelimiter}")

    // order matters here
    List("extends", "name") foreach { s =>
      lhm.get(s) foreach (_.writeTo(buffer))
    }
    writeImports
  }

  def addConstructorArgs(args: Args) {
    val ns = if (args.as.nonEmpty) eval(args) else ""
    val nb = lhm.get("name").get.asInstanceOf[NameBufferSupport]
    lhm.put("name", new NameBufferSupport(nb, ns))

    val e = if (args.as.nonEmpty) eval(ParenList(args.as.map(a => a.n))) else ""
    val eb = lhm.get("extends").get.asInstanceOf[ExtendsBufferSupport]
    lhm.put("extends", new ExtendsBufferSupport(eb, e))
  }

  def createElementDeclaration(name: String, superTypes: List[String], buffer: Buffer)(implicit lineDelimiter: String) {
    val nameBuffer = new NameBufferSupport(" " + name)
    lhm.put("name", nameBuffer)
    nameBuffer.writeTo(buffer)

    val extendsBuffer = new ExtendsBufferSupport(superTypes)
    lhm.put("extends", extendsBuffer)
    extendsBuffer.writeTo(buffer)
  }

  def createMain(implicit lineDelimiter: String): String =
    s"$lineDelimiter  def main(args: Array[String]): Unit = {  }$lineDelimiter"

  def unimplemetedConstructors(newType: IType)(implicit lineDelimiter: String): String

  def unimplemetedMethods(newType: IType)(implicit lineDelimiter: String): String
}

object CodeBuilder {

  import BufferSupport._

  def apply(packageName: String, superTypes: List[String], buffer: Buffer, proxy: ScalaPresentationCompilerProxy): CodeBuilder = {
    val imports = ImportSupport(packageName)
    imports.addImports(superTypes)
    new CodeBuilderImpl(imports, superTypes, buffer, proxy)
  }

  private val lhm: HashMap[String, BufferSupport] = HashMap.empty

  private class NameBufferSupport(val name: String, val cons: String = "") extends BufferSupport {

    def this(buffer: NameBufferSupport, cons: String) {
      this(buffer.name, cons)
      offset = buffer.offset
      length = buffer.length
    }

    protected def contents(implicit lineDelimiter: String) = name + cons
  }

  private class ExtendsBufferSupport(val superTypes: List[String], val cons: String = "") extends BufferSupport {

    def this(buffer: ExtendsBufferSupport, cons: String) {
      this(buffer.superTypes, cons)
      offset = buffer.offset
      length = buffer.length
    }

    import templates._

    protected def contents(implicit lineDelimiter: String) = {
      val t = typeTemplate(superTypes)
      val a = t.split(" ")
      if (a.length > 1)
        a(2) = a(2) + cons
      a.mkString(" ")
    }
  }

  sealed abstract class Part

  case class Type(n: Name) extends Part
  case class TypeParam(n: Name, b: TypeBounds) extends Part
  case class TypeBounds(lo: Type = NothingBound, hi: Type = AnyBound, view: Option[Type] = None) extends Part
  case class TypeParams(tp: Option[List[TypeParam]]) extends Part
  case class Value(s: String) extends Part
  case class Mods(s: Option[String]) extends Part
  case class Name(s: String) extends Part
  case class Arg(n: Name, t: Type) extends Part
  case class Result(t: Type, v: Value) extends Part
  case class ParenList(ps: List[Part]) extends Part
  class ParamNames(val pn: List[Name]) extends ParenList(pn)
  class Args(val as: List[Arg]) extends ParenList(as)
  case class Func(mods: Mods, name: Name, typeParams: TypeParams, args: Args, result: Result) extends Part
  case class ConsBody(pn: ParamNames) extends Part
  case class AuxCons(args: Args, body: ConsBody) extends Part

  object AnyBound extends Type(Name("Any"))
  object NothingBound extends Type(Name("Nothing"))

  def eval(p: Part): String = p match {
    case Type(n)             => eval(n)
    case TypeParam(n, b)     => eval(n) + eval(b)
    case TypeBounds(l, h, v) => " >: " + eval(l) + " <: " + eval(h)
    case TypeParams(o)       => o.map(_.map(t => eval(t)).mkString("[", ", ", "]")).getOrElse("")
    case Value(s)            => s
    case Mods(o)             => o.map(m => m + " ").getOrElse("")
    case Name(s)             => s
    case AuxCons(a, b)       => "def this" + eval(a) + " { " + eval(b) + " }"
    case ConsBody(pn)        => "this" + eval(pn)
    case ParenList(ps)       => ps.map(eval) mkString ("(", ", ", ")")
    case Arg(n, t)           => eval(n) + ": " + eval(t)
    case Result(t, v)        => ": " + eval(t) + " = { " + eval(v) + " }"
    case Func(m, n, t, a, r) => eval(m) + "def " + eval(n) + eval(t) + eval(a) + eval(r)
  }

  def toOption[A](in: A)(guard: => Boolean = (in != null)): Option[A] =
    if (guard) Some(in) else None

  def resultValue(s: String): String = {
    s(0) match {
      case 'V'       => ""
      case 'Z'       => "false"
      case 'S' | 'I' => "0"
      case 'J'       => "0L"
      case 'F'       => "0.0f"
      case 'D'       => "0.0d"
      case _         => "null"
    }
  }

  def convertSignature(s: String): String = {
    s(0) match {
      case 'Z' => "scala.Boolean"
      case 'B' => "scala.Byte"
      case 'C' => "scala.Char"
      case 'S' => "scala.Short"
      case 'I' => "scala.Int"
      case 'J' => "scala.Long"
      case 'F' => "scala.Float"
      case 'D' => "scala.Double"
      case 'V' => "scala.Unit"
      case 'L' => s.substring(1).dropRight(1)
      case '[' => "scala.Array[]"
      case 'Q' => s.substring(1).dropRight(1)
      case _   => "Unknown -> " + s
    }
  }

  private val methodModifiers = (k: IMethod) => {
    val s = Flags.toString(k.getFlags & ~Flags.AccPublic & ~Flags.AccAbstract)
    toOption(s) { s.length > 0 } map (_ + " ")
  }

  private def elementName(k: IMethod) = toOption(k.getElementName)()

  private def returnType(k: IMethod) = toOption(k.getReturnType)()

  private def returnValue(k: IMethod) = toOption(resultValue(k.getReturnType))()

  private def similarMethod(ma: IMethod, xs: ListBuffer[IMethod]): Boolean = {
    def similar(m: IMethod) = {
      ma.getElementName == m.getElementName &&
        ma.getNumberOfParameters == m.getNumberOfParameters &&
        (ma.getParameterTypes.mkString == m.getParameterTypes.mkString ||
          ma.getParameterNames.mkString == m.getParameterNames.mkString)
    }
    xs exists similar
  }

  private def overridenByField(ma: IMethod, xs: ListBuffer[IField]): Boolean = {
    xs exists (_.getElementName == ma.getElementName)
  }

  private val constructorIMethodOrdering = new Ordering[IMethod] {
    def compare(x: IMethod, y: IMethod) =
      y.getNumberOfParameters - x.getNumberOfParameters
  }

  private class CodeBuilderImpl(val imports: ImportSupport, superTypes: List[String], val buffer: Buffer, val compilerProxy: ScalaPresentationCompilerProxy)
      extends QualifiedNameSupport with BufferSupport with CodeBuilder {

    private val generatedMethods: ListBuffer[String] = ListBuffer()
    private val generatedConstructors: ListBuffer[String] = ListBuffer()

    protected def contents(implicit lineDelimiter: String): String =
      withDelimiterToString(generatedConstructors) + withDelimiterToString(generatedMethods)

    /** This code is a translation of the previous attempt (based on JDT Type Hierarchy)
     *  to auto-generate constructor code. It fails in many ways, but it shows how the
     *  presentation compiler could be used to do it.
     */
    override def unimplemetedConstructors(newType: IType)(implicit lineDelimiter: String) = {

      compilerProxy { comp =>
        val sym = comp.rootMirror.getClassIfDefined(superTypes.head)
        val ctors = sym.info.members.filter(_.isConstructor).toSeq

        //      val sastc = astc.sorted(constructorIMethodOrdering)
        for {
          ctor <- ctors
          pn = ctor.info.params map (pn => Name(pn.nameString))
          pt = ctor.info.params map (param => Type(Name(param.info.toString))) //Type(Name(convertAndAdd(pt))))
          nt = pn zip pt map (nt => Arg(nt._1, nt._2))
          ag = new Args(nt)
        } addConstructorArgs(ag)

        for {
          ctor <- ctors
          pn = ctor.info.params map (pn => Name(pn.nameString))
          pt = ctor.info.params map (param => Type(Name(param.info.toString))) //Type(Name(convertAndAdd(pt))))
          nt = pn zip pt map (nt => Arg(nt._1, nt._2))
          if (nt.nonEmpty)
          ag = new Args(nt.init)
          pl = new ParamNames(pn.dropRight(1) :+ Name("null"))
        } {
          println(AuxCons(ag, ConsBody(pl)))
          generatedConstructors += eval(AuxCons(ag, ConsBody(pl)))
        }
        withDelimiterToString(generatedConstructors)
      } getOrElse("")
    }

    /** TODO: Generate stubs for abstract inherited methods */
    override def unimplemetedMethods(newType: IType)(implicit lineDelimiter: String): String = ""

    private def withDelimiterToString(seq: Seq[String])(implicit lineDelimiter: String): String =
      seq.map(str => s"$lineDelimiter  $str$lineDelimiter").mkString
  }
}

object templates extends QualifiedNameSupport {

  val DEFAULT_SUPER_TYPE = "scala.AnyRef"

  def newLine(implicit lineDelimiter: String): (String => String) =
    (s: String) => s + lineDelimiter

  def bodyStub(implicit lineDelimiter: String): String =
    s" {$lineDelimiter$lineDelimiter}"

  def newLines(implicit lineDelimiter: String): (String => String) =
    (s: String) => s + lineDelimiter + lineDelimiter

  def packageTemplate(opt: Option[String])(implicit lineDelimiter: String): String = {
    val g = (s: String) => s"package $s"
    val f = newLines compose g
    opt map f getOrElse ""
  }

  def commentTemplate(opt: Option[String])(implicit lineDelimiter: String): String =
    opt map newLine getOrElse ""

  def importsTemplate(xs: List[String])(implicit lineDelimiter: String): String = {
    val g = (s: String) => s"import $s"
    val f = g compose newLine compose removeParameters
    xs map f reduceLeftOption (_ + _) map newLine getOrElse ""
  }

  private val explicitSuperTypes: List[String] => List[String] = {
    case DEFAULT_SUPER_TYPE :: rest => rest.map(removePackage)
    case xs => xs.map(removePackage)
  }

  private val extendsTemplate: List[String] => String = xs =>
    if (xs.isEmpty) "" else " extends " + xs.mkString(" with ")

  val typeTemplate: List[String] => String =
    extendsTemplate compose explicitSuperTypes
}

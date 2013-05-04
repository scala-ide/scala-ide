/*
 * Copyright 2010 LAMP/EPFL
 *
 * @author Tim Clendenen
 *
 */
package scala.tools.eclipse.wizards

import org.eclipse.jdt.core.{ Flags, IField, IMethod, IType, ITypeHierarchy,
    Signature }

import scala.collection.mutable.{ HashMap, ListBuffer }
import scala.reflect.NameTransformer

trait CodeBuilder {

  import CodeBuilder._
  import BufferSupport._

  val imports: ImportSupport
  val buffer: Buffer

  protected val convertAndAdd = (s: String) => {
    imports.addImport(convertSignature(s))
  }

  def append(s: String): CodeBuilder = {
    buffer.append(s)
    this
  }

  def writeImports(implicit ld: String) {
    imports.writeTo(buffer)
  }

  def finishReWrites(typeHierarchy: ITypeHierarchy, createdType: IType)
                    (createConstructorsSelected: Boolean)
                    (createInheritedSelected: Boolean)
                    (createMainSelected: Boolean)(implicit ld: String) {
    val sb = new StringBuilder

    if(createConstructorsSelected)
      sb.append(unimplemetedConstructors(typeHierarchy, createdType))

    if(createInheritedSelected)
      sb.append(unimplemetedMethods(typeHierarchy, createdType))

    if(createMainSelected)
      sb.append(createMain)

    val idx = buffer.getContents.indexOf(templates.bodyStub)
    buffer.replace(idx, templates.bodyStub.length, " {" + ld + sb + ld + "}")

    // order matters here
    List("extends", "name") foreach { s =>
      lhm.get(s) match {
          case Some(x) => x.writeTo(buffer)
          case _ =>
      }
    }
    writeImports
  }

  def addConstructorArgs(args: Args) {

  val ns = if(args.as.nonEmpty)eval(args) else ""
  val nb = lhm.get("name").get.asInstanceOf[NameBufferSupport]
  lhm.put("name", new NameBufferSupport(nb, ns))

  val e = if(args.as.nonEmpty)eval(ParenList(args.as.map (a => a.n))) else ""
  val eb = lhm.get("extends").get.asInstanceOf[ExtendsBufferSupport]
  lhm.put("extends", new ExtendsBufferSupport(eb, e))
  }

  def createElementDeclaration(name: String, superTypes: List[String],
                               buffer: Buffer)(implicit ld: String) {

    val nameBuffer = new NameBufferSupport(" " +name)
    lhm.put("name", nameBuffer)
    nameBuffer.writeTo(buffer)

    val extendsBuffer = new ExtendsBufferSupport(superTypes)
    lhm.put("extends", extendsBuffer)
    extendsBuffer.writeTo(buffer)
  }

  def createMain(implicit ld: String): String =
    ld + "  def main(args: Array[String]): Unit = {  }" + ld

  def unimplemetedConstructors(typeHierarchy: ITypeHierarchy, newType: IType)
                              (implicit ld: String): String

  def unimplemetedMethods(typeHierarchy: ITypeHierarchy, newType: IType)
                         (implicit ld: String): String
}

object CodeBuilder {

  import BufferSupport._

  def apply(packageName: String, superTypes: List[String], buffer: Buffer): CodeBuilder = {
    val imports = ImportSupport(packageName)
    imports.addImports(superTypes)
    new CodeBuilderImpl(imports, superTypes, buffer)
  }

  private val lhm: HashMap[String, BufferSupport] = HashMap.empty

  private class NameBufferSupport(val name: String, val cons: String)
    extends BufferSupport {

  def this(name: String) {
    this(name, "")
  }

  def this(buffer: NameBufferSupport, cons: String) {
    this(buffer.name, cons)
    offset = buffer.offset
    length = buffer.length
  }

  protected def contents(implicit ld: String) = name + cons
  }

  private class ExtendsBufferSupport(
      val superTypes: List[String], val cons: String)
    extends BufferSupport {

  def this(superTypes: List[String]) {
    this(superTypes, "")
  }

  def this(buffer: ExtendsBufferSupport, cons: String) {
    this(buffer.superTypes, cons)
    offset = buffer.offset
    length = buffer.length
  }

    import templates._

  protected def contents(implicit ld: String) = {
    val t = typeTemplate(superTypes)
    val a = t.split(" ")
    if(a.length > 1)
      a(2) = a(2) + cons
    a.mkString(" ")
  }
  }

  sealed abstract class Part

  case class Type(val n: Name) extends Part
  case class TypeParam(val n: Name, val b: TypeBounds) extends Part
  case class TypeBounds(val lo: Type = NothingBound, val hi: Type = AnyBound,
                      val view: Option[Type] = None) extends Part
  case class TypeParams(val tp: Option[List[TypeParam]]) extends Part
  case class Value(val s: String) extends Part
  case class Mods(val s: Option[String]) extends Part
  case class Name(val s: String) extends Part
  case class Arg(val n: Name, val t: Type) extends Part
  case class Result(val t: Type, val v: Value) extends Part
  case class ParenList(val ps: List[Part]) extends Part
  class ParamNames(val pn: List[Name]) extends ParenList(pn)
  class Args(val as: List[Arg]) extends ParenList(as)
  case class Func(val mods: Mods, val name: Name, val typeParams: TypeParams,
              val args: Args, val result: Result) extends Part
  case class ConsBody(val pn: ParamNames) extends Part
  case class AuxCons(val args: Args, val body: ConsBody) extends Part

  object AnyBound extends Type(Name("Any"))
  object NothingBound extends Type(Name("Nothing"))

  def eval(p: Part): String = p match {
    case Type(n)                => eval(n)
  case TypeParam(n,b)         => eval(n) + eval(b)
  case TypeBounds(l,h,v)      => " >: " + eval(l) + " <: "+ eval(h)
  case TypeParams(o)          => o.map(_.map(t => eval(t))
                              .mkString("[", ", ", "]")).getOrElse("")
  case Value(s)               => s
  case Mods(o)                => o.map(m => m + " ").getOrElse("")
  case Name(s)                => s
  case AuxCons(a,b)           => "def this" + eval(a) + " { " +eval(b)+ " }"
  case ConsBody(pn)           => "this" + eval(pn)
  case ParenList(ps)          => ps.map(eval) mkString("(", ", ", ")")
  case Arg(n,t)               => eval(n) + ": " + eval(t)
  case Result(t,v)            => ": " + eval(t) + " = { " + eval(v) + " }"
  case Func(m,n,t,a,r)        => eval(m) + "def " + eval(n) + eval(t) +
                                 eval(a) + eval(r)
  }

  def toOption[A](in: A)(guard: => Boolean = (in != null)) =
    in match {
      case x if(guard) => Some(x)
      case _ => None
    }

  def resultValue(s: String) = {
    s(0) match {
      case 'V' => ""
      case 'Z' => "false"
      case 'S' => "0"
      case 'I' => "0"
      case 'J' => "0L"
      case 'F' => "0.0f"
      case 'D' => "0.0d"
      case  _  => "null"
    }
  }

  def convertSignature(s: String) = {
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
    toOption(s) {s.length > 0 } map (_ + " ")
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

  private val constructorIMethodOrdering = new math.Ordering[IMethod] {
    def compare(x: IMethod, y: IMethod) = {
      (x.getNumberOfParameters, y.getNumberOfParameters) match {
        case (l, r) if l > r => -1
        case (l, r) if l < r =>  1
        case _ =>  0
      }
    }
  }

  private class CodeBuilderImpl(val imports: ImportSupport,
                                        superTypes: List[String],
                                        val buffer: Buffer)
    extends QualifiedNameSupport
       with BufferSupport
       with CodeBuilder {

    private val allSuperTypes = superTypes map createSuperType
    private val generatedMethods: ListBuffer[String] = ListBuffer()
    private val generatedConstructors: ListBuffer[String] = ListBuffer()

    protected def contents(implicit ld: String) =
      (generatedConstructors map (s => ld+"  " +s+ld)
        ++= generatedMethods map (s => ld+"  " +s+ld)) mkString

    def unimplemetedConstructors(typeHierarchy: ITypeHierarchy, newType: IType)
                                (implicit ld: String) = {

      val astc: ListBuffer[IMethod] = ListBuffer()

      typeHierarchy.getSuperclass(newType).getMethods.foreach { scm =>
        if(scm.isConstructor)
          astc += scm
      }

      val sastc = astc.sorted(constructorIMethodOrdering)
      for {
        stc <- sastc.headOption
         pn =  stc.getParameterNames map (pn => Name(pn))
         pt =  stc.getParameterTypes map (pt => Type(Name(convertAndAdd(pt))))
         nt =  pn zip pt map (nt => Arg(nt._1, nt._2))
         ag =  new Args(nt.toList)
      } addConstructorArgs(ag)

      for {
        stc <- sastc
         pn =  stc.getParameterNames.map (pn => Name(pn)).toList
         pt =  stc.getParameterTypes map (pt => Type(Name(convertAndAdd(pt))))
         nt =  pn zip pt map (nt => Arg(nt._1, nt._2))
         if(nt.nonEmpty)
         ag =  new Args(nt.init.toList)
         pl =  new ParamNames(pn.dropRight(1) :+ Name("null"))
      } generatedConstructors + eval(AuxCons(ag, ConsBody(pl)))

      generatedConstructors.map (s => ld+"  " +s+ld).toList.mkString
    }

    def unimplemetedMethods(typeHierarchy: ITypeHierarchy, newType: IType)
                           (implicit ld: String) = {

      val astm: ListBuffer[IMethod] = ListBuffer()
      val istm: ListBuffer[IMethod] = ListBuffer()
      val istf: ListBuffer[IField] = ListBuffer()
      typeHierarchy getAllSupertypes(newType) foreach { st =>
        istf ++= st.getFields
        st.getMethods.foreach { stm =>
          if(Flags.isAbstract(stm.getFlags) && !similarMethod(stm, astm)
                                            && !similarMethod(stm, istm)
                                            && !overridenByField(stm, istf))
            astm += stm
          else
            istm += stm
        }
      }

      for {
        stm <- astm
         md =  Mods(methodModifiers(stm))
         nm =  Name(NameTransformer.decode(elementName(stm).get))
         tp =  stm.getTypeParameters map (tp => TypeParam(Name(tp.getElementName),TypeBounds()))
         ts =  TypeParams(toOption(tp.toList)(tp.length > 0))
         pn =  stm.getParameterNames map (pn => Name(pn))
         pt =  stm.getParameterTypes map (pt => Type(Name(convertAndAdd(pt))))
         nt =  pn zip pt map (nt => Arg(nt._1, nt._2))
         ag =  new Args(nt.toList)
          r =  Result(Type(Name(convertAndAdd(returnType(stm).get))), Value(returnValue(stm).get))
      } generatedMethods + eval(Func(md,nm,ts,ag,r))

      generatedMethods.map (s => ld+"  " +s+ld).toList.mkString
    }
  }
}

object templates extends QualifiedNameSupport {

  type LineDelimiter = String

  val DEFAULT_SUPER_TYPE = "scala.AnyRef"

  def newLine(implicit ld: LineDelimiter): (String => String) =
    (s: String) => s + ld

  def bodyStub(implicit ld: LineDelimiter) = " {"+ld+ld+"}"

  def newLines(implicit ld: LineDelimiter): (String => String) =
    (s: String) => s + ld + ld

  def packageTemplate(opt: Option[String])(implicit ld: LineDelimiter): String = {
    val g = (s: String) => "package " + s
    val f = newLines compose g
    opt map f getOrElse ""
  }

  def commentTemplate(opt: Option[String])(implicit ld: LineDelimiter): String =
    opt map newLine getOrElse("")

  def importsTemplate(xs: List[String])(implicit ld: LineDelimiter): String = {
    val g = (s: String) => "import " + s
    val f = g compose newLine compose removeParameters
    xs map f reduceLeftOption(_ + _) map newLine getOrElse ""
  }

  def typeTemplate = extendsTemplate compose explicitSuperTypes

  private val explicitSuperTypes = (xs: List[String]) =>
    xs match {
      case List(DEFAULT_SUPER_TYPE, rest @ _*) => rest map removePackage toList
      case List(all @ _*) => all map removePackage toList
    }

  private val extendsTemplate = (xs: List[String]) =>
    xs match {
      case l: List[_] if(l.nonEmpty) => " extends " + l.mkString(" with ")
      case _ => ""
    }
}
/*
 * Copyright 2010 LAMP/EPFL
 * 
 * @author Tim Clendenen
 * 
 */
package scala.tools.eclipse.wizards

import scala.collection.mutable.HashMap

trait CodeBuilder {
	
  val imports: ImportSupport
	
  import CodeBuilder._

  def addConstructorArgs(args: Args) {
	
	val ns = eval(args)
	val nb = lhm.get("name").get.asInstanceOf[NameBufferSupport]
	lhm.put("name", new NameBufferSupport(nb, ns))
	    
	val e = eval(ParenList(args.as.map (a => a.n)))
	val eb = lhm.get("extends").get.asInstanceOf[ExtendsBufferSupport]
	val eb2 = new ExtendsBufferSupport(eb, e)
	eb2.offset_= (eb2.offset + ns.length)
	lhm.put("extends", eb2)
  }

  protected val convertAndAdd = (s: String) => {
    imports.addImport(convertSignature(s))
  }
}

object CodeBuilder {
	
  import BufferSupport._
  
  def createClassDeclaration(name: String, superTypes: List[String], buffer: Buffer)
                            (implicit ld: String) {
	  
    val nameBuffer = new NameBufferSupport(" " +name)
    lhm.put("name", nameBuffer)
    nameBuffer.writeTo(buffer)
    
    val extendsBuffer = new ExtendsBufferSupport(superTypes)
	lhm.put("extends", extendsBuffer)
    extendsBuffer.writeTo(buffer)
  }
  
  val lhm: HashMap[String, BufferSupport] = HashMap.empty
  
  class NameBufferSupport(val name: String, val cons: String) extends BufferSupport {
	  
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
  
  class ExtendsBufferSupport(val superTypes: List[String], val cons: String) 
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
  case class ParamNames(pn: List[Name]) extends ParenList(pn)
  case class Args(as: List[Arg]) extends ParenList(as)
  case class Func(val mods: Mods, val name: Name, val typeParams: TypeParams,
		          val args: Args, val result: Result) extends Part
  case class ConsBody(val pn: ParamNames) extends Part
  case class AuxCons(val args: Args, val body: ConsBody) extends Part
  
  case object AnyBound extends Type(Name("Any"))
  case object NothingBound extends Type(Name("Nothing"))
  
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
  
  def toOption[A](in: A, guard: => Boolean = true) = 
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
}

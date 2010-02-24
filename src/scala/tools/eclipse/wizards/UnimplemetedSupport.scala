/*
 * Copyright 2010 LAMP/EPFL
 * 
 * @author Tim Clendenen
 * 
 */
package scala.tools.eclipse.wizards

import org.eclipse.jdt.core.{ Flags, IMethod, IType, ITypeHierarchy, Signature }

import scala.reflect.NameTransformer
import scala.collection.mutable.ListBuffer
	
trait UnimplemetedSupport extends QualifiedNameSupport with BufferSupport {
  def unimplemetedConstructors(typeHierarchy: ITypeHierarchy, 
		  newType: IType): Unit
  def unimplemetedMethods(typeHierarchy: ITypeHierarchy, newType: IType): Unit
}

object UnimplemetedSupport {
	
  def apply(imports: ImportSupport, superTypes: List[String]): UnimplemetedSupport = {
    new UnimplemetedSupportImpl(imports, superTypes)
  } 
  
  private def toOption[A](in: A, guard: A => Boolean = {a:A => true}) = 
    in match {
      case x if(guard(x)) => Some(x)
      case _ => None
    }
      
  private val modifiers = (k: IMethod) => 
    toOption(Flags.toString(k.getFlags & ~Flags.AccPublic & ~Flags.AccAbstract),
      {s:String => s.length > 0}) map (_ + " ")
  
  private val elementName = (k: IMethod) => toOption(k.getElementName) 
  
  private val returnType = (k: IMethod) => toOption(k.getReturnType)
  
  private val returnValue = (k: IMethod) => {
    toOption(k.getReturnType()(0) match {
      case 'V' => ""
      case 'Z' => "false"
      case 'S' => "0"
      case 'I' => "0"
      case 'J' => "0"
      case 'F' => "0.0"
      case 'D' => "0.0"
      case  _  => "null"
    })
  }
  
  private def convertSignature(s: String) = {
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
  
  type Modifiers = String
  type Name = String
  type ParameterName = String
  type ParameterType = String
  type ReturnType = Option[String]
  type ReturnValue = Option[String]

  case class MethodMods(val im: IMethod, mods: Option[Modifiers]) {
	def this(im: IMethod) {
	  this(im, modifiers(im))
	}
	val opt = mods
	override def toString() = mods.getOrElse("")
  }
  
  case class MethodName(val im: IMethod, name: Option[Name]) {
	def this(im: IMethod) {
	  this(im, elementName(im))
	}
	override def toString() = NameTransformer.decode(name.get)
  }
  
  case class Parameters(val im: IMethod, xs: List[(ParameterName, ParameterType)]) {
	def this(im: IMethod) {
	  this(im, im.getParameterNames.zip(im.getParameterTypes).toList)
	}
    val list = xs
    override def toString() = 
    	list.map(nt => nt._1 + ": " + nt._2).mkString("(",", ",")")
  }
  
  case class ReturnInfo(val im: IMethod, val rt: ReturnType, val rv: ReturnValue) {
	def this(im: IMethod) {
	  this(im, returnType(im), returnValue(im))
	}
	val opt = rt.zip(rv).headOption
	override def toString() = opt match {
	  case s:Some[(String, String)] => 
		s map(tv => ": " + tv._1 + " = { " + tv._2 + " }") get
	  case None => " {  }"
	}
  }
  
  trait UnimplementedMember {
    val d: MethodMods
    val n: MethodName
	val p: Parameters
	val r: ReturnInfo
  }
  
  case class Constructor(val im: IMethod) extends UnimplementedMember {
    val d = new MethodMods(im) 
    val n = new MethodName(im, toOption("this"))
	val p = new Parameters(im)
	val r = new ReturnInfo(im, None, None)
  }
  
  case class Method(im: IMethod) extends UnimplementedMember {
    val d = new MethodMods(im) 
    val n = new MethodName(im)
	val p = new Parameters(im)
	val r = new ReturnInfo(im)
  }
  
  private def similarMethod(ma: IMethod, xs: ListBuffer[IMethod]): Boolean = {
    def similar(m: IMethod) = {
      ma.getElementName == m.getElementName &&
      ma.getNumberOfParameters == m.getNumberOfParameters &&
      (ma.getParameterTypes.mkString == m.getParameterTypes.mkString ||
       ma.getParameterNames.mkString == m.getParameterNames.mkString)
    }

    xs exists similar
  }
  
  private val constructorOrdering = new math.Ordering[Constructor] {
    def compare(x: Constructor, y: Constructor) = {
      (x.p.list.size, y.p.list.size) match {
        case (l, r) if l > r => -1
        case (l, r) if l < r =>  1
        case _ =>  0
      }
    }
  }

  private class UnimplemetedSupportImpl(imports: ImportSupport, 
		                                superTypes: List[String]) 
  	extends UnimplemetedSupport {
	
	val allSuperTypes = superTypes map createSuperType
	
	val generatedMembers: ListBuffer[String] = ListBuffer()
	
	protected def contents(implicit ld: String) =
	  generatedMembers.map(s => ld+s+ld).toList.mkString
	  
    private def convertAndAdd(s: String) = {
      imports.addImport(convertSignature(s))
    }

	def unimplemetedConstructors(typeHierarchy: ITypeHierarchy, newType: IType) {
		
	  val actm: ListBuffer[IMethod] = ListBuffer()
	  
	  typeHierarchy.getSuperclass(newType).getMethods.foreach { scm =>
	    if(scm.isConstructor)
	      actm += scm
	  }
	   
	  val l = actm.map(c => Constructor(c)).sorted(constructorOrdering)
	  
	  if(l.size > 0) {  
	 	import CodeBuilder._
	 	
	    val c = l(0)
	 	
	 	val n = c.p.list.map(nt => nt._1 + ": " + convertAndAdd(nt._2))
	    val nb = lhm.get("name").get.asInstanceOf[NameBuffer]
	 	val ns = n.mkString("(",", ",")")
	 	lhm.put("name", new NameBuffer(nb, ns))
	 	
	 	val e = c.p.list.map (m => m._1).mkString("(",", ",")")
	    val eb = lhm.get("extends").get.asInstanceOf[ExtendsBuffer]
	 	val eb2 = new ExtendsBuffer(eb, e)
	 	eb2.offset_= (eb2.offset + ns.length)
	    lhm.put("extends", eb2)
	   
	    generateMembers(l.tail.toList).map { cBody =>
	 	  val idxs = cBody.indexOf("{")+2
	 	  val sb = new StringBuilder(cBody)
	 	  val m = l.remove(0).im
	 	  if(m.getNumberOfParameters > 1) {
	 	    val s = m.getParameterNames.init :+ "null"
	 	    sb.insert(idxs, "this"+s.mkString("(", ", ", ")")).toString
	 	  } 
	 	  else if(m.getNumberOfParameters == 1) 
	 	    sb.insert(idxs, "this(null)").toString
	 	  else 
	 	    "" 
	    } ++=: generatedMembers
	  }
	}

	def unimplemetedMethods(typeHierarchy: ITypeHierarchy, newType: IType) {
	   
	  def superTypeMethods(theType: IType): List[Method] = {
	    val astm: ListBuffer[IMethod] = ListBuffer()
	    val istm: ListBuffer[IMethod] = ListBuffer()
	    typeHierarchy getAllSupertypes(newType) foreach { st =>
	      st.getMethods.foreach { stm =>
	        if(Flags.isAbstract(stm.getFlags) && !similarMethod(stm, astm)
	        		                          && !similarMethod(stm, istm))
	          astm += stm
	        else 
	          istm += stm
	      }
	    }
	    astm map (m => Method(m)) toList
	  }
	  
	  generatedMembers ++= generateMembers(superTypeMethods(newType))
	}
	
	def generateMembers(methods: List[UnimplementedMember]): List[String] = {
	   
	  for {
	     m <- methods
	    ds =  "  " + m.d
	    ns =  "def " + m.n
	    ps =  Parameters( m.p.im, m.p.list.map(nt => 
	                                          (nt._1, convertAndAdd(nt._2))))
	     r =  m.r.rt map convertAndAdd
        rs =  ReturnInfo( m.r.im, r, m.r.rv)
	  } yield ds + ns + ps + rs
	}
  }
}

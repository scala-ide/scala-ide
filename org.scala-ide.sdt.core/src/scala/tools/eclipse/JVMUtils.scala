/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import scala.reflect.NameTransformer
import scala.tools.nsc.Global
import ch.epfl.lamp.fjbg.{ JArrayType, JMethodType, JObjectType, JType }

trait JVMUtils { self : Global =>

  private lazy val jvmUtil =
    new genJVM.BytecodeUtil {}

  def javaName(sym : Symbol) : String = jvmUtil.javaName(sym)

  def javaNames(syms : List[Symbol]) : Array[String] = syms.toArray map (s => jvmUtil.javaName(s))

  def javaFlags(sym : Symbol) : Int = genJVM.javaFlags(sym)

  def javaType(t: Type): JType = t.normalize match {
    case ErrorType | NoType => JType.UNKNOWN

    case m : MethodType =>
      val t = m.finalResultType
      new JMethodType(javaType(t), m.paramss.flatMap(_.map(javaType)).toArray)

    case p : PolyType =>
      val t = p.finalResultType
      javaType(t)

    case r : RefinedType =>
      JObjectType.JAVA_LANG_OBJECT
      //javaType(r.typeSymbol.tpe)

    case _ => jvmUtil.javaType(t)
  }

  // Provides JType where all special symbols are substituted.
  def encodedJavaType(t : Type) = {
	def encode(jt : JType) : JType = jt match {
	  case clazz : JObjectType => {
	    val name = clazz.getName.split("/").map{NameTransformer encode _}.mkString("/")
	    new JObjectType(name)
	  }
	  case arr : JArrayType => {
		  val etype = encode(arr.getElementType)
	 	  if (JType.UNKNOWN == etype) JType.UNKNOWN else new JArrayType(etype)
	  }
	  case m : JMethodType => {
	 	  val ret = encode(m.getReturnType)
	 	  val argTypes = m.getArgumentTypes.map( encode(_) )
	 	  if (ret == JType.UNKNOWN || argTypes.exists(_ == JType.UNKNOWN)) JType.UNKNOWN else new JMethodType(ret, argTypes)
	  }
	  case _ => jt
    }
	encode(javaType(t))
  }

  def javaType(s: Symbol): JType =
    if (s.isMethod)
      new JMethodType(
        if (s.isClassConstructor) JType.VOID else javaType(s.tpe.resultType),
        s.tpe.paramTypes.map(javaType).toArray)
    else
      javaType(s.tpe)
}

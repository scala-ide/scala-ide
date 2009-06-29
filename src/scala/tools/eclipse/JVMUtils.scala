/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import scala.collection.immutable.HashMap

import scala.tools.nsc.Global 
import scala.tools.nsc.symtab.Flags
import ch.epfl.lamp.fjbg.{ JAccessFlags, JArrayType, JMethodType, JObjectType, JType }

trait JVMUtils { self : Global =>
  def javaName(sym: Symbol): String = {
    val suffix = if (sym.hasFlag(Flags.MODULE) && !sym.isMethod &&
                      !sym.isImplClass && 
                      !sym.hasFlag(Flags.JAVA)) "$" else "";

    if (sym == definitions.NothingClass)
      return javaName(definitions.RuntimeNothingClass)
    else if (sym == definitions.NullClass)
      return javaName(definitions.RuntimeNullClass)

    (if (sym.isClass || (sym.isModule && !sym.isMethod))
      sym.fullNameString('/')
    else
      sym.simpleName.toString.trim()) + suffix
  }

  def javaNames(syms: List[Symbol]): Array[String] = {
    val res = new Array[String](syms.length)
    var i = 0
    syms foreach (s => { res(i) = javaName(s); i += 1 })
    res
  }

  def javaFlags(sym: Symbol): Int = {
    import JAccessFlags._

    var jf: Int = 0
    val f = sym.flags
    jf = jf | (if (sym hasFlag Flags.SYNTHETIC) ACC_SYNTHETIC else 0)
/*      jf = jf | (if (sym hasFlag Flags.PRIVATE) ACC_PRIVATE else 
                if (sym hasFlag Flags.PROTECTED) ACC_PROTECTED else ACC_PUBLIC)
*/
    jf = jf | (if (sym hasFlag Flags.PRIVATE) ACC_PRIVATE else  ACC_PUBLIC)
    jf = jf | (if ((sym hasFlag Flags.ABSTRACT) ||
                   (sym hasFlag Flags.DEFERRED)) ACC_ABSTRACT else 0)
    jf = jf | (if (sym hasFlag Flags.INTERFACE) ACC_INTERFACE else 0)
    jf = jf | (if ((sym hasFlag Flags.FINAL)
                     && !sym.enclClass.hasFlag(Flags.INTERFACE) 
                     && !sym.isClassConstructor) ACC_FINAL else 0)
    jf = jf | (if (sym.isStaticMember) ACC_STATIC else 0)
    jf = jf | (if (sym hasFlag Flags.BRIDGE) ACC_BRIDGE | ACC_SYNTHETIC else 0)

    if (sym.isClass && !sym.hasFlag(Flags.INTERFACE))
      jf = jf | ACC_SUPER

    // constructors of module classes should be private
    if (sym.isPrimaryConstructor && isTopLevelModule(sym.owner)) {
      jf |= ACC_PRIVATE
      jf &= ~ACC_PUBLIC
    }
    jf
  }

  def javaType(t: Type): JType = { new Breakpoint ; t match {
    case ThisType(sym) => 
      if (sym == definitions.ArrayClass)
        new JObjectType(javaName(definitions.ObjectClass))
      else
        new JObjectType(javaName(sym))

    case SingleType(pre, sym) => 
      println(sym.name+" "+nme.Int+" "+(sym.name == nme.Int))
      primitiveTypeMap get sym.name.toString match {
        case Some(k) => k
        case None    => new JObjectType(javaName(sym))
      }

    case ConstantType(value) =>
      javaType(t.underlying)

    case TypeRef(_, sym, args) =>
      println(sym.name+" "+nme.Int+" "+(sym.name == nme.Int))
      primitiveTypeMap get sym.name.toString match {
        case Some(k) => k
        case None    => arrayOrClassType(sym, args)
      }

    case ClassInfoType(_, _, sym) =>
      println(sym.name+" "+nme.Int+" "+(sym.name == nme.Int))
      primitiveTypeMap get sym.name.toString match {
        case Some(k) => k
        case None    =>
          if (sym == definitions.ArrayClass)
            abort("ClassInfoType to ArrayClass!")
          else
            new JObjectType(javaName(sym))
      }

    case ExistentialType(tparams, t) =>
      javaType(t)
      
    //case WildcardType => // bq: useful hack when wildcard types come here
    //  REFERENCE(definitions.ObjectClass)

    case _ =>
      abort("Unknown type: " + t + ", " + t.normalize + "[" + t.getClass + ", " + t.normalize.getClass + "]" + 
      " TypeRef? " + t.isInstanceOf[TypeRef] + ", " + t.normalize.isInstanceOf[TypeRef]) 
  }
  }
  
  private def arrayOrClassType(sym: Symbol, targs: List[Type]): JType = {
    if (sym == definitions.ArrayClass)
      new JArrayType(javaType(targs.head))
    else if (sym.isClass)
      new JObjectType(javaName(sym))
    else {
      assert(sym.isType, sym) // it must be compiling Array[a]
      new JObjectType(javaName(definitions.ObjectClass))
    }
  }
  
  private val primitiveTypeMap = HashMap(
    (nme.Unit.toString -> JType.VOID),
    (nme.Boolean.toString -> JType.BOOLEAN),
    (nme.Byte.toString -> JType.BYTE),
    (nme.Short.toString -> JType.SHORT),
    (nme.Char.toString -> JType.CHAR),
    (nme.Int.toString -> JType.INT),
    (nme.Long.toString -> JType.LONG),
    (nme.Float.toString -> JType.FLOAT),
    (nme.Double.toString -> JType.DOUBLE)
  )
  
  def javaType(s: Symbol): JType =
    if (s.isMethod)
      new JMethodType(
        if (s.isClassConstructor) JType.VOID else javaType(s.tpe.resultType),
        s.tpe.params.map(javaType).toArray)
    else
      javaType(s.tpe)

  def isTopLevelModule(sym: Symbol): Boolean =
    atPhase (currentRun.picklerPhase.next) {
      sym.isModuleClass && !sym.isImplClass && !sym.isNestedClass
    }
}

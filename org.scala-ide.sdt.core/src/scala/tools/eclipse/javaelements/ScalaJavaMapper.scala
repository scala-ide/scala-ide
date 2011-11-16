/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.javaelements


import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants
import scala.tools.nsc.symtab.Flags
import scala.tools.eclipse.ScalaPresentationCompiler
import ch.epfl.lamp.fjbg.{ JObjectType, JType }
import scala.tools.eclipse.util.HasLogger

trait ScalaJavaMapper extends ScalaAnnotationHelper with HasLogger { self : ScalaPresentationCompiler => 

  def mapType(t : Tree) : String = {
    (t match {
      case tt : TypeTree => {
        if(tt.symbol == null || tt.symbol == NoSymbol || tt.symbol.isRefinementClass || tt.symbol.owner.isRefinementClass)
          "scala.AnyRef"
        else
          tt.symbol.fullName
      }
      case Select(_, name) => name.toString
      case Ident(name) => name.toString
      case _ => "scala.AnyRef"
    }) match {
      case "scala.AnyRef" => "java.lang.Object"
      case "scala.Unit" => "void"
      case "scala.Boolean" => "boolean"
      case "scala.Byte" => "byte"
      case "scala.Short" => "short"
      case "scala.Int" => "int"
      case "scala.Long" => "long"
      case "scala.Float" => "float"
      case "scala.Double" => "double"
      case "<NoSymbol>" => "void"
      case n => n
    }
  }

  /** Compatible with both 2.8 and 2.9 (interface HasFlags appears in 2.9).
   * 
   *  COMPAT: Once we drop 2.8, rewrite to use the HasFlags trait in scala.reflect.generic
   */
  
  
/* Re-add when ticket #4560 is fixed.
  type HasFlags = {
      /** Whether this entity has ANY of the flags in the given mask. */
      def hasFlag(flag: Long): Boolean
      def isFinal: Boolean
      def isTrait: Boolean
  }
*/  
  
  def mapModifiers(owner: Symbol) : Int = {
    var jdtMods = 0
    if(owner.hasFlag(Flags.PRIVATE))
      jdtMods = jdtMods | ClassFileConstants.AccPrivate
    else
      // protected entities need to be exposed as public to match scala compiler's behavior.
      jdtMods = jdtMods | ClassFileConstants.AccPublic
    
    if(owner.hasFlag(Flags.ABSTRACT) || owner.hasFlag(Flags.DEFERRED))
      jdtMods = jdtMods | ClassFileConstants.AccAbstract

    if(owner.isFinal || owner.hasFlag(Flags.MODULE))
      jdtMods = jdtMods | ClassFileConstants.AccFinal
    
    if(owner.isTrait)
      jdtMods = jdtMods | ClassFileConstants.AccInterface
    
    /** Handle Scala's annotations that have to be mapped into Java modifiers */
    def mapScalaAnnotationsIntoJavaModifiers(): Int = {
      var mod = 0
      if(hasTransientAnn(owner)) {
        mod = mod | ClassFileConstants.AccTransient
      }
      
      if(hasVolatileAnn(owner)) {
        mod = mod | ClassFileConstants.AccVolatile
      }
      
      if(hasNativeAnn(owner)) {
        mod = mod | ClassFileConstants.AccNative
      }
      
      if(hasStrictFPAnn(owner)) {
        mod = mod | ClassFileConstants.AccStrictfp
      }
      
      if(hasDeprecatedAnn(owner)) {
        mod = mod | ClassFileConstants.AccDeprecated
      }
      
      mod
    }
      
    jdtMods | mapScalaAnnotationsIntoJavaModifiers()
  }

  /** Overload that needs to go away when 'HasFlag' can be used, either as a
   *  structural type -- see #4560, or by sticking to 2.9.0 that has this trait
   */
  def mapModifiers(owner: Modifiers) : Int = {
    var jdtMods = 0
    if(owner.hasFlag(Flags.PRIVATE))
      jdtMods = jdtMods | ClassFileConstants.AccPrivate
    else
      // protected entities need to be exposed as public to match scala compiler's behavior.
      jdtMods = jdtMods | ClassFileConstants.AccPublic
    
    if(owner.hasFlag(Flags.ABSTRACT) || owner.hasFlag(Flags.DEFERRED))
      jdtMods = jdtMods | ClassFileConstants.AccAbstract

    if(owner.isFinal || owner.hasFlag(Flags.MODULE))
      jdtMods = jdtMods | ClassFileConstants.AccFinal
    
    if(owner.isTrait)
      jdtMods = jdtMods | ClassFileConstants.AccInterface
    
    jdtMods
  }

  
  def mapType(s : Symbol) : String = {
    (if(s == null || s == NoSymbol || s.isRefinementClass || s.owner.isRefinementClass)
        "scala.AnyRef"
      else
        s.fullName
    ) match {
      case "scala.AnyRef" | "scala.Any" => "java.lang.Object"
      case "scala.Unit" => "void"
      case "scala.Boolean" => "boolean"
      case "scala.Byte" => "byte"
      case "scala.Short" => "short"
      case "scala.Int" => "int"
      case "scala.Long" => "long"
      case "scala.Float" => "float"
      case "scala.Double" => "double"
      case "<NoSymbol>" => "void"
      case n => n
    }
  }
  
  /** 
   * Map a Scala `Type` that '''does not take type parameters''' into its
   * Java representation. 
   * A special case exists for Scala `Array` since in Java array do not take 
   * type parameters.
   * */
  def mapType(tpe: Type): String = {
	val base = mapType(tpe.typeSymbol)
	tpe.typeSymbol match {
    // only the Array class has type parameters. the Array object is non-parametric
	  case definitions.ArrayClass => 
	    val paramTypes = tpe.typeArgs.map(mapType(_))
	    assert(paramTypes.size == 1, "Expected exactly one type parameter, found %d [%s]".format(paramTypes.size, tpe))
        paramTypes.head + "[]"
	  case _ => 
	    if(tpe.typeParams.nonEmpty) 
	      logger.debug("mapType(Type) is not expected to be used with a type that has type parameters. (passed type was %s)".format(tpe))
	    base
	}
  }
  
  
  def mapParamTypePackageName(t : Type) : String = {
    if (t.typeSymbolDirect.isTypeParameter)
      ""
    else {
      val jt = javaType(t)
      if (jt.isValueType)
        ""
      else
        t.typeSymbol.enclosingPackage.fullName
    }
  }

  def isScalaSpecialType(t : Type) = {
    import definitions._
    t.typeSymbol match {
      case AnyClass | AnyRefClass | AnyValClass | NothingClass | NullClass => true
      case _ => false
    }
  }
  
  def mapParamTypeName(t : Type) : String = {
    if (t.typeSymbolDirect.isTypeParameter)
      t.typeSymbolDirect.name.toString
    else if (isScalaSpecialType(t))
      "java.lang.Object"
    else {
      val jt = javaType(t)
      if (jt.isValueType)
        jt.toString
      else
        mapTypeName(t.typeSymbol)
    }
  }
  
  def mapParamTypeSignature(t : Type) : String = {
    if (t.typeSymbolDirect.isTypeParameter)
      "T"+t.typeSymbolDirect.name.toString+";"
    else if (isScalaSpecialType(t))
      "Ljava.lang.Object;"
    else {
      val jt = javaType(t)
      val fjt = if (jt == JType.UNKNOWN)
        JObjectType.JAVA_LANG_OBJECT
      else
        jt
      fjt.getSignature.replace('/', '.')
    }
  }
  
  def mapTypeName(s : Symbol) : String =
    if (s == NoSymbol || s.hasFlag(Flags.PACKAGE)) ""
    else {
      val owner = s.owner
      val prefix = if (owner != NoSymbol && !owner.hasFlag(Flags.PACKAGE)) mapTypeName(s.owner)+"." else ""
      val suffix = if (s.hasFlag(Flags.MODULE) && !s.hasFlag(Flags.JAVA)) "$" else ""
      prefix+s.nameString+suffix
    }

  def enclosingTypeNames(sym : Symbol): List[String] = {
    def enclosing(sym : Symbol) : List[String] =
      if (sym == NoSymbol || sym.owner.hasFlag(Flags.PACKAGE))
        Nil
      else {
        val owner = sym.owner 
        val name0 = owner.simpleName.toString
        val name = if (owner.isModuleClass) name0+"$" else name0
        name :: enclosing(owner)
      }
        
    enclosing(sym).reverse
  }
  
  /** Return the enclosing package. Correctly handle the empty package, by returning
   *  the empty string, instead of <empty>. */
  def enclosingPackage(sym: Symbol): String = {
    val enclPackage = sym.enclosingPackage
    if (enclPackage == definitions.EmptyPackage || enclPackage == definitions.RootPackage)
      ""
    else
      enclPackage.fullName
  }
}

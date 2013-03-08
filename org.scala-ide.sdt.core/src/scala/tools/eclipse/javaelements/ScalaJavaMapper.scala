/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.javaelements


import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants
import scala.tools.nsc.symtab.Flags
import scala.tools.eclipse.ScalaPresentationCompiler
import scala.tools.eclipse.logging.HasLogger
import org.eclipse.jdt.core._
import org.eclipse.jdt.internal.core.JavaModelManager
import org.eclipse.core.runtime.Path

trait ScalaJavaMapper extends ScalaAnnotationHelper with SymbolNameUtil with HasLogger { self : ScalaPresentationCompiler =>

  private[eclipse] def initializeRequiredSymbols() {
    import definitions._
    Set(UnitClass,
      BooleanClass,
      ByteClass,
      ShortClass,
      IntClass,
      LongClass,
      FloatClass,
      DoubleClass).foreach(_.initialize)
  }

  /** Return the Java Element corresponding to the given Scala Symbol, looking in the
   *  given project list
   * 
   *  If the symbol exists in several projects, it returns one of them.
   */
  def getJavaElement(sym: Symbol, projects: IJavaProject*): Option[IJavaElement] = {
    assert(sym ne null)
    if (sym == NoSymbol) return None
    
    def matchesMethod(meth: IMethod): Boolean = {
      import Signature._
      askOption { () =>
        lazy val methName = meth.getElementName
        lazy val symName = (if(sym.isConstructor) sym.owner.simpleName.toString + (if (sym.owner.isModuleClass) "$" else "") else sym.name.toString)
        lazy val sameName = methName == symName
        lazy val methParamsTpe = meth.getParameterTypes.map(tp => getTypeErasure(getElementType(tp)))
        lazy val symParamsTpe = sym.paramss.flatten.map(param => mapParamTypeSignature(param.tpe))
        lazy val sameParams = methParamsTpe.sameElements(symParamsTpe)
        sameName && sameParams
      }.getOrElse(false)
    }

    if (sym.isPackage) {
      val fullName = sym.fullName
      val results = projects.map(p => Option(p.findElement(new Path(fullName.replace('.', '/')))))
      results.flatten.headOption
    } else if (sym.isClass || sym.isModule) {
      val fullClassName = mapType(sym)
      val results = projects.map(p => Option(p.findType(fullClassName)))
      results.find(_.isDefined).flatten.headOption
    } else getJavaElement(sym.owner, projects: _*) match {
        case Some(ownerClass: IType) =>
          def isGetterOrSetter: Boolean = sym.isGetter || sym.isSetter
          def isConcreteGetterOrSetter: Boolean = isGetterOrSetter && !sym.isDeferred
          if (sym.isMethod && !isConcreteGetterOrSetter) ownerClass.getMethods.find(matchesMethod)
          else {
            val fieldName = 
              if(self.nme.isLocalName(sym.name)) self.nme.localToGetter(sym.name.toTermName)
              else sym.name

            ownerClass.getFields.find(_.getElementName == fieldName.toString)
          }
        case _ => None
    }
  }
  
  /** Return the Java Element corresponding to the given Scala Symbol, looking in the
   *  all existing Java projects.
   *  
   *  If the symbol exists in several projects, it returns one of them.
   */
  def getJavaElement(sym: Symbol): Option[IJavaElement] = {
    val javaModel = JavaModelManager.getJavaModelManager.getJavaModel
    getJavaElement(sym, javaModel.getJavaProjects(): _*)
  }

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

  /** Returns the fully-qualified name for the passed symbol (it expects the symbol to be a type).*/
  def mapType(s: Symbol): String = mapType(s, javaClassName(_))

  /** Returns the simple name for the passed symbol (it expects the symbol to be a type).*/ 
  def mapSimpleType(s: Symbol): String = mapType(s, javaSimpleName(_))

  private def mapType(symbolType: Symbol, symbolType2StringName: Symbol => String): String = {
    val normalizedSymbol =
      if (symbolType == null || symbolType == NoSymbol || symbolType.isRefinementClass || symbolType.owner.isRefinementClass ||
        symbolType == definitions.AnyRefClass || symbolType == definitions.AnyClass)
        definitions.ObjectClass
      else symbolType

    normalizedSymbol match {
      case definitions.UnitClass    => "void"
      case definitions.BooleanClass => "boolean"
      case definitions.ByteClass    => "byte"
      case definitions.ShortClass   => "short"
      case definitions.IntClass     => "int"
      case definitions.LongClass    => "long"
      case definitions.FloatClass   => "float"
      case definitions.DoubleClass  => "double"
      case n                        => symbolType2StringName(n)
    }
  }

  /** Map a Scala `Type` that '''does not take type parameters''' into its
   *  Java representation.
   *  A special case exists for Scala `Array` since in Java arrays do not take
   *  type parameters.
   */
  def mapType(tpe: Type): String = {
    val base = mapType(tpe.typeSymbol)
    tpe.typeSymbol match {
      // only the Array class has type parameters. the Array object is non-parametric
      case definitions.ArrayClass =>
        val paramTypes = tpe.normalize.typeArgs.map(mapType(_)) // normalize is needed when you have `type BitSet = Array[Int]`
        assert(paramTypes.size == 1, "Expected exactly one type parameter, found %d [%s]".format(paramTypes.size, tpe))
        paramTypes.head + "[]"
      case _ =>
        if (tpe.typeParams.nonEmpty)
          logger.debug("mapType(Type) is not expected to be used with a type that has type parameters. (passed type was %s)".format(tpe))
        base
    }
  }
  
  
  def mapParamTypePackageName(t : Type) : String = {
    if (t.typeSymbolDirect.isTypeParameter)
      ""
    else {
      if (definitions.isPrimitiveValueType(t))
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
      if (definitions.isPrimitiveValueType(t))
        t.toString
      else
        mapTypeName(t.typeSymbol)
    }
  }
  
  def mapParamTypeSignature(t : Type) : String = {
    val objectSig = "Ljava.lang.Object;"
    if (t.typeSymbolDirect.isTypeParameter)
      "T"+t.typeSymbolDirect.name.toString+";"
    else if (isScalaSpecialType(t) || t.isErroneous)
      objectSig
    else
      javaType(t).getDescriptor().replace('/', '.')
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

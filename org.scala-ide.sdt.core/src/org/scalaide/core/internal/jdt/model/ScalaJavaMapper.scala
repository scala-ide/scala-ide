package org.scalaide.core.internal.jdt.model

import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants
import scala.tools.nsc.symtab.Flags
import org.scalaide.logging.HasLogger
import org.eclipse.jdt.core._
import org.eclipse.jdt.internal.core.JavaModelManager
import org.eclipse.core.runtime.Path
import org.scalaide.core.compiler.IScalaPresentationCompiler.Implicits._
import org.scalaide.core.compiler.InternalServices

/** Implementation of a internal compiler services dealing with mapping Scala types and symbols
 *  to internal JDT counterparts.
 */
trait ScalaJavaMapper extends InternalServices with ScalaAnnotationHelper with HasLogger {

  /** Return the Java Element corresponding to the given Scala Symbol, looking in the
   *  given project list
   *
   *  If the symbol exists in several projects, it returns one of them.
   */
  def getJavaElement(sym: Symbol, _projects: IJavaProject*): Option[IJavaElement] = {
    assert(sym ne null)
    if (sym == NoSymbol) return None

    val projects: Seq[IJavaProject] = if (_projects.isEmpty) JavaModelManager.getJavaModelManager.getJavaModel.getJavaProjects.toSeq else _projects

    // this can be computed only once, to minimize the number of askOption calls
    val (symName, symParamsTpe) = asyncExec {
      val symName = if (sym.isConstructor)
        sym.owner.simpleName.toString + (if (sym.owner.isModuleClass) "$" else "")
      else sym.name.toString

      val symParamsTpe = sym.paramss.flatten.map(param => mapParamTypeSignature(param.tpe))
      (symName, symParamsTpe)
    }.getOrElse(("", Nil))()

    def matchesMethod(meth: IMethod): Boolean = {
      import Signature._
      val sameName = meth.getElementName == symName
      sameName && {
        val methParamsTpe = meth.getParameterTypes.map(tp => getTypeErasure(getElementType(tp)))
        methParamsTpe.sameElements(symParamsTpe)
      }
    }

    if (sym.hasPackageFlag) {
      val fullName = sym.fullName
      val results = projects.map(p => Option(p.findElement(new Path(fullName.replace('.', '/')))))
      results.flatten.headOption
    } else if (sym.isClass || sym.isModule) {
      val fullClassName = javaTypeName(sym)
      val results = projects.map(p => Option(p.findType(fullClassName)))
      results.find(_.isDefined).flatten.headOption
    } else getJavaElement(sym.owner, projects: _*) match {
        case Some(ownerClass: IType) =>
          def isGetterOrSetter: Boolean = sym.isGetter || sym.isSetter
          def isConcreteGetterOrSetter: Boolean = isGetterOrSetter && !sym.isDeferred
          if (sym.isMethod && !isConcreteGetterOrSetter) ownerClass.getMethods.find(matchesMethod)
          else {
            val fieldName =
              if(nme.isLocalName(sym.name)) sym.name.dropLocal
              else sym.name

            ownerClass.getFields.find(_.getElementName == fieldName.toString)
          }
        case _ => None
    }
  }

  override def mapModifiers(sym: Symbol): Int = {
    var jdtMods = 0
    if(sym.hasFlag(Flags.PRIVATE))
      jdtMods = jdtMods | ClassFileConstants.AccPrivate
    else
      // protected entities need to be exposed as public to match scala compiler's behavior.
      jdtMods = jdtMods | ClassFileConstants.AccPublic

    if(sym.hasFlag(Flags.ABSTRACT) || sym.hasFlag(Flags.DEFERRED))
      jdtMods = jdtMods | ClassFileConstants.AccAbstract

    if(sym.isFinal || sym.hasFlag(Flags.MODULE))
      jdtMods = jdtMods | ClassFileConstants.AccFinal

    if(sym.isTrait)
      jdtMods = jdtMods | ClassFileConstants.AccInterface

    /** Handle Scala's annotations that have to be mapped into Java modifiers */
    def mapScalaAnnotationsIntoJavaModifiers(): Int = {
      var mod = 0
      if(hasTransientAnn(sym)) {
        mod = mod | ClassFileConstants.AccTransient
      }

      if(hasVolatileAnn(sym)) {
        mod = mod | ClassFileConstants.AccVolatile
      }

      if(hasNativeAnn(sym)) {
        mod = mod | ClassFileConstants.AccNative
      }

      if(hasStrictFPAnn(sym)) {
        mod = mod | ClassFileConstants.AccStrictfp
      }

      if(hasDeprecatedAnn(sym)) {
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
  def javaTypeName(s: Symbol): String = mapType(s, _.javaClassName)

  /** Returns the simple name for the passed symbol (it expects the symbol to be a type).*/
  def javaSimpleTypeName(s: Symbol): String = mapType(s, _.javaSimpleName.toString)

  override def javaTypeNameMono(tpe: Type): String = {
    val base = javaTypeName(tpe.typeSymbol)
    tpe.typeSymbol match {
      // only the Array class has type parameters. the Array object is non-parametric
      case definitions.ArrayClass =>
        val paramTypes = tpe.normalize.typeArgs.map(javaTypeNameMono) // normalize is needed when you have `type BitSet = Array[Int]`
        assert(paramTypes.size == 1, "Expected exactly one type parameter, found %d [%s]".format(paramTypes.size, tpe))
        paramTypes.head + "[]"
      case _ =>
        if (tpe.typeParams.nonEmpty)
          logger.debug("mapType(Type) is not expected to be used with a type that has type parameters. (passed type was %s)".format(tpe))
        base
    }
  }

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

  override def mapParamTypePackageName(tpe: Type): String = {
    if (tpe.typeSymbolDirect.isTypeParameter)
      ""
    else {
      if (definitions.isPrimitiveValueType(tpe))
        ""
      else
        tpe.typeSymbol.enclosingPackage.fullName
    }
  }

  def isScalaSpecialType(t : Type) = {
    import definitions._
    t.typeSymbol match {
      case AnyClass | AnyRefClass | AnyValClass | NothingClass | NullClass => true
      case _ => false
    }
  }

  override def mapParamTypeSignature(tpe: Type): String = {
    val objectSig = "Ljava.lang.Object;"
    if (tpe.typeSymbolDirect.isTypeParameter)
      "T"+tpe.typeSymbolDirect.name.toString+";"
    else if (isScalaSpecialType(tpe) || tpe.isErroneous)
      objectSig
    else
      javaDescriptor(tpe).replace('/', '.')
  }

  import icodes._

  /** Return the descriptor of the given type. A typed descriptor is defined
   *  by the JVM Specification Section 4.3 (http://docs.oracle.com/javase/specs/vms/se7/html/jvms-4.html#jvms-4.3)
   *
   *  Example:
   *   javaDescriptor(Array[List[Int]]) == "[Lscala/collection/immutable/List;"
   */
  private def javaDescriptor(tk: TypeKind): String = {
    import Signature._
    (tk: @unchecked) match {
      case BOOL           => C_BOOLEAN.toString
      case BYTE           => C_BYTE.toString
      case SHORT          => C_SHORT.toString
      case CHAR           => C_CHAR.toString
      case INT            => C_INT.toString
      case UNIT           => C_VOID.toString
      case LONG           => C_LONG.toString
      case FLOAT          => C_FLOAT.toString
      case DOUBLE         => C_DOUBLE.toString
      case REFERENCE(cls) => s"L${cls.javaBinaryName};"
      case ARRAY(elem)    => s"[${javaDescriptor(elem)}"
    }
  }

  override def javaDescriptor(tpe: Type): String =
    if (tpe.isErroneous) "Ljava/lang/Object;"
    else javaDescriptor(toTypeKind(tpe.erasure)) // `toTypeKind` only handles erased types

  override def enclosingTypeName(s : Symbol): String =
    if (s == NoSymbol || s.hasFlag(Flags.PACKAGE)) ""
    else {
      val owner = s.owner
      val prefix = if (owner != NoSymbol && !owner.hasFlag(Flags.PACKAGE)) enclosingTypeName(s.owner)+"." else ""
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
   *  the empty string, instead of <empty>.
   */
  override def javaEnclosingPackage(sym: Symbol): String = {
    val enclPackage = sym.enclosingPackage
    if (enclPackage == rootMirror.EmptyPackage || enclPackage == rootMirror.RootPackage)
      ""
    else
      enclPackage.fullName
  }
}

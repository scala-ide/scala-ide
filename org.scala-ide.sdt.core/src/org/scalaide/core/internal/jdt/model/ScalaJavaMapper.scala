package org.scalaide.core.internal.jdt.model

import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants
import scala.tools.nsc.symtab.Flags
import org.scalaide.logging.HasLogger
import org.eclipse.jdt.core._
import org.eclipse.jdt.internal.core.JavaModelManager
import org.eclipse.core.runtime.Path
import org.scalaide.core.compiler.IScalaPresentationCompiler.Implicits._
import org.scalaide.core.internal.compiler.InternalCompilerServices
import org.scalaide.core.internal.compiler.ScalaPresentationCompiler

/** Implementation of a internal compiler services dealing with mapping Scala types and symbols
 *  to internal JDT counterparts.
 */
trait ScalaJavaMapper extends InternalCompilerServices with ScalaAnnotationHelper with HasLogger { self: ScalaPresentationCompiler =>

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
    // Correctly handle NullaryMethodType that sometimes may leak as a type of ValDefs
    // Since .typeSymbol forwards to `resultType.typeSymbol` we might get into inconsistencies
    // where tpe.typeArgs is Nil (for a nullary method type), but typeSymbol is Array, and therefore
    // expects one type argument.
    val tpe1 = tpe match {
      case NullaryMethodType(resultType) => resultType
      case _ => tpe
    }
    val base = javaTypeName(tpe1.typeSymbol)
    tpe1.typeSymbol match {
      // only the Array class has type parameters. the Array object is non-parametric
      case definitions.ArrayClass =>
        val paramTypes = tpe1.normalize.typeArgs.map(javaTypeNameMono) // normalize is needed when you have `type BitSet = Array[Int]`
        if (paramTypes.size != 1)
          logger.error(s"Expected exactly one type parameter, found ${paramTypes.size} [$tpe1]")
        paramTypes.head + "[]"
      case _ =>
        if (tpe1.typeParams.nonEmpty)
          logger.error(s"javaTypeNameMono is not expected to be used with a type that has type parameters. (passed type was $tpe1)")
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

  private lazy val objectSig = "Ljava.lang.Object;"

  override def mapParamTypeSignature(tpe: Type): String = {
    if (tpe.typeSymbolDirect.isTypeParameter)
      "T"+tpe.typeSymbolDirect.name.toString+";"
    else if (isScalaSpecialType(tpe) || tpe.isErroneous)
      objectSig
    else
      javaDescriptor(tpe).replace('/', '.')
  }

  /** Return the descriptor of the given type. A typed descriptor is defined
   *  by the JVM Specification Section 4.3 (http://docs.oracle.com/javase/specs/vms/se7/html/jvms-4.html#jvms-4.3)
   *
   *  Example:
   *   toJavaDescriptor(Array[List[Int]]) == "[Lscala/collection/immutable/List;"
   */
  private def toJavaDescriptor(tpe: Type): String = {
    import scala.reflect.internal.ClassfileConstants._
    val (sym, args) = tpe match {
      case TypeRef(_, sym, args) => (sym, args)
      case rt @ RefinedType(_, _) => (rt.typeSymbol, rt.typeArgs)
      case ExistentialType(_, typ) => (typ.typeSymbol, Nil)
    }
    sym match {
      case definitions.UnitClass => VOID_TAG.toString
      case definitions.BooleanClass => BOOL_TAG.toString
      case definitions.CharClass => CHAR_TAG.toString
      case definitions.ByteClass => BYTE_TAG.toString
      case definitions.ShortClass => SHORT_TAG.toString
      case definitions.IntClass => INT_TAG.toString
      case definitions.FloatClass => FLOAT_TAG.toString
      case definitions.LongClass => LONG_TAG.toString
      case definitions.DoubleClass => DOUBLE_TAG.toString
      case sym if sym == definitions.NullClass => OBJECT_TAG + SCALA_NULL + ";"
      case sym if sym == definitions.NothingClass => OBJECT_TAG + SCALA_NOTHING + ";"
      case sym if sym.isAliasType => toJavaDescriptor(sym.info.resultType)
      case sym if sym.isTypeParameterOrSkolem => objectSig
      case definitions.ArrayClass => ARRAY_TAG + args.headOption.map(toJavaDescriptor).getOrElse(objectSig)
      case sym if sym != NoSymbol => OBJECT_TAG + sym.javaBinaryNameString + ";"
      case _ => objectSig
    }
  }

  override def javaDescriptor(tpe: Type): String = {
    if (tpe.isErroneous) "Ljava/lang/Object;"
    else toJavaDescriptor(tpe)
  }

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

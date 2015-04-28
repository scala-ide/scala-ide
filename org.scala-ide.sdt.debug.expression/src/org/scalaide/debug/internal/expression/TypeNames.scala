/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression

import scala.reflect.runtime.universe

import org.eclipse.jdi.internal.TypeImpl

import Names.Java
import Names.Scala

object TypeNames {

  /**
   * Converts array signature to name.
   *
   * For example `[[I` becomes `int[][]` and `[Ljava.lang.String` becomes `java.lang.String[]`.
   */
  def arraySignatureToName(javaArrayTpe: String): String =
    TypeImpl.arraySignatureToName(javaArrayTpe)

  def fixScalaObjectType(name: String): String = {
    if (name.endsWith(".type")) name.dropRight(".type".length) + "$"
    else name
  }

  case class Primitive(java: String, scala: String, javaBoxed: String, scalaRich: String)

  val Boolean = Primitive(Java.primitives.boolean, Scala.primitives.Boolean, Java.boxed.Boolean, Scala.rich.Boolean)
  val Byte = Primitive(Java.primitives.byte, Scala.primitives.Byte, Java.boxed.Byte, Scala.rich.Byte)
  val Char = Primitive(Java.primitives.char, Scala.primitives.Char, Java.boxed.Character, Scala.rich.Char)
  val Double = Primitive(Java.primitives.double, Scala.primitives.Double, Java.boxed.Double, Scala.rich.Double)
  val Float = Primitive(Java.primitives.float, Scala.primitives.Float, Java.boxed.Float, Scala.rich.Float)
  val Int = Primitive(Java.primitives.int, Scala.primitives.Int, Java.boxed.Integer, Scala.rich.Int)
  val Long = Primitive(Java.primitives.long, Scala.primitives.Long, Java.boxed.Long, Scala.rich.Long)
  val Short = Primitive(Java.primitives.short, Scala.primitives.Short, Java.boxed.Short, Scala.rich.Short)
  val Unit = Primitive(Java.primitives.void, Scala.unitType, Java.boxed.Void, "n/a")

  private val primitives: Seq[Primitive] = Seq(Boolean, Byte, Char, Double, Float, Int, Long, Short, Unit)

  type Convert = Primitive => String
  val JavaPrimitive: Convert = _.java
  val ScalaPrimitive: Convert = _.scala
  val JavaBoxed: Convert = _.javaBoxed

  /**
   * Example usage: `convert(typeName, from = JavaPrimitive, to = ScalaPrimitive)`.
   */
  def convert(value: String, from: Convert, to: Convert): Option[String] =
    primitives.find(p => from(p) == value).map(to)

  /**
   * Maps java primitives to Scala unified names.
   * Java arrays (int[], String[]) are mapped to Scala Arrays (Array[Int], Array[String]).
   * Other names are unchanged.
   */
  def javaNameToScalaName(typeName: String): String =
    convert(typeName, from = JavaPrimitive, to = ScalaPrimitive).getOrElse {
      typeName match {
        case Java.primitives.Array(innerType) => Scala.Array(javaNameToScalaName(innerType))
        case other => other
      }
    }

  /** Drops ''scala.'' from primitive type names */
  def fixScalaPrimitives(name: String): String =
    if (Names.Scala.primitives.all.contains(name))
      name.drop("scala.".size)
    else name

  /**
   * @param withoutGenerics remove generics for this tree
   * @return Some(<type name>) if `tree.tpe != null`
   */
  def fromTree(tree: universe.Tree, withoutGenerics: Boolean = false): Option[String] = {
    def isPackageType(tpe: universe.Type) = tpe.widen.getClass.getSimpleName == "PackageTypeRef"

    tree.tpe match {
      case null => None
      case packageType if isPackageType(packageType) => None
      case tpe => Some(typeName(tpe, withoutGenerics))
    }
  }

  /** Same as `fromTree`, but throws exception if `tree.tpe == null`. */
  def getFromTree(tree: universe.Tree, withoutGenerics: Boolean = false): String =
    tree.tpe match {
      case null => throw new RuntimeException(s"Tree: $tree have null type.")
      case tpe => typeName(tpe, withoutGenerics)
    }

  /**
   * Obtains type name as string for given tree.
   *
   * @param tpe tree to search for
   */
  private def typeName(tpe: universe.Type, withoutGenerics: Boolean): String = {

    import universe._

    def isUnderscoreType(tpe: Type) = tpe.getClass.getSimpleName == "AbstractNoArgsTypeRef"

    def isObjectType(tpe: Type) = tpe.getClass.getSimpleName == "ModuleTypeRef"

    def isPackageObjectScoped(typeRef: TypeRef) = typeRef.typeSymbol.name.toString.startsWith("package$")

    tpe.widen match {
      case NoType =>
        "Any"
      case underscoreType if isUnderscoreType(underscoreType) =>
        "Any"
      case moduleTypeRef if isObjectType(moduleTypeRef) =>
        moduleTypeRef.typeSymbol.fullName + ".type"
      case typeRef: TypeRef if isPackageObjectScoped(typeRef) =>
        typeRef.typeSymbol.fullName.replace("package$", "")
      case typeRef: TypeRef =>
        genenericTypeString(typeRef, typeRef.args, withoutGenerics)
      case existentialType @ ExistentialType(_, _) =>
        genenericTypeString(existentialType, existentialType.typeArgs, withoutGenerics)
      case any =>
        throw new RuntimeException(s"Unsupported tree shape: $any.")
    }
  }

  private def genenericTypeString(baseType: universe.Type, args: Seq[universe.Type], withoutGenerics: Boolean): String = {
    val rootType = fixScalaPrimitives(baseType.typeSymbol.fullName)
    val isArray = rootType == Names.Scala.ArrayRoot
    if (args.isEmpty || (withoutGenerics && !isArray))
      rootType
    else {
      val argsStrings = args.map(t => typeName(t, withoutGenerics = false)).mkString(", ")
      s"$rootType[$argsStrings]"
    }
  }
}

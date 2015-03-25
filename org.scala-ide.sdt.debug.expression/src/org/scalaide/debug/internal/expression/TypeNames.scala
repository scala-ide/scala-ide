/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression

import Names.Java
import Names.Scala

import scala.reflect.runtime.universe
import scala.reflect.runtime.universe.TypeRef

object TypeNames {

  /**
   * Maps java primitives to Scala unified names.
   * Java arrays (int[], String[]) are mapped to Scala Arrays (Array[Int], Array[String]).
   * Other names are unchanged.
   */
  def javaNameToScalaName(typeName: String): String = typeName match {
    case Java.primitives.boolean => Scala.primitives.Boolean
    case Java.primitives.byte => Scala.primitives.Byte
    case Java.primitives.char => Scala.primitives.Char
    case Java.primitives.double => Scala.primitives.Double
    case Java.primitives.float => Scala.primitives.Float
    case Java.primitives.int => Scala.primitives.Int
    case Java.primitives.long => Scala.primitives.Long
    case Java.primitives.short => Scala.primitives.Short
    case Java.primitives.void => Scala.unitType
    case Java.primitives.Array(innerType) => Scala.Array(javaNameToScalaName(innerType))
    case other => other
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
  def fromTree(tree: universe.Tree, withoutGenerics: Boolean = false): Option[String] = tree.tpe match {
    case null => None
    case tpe => Some(typeName(tpe, withoutGenerics))
  }

  /** Same as `fromTree`, but throws exception if `tree.tpe == null`. */
  def getFromTree(tree: universe.Tree, withoutGenerics: Boolean = false): String = tree.tpe match {
    case null => throw new RuntimeException(s"Tree: $tree have null type.")
    case tpe => typeName(tpe, withoutGenerics)
  }

  /**
   * Obtains type name as string for given tree.
   *
   * @param tpe tree to search for
   */
  private def typeName(tpe: universe.Type, withoutGenerics: Boolean): String = {

    def isUnderscoreType(tpe: universe.Type) = tpe.getClass.getSimpleName == "AbstractNoArgsTypeRef"

    def isObjectType(tpe: universe.Type) = tpe.getClass.getSimpleName == "ModuleTypeRef"

    def isPackageObjectScoped(typeRef: TypeRef) = typeRef.typeSymbol.name.toString.startsWith("package$")

    import universe._
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

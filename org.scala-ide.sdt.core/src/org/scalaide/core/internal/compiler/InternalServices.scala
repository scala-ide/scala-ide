package org.scalaide.core.internal.compiler

import scala.tools.nsc.interactive.Global

/** This trait groups methods are only available to core IDE implementations.
 *  They may change without notice or deprecation cycle.
 */
trait InternalServices extends Global {
  /** Return the enclosing package. Correctly handle the empty package, by returning
   *  the empty string, instead of <empty>.
   */
  private[core] def javaEnclosingPackage(sym: Symbol): String

  /** Return the full name of the enclosing type name, without enclosing packages. */
  private[core] def enclosingTypeName(sym: Symbol): String

  /** Return the enclosing package name of given type. */
  private[core] def mapParamTypePackageName(tpe: Type): String

  /** Return the type signature of `tpe`. This method returns a JDT specific
   *  string, and probably useless in any other case.
   *
   *  @return a JVM type descriptor, except that the separator is '.' instead of
   *          '/'.
   */
  private[core] def mapParamTypeSignature(tpe: Type): String

  /** Return the descriptor of the given type. A typed descriptor is defined
   *  by the JVM Specification Section 4.3 (http://docs.oracle.com/javase/specs/vms/se7/html/jvms-4.html#jvms-4.3)
   *
   *  Example:
   *   javaDescriptor(Array[List[Int]]) == "[Lscala/collection/immutable/List;"
   */
  private[core] def javaDescriptor(tpe: Type): String

  /** Return a JDT specific value for the modifiers of given symbol/ */
  private[core] def mapModifiers(sym: Symbol): Int

  /** Map a Scala `Type` that '''does not take type parameters''' into its
   *  Java representation.
   *  A special case exists for Scala `Array` since in Java arrays do not take
   *  type parameters.
   */
  private[core] def javaTypeNameMono(tpe: Type): String
}
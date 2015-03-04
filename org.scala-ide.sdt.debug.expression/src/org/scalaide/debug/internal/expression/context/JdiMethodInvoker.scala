/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package context

import scala.annotation.tailrec
import scala.util.Success
import scala.util.Try

import org.scalaide.debug.internal.expression.Names.Scala
import org.scalaide.debug.internal.expression.context.invoker._
import org.scalaide.debug.internal.expression.proxies.JdiProxy
import org.scalaide.debug.internal.expression.proxies.StaticCallClassJdiProxy

import com.sun.jdi.ClassType
import com.sun.jdi.ReferenceType
import com.sun.jdi.Value

private[context] trait JdiMethodInvoker {
  self: JdiContext =>

  /**
   * Invokes a method on a proxy. Wraps `invokeUnboxed` with a `valueProxy`.
   *
   * WARNING - this method is used in reflective compilation.
   * If you change it's name, package or behavior, make sure to change it also.
   *
   *
   * @param on
   * @param onScalaType Scala type of object laying under proxy (e.g. for '1' in code '1.toDouble' it will be RichInt)
   * if you are not aware which type Scala see for object or you are not interested in e.g. AnyVal method calls just pass None here
   * @param name
   * @param args list of list of arguments to pass to method (flattened)
   * @return JdiProxy with a result of a method call
   */
  def invokeMethod(on: JdiProxy,
    onScalaType: Option[String],
    name: String,
    args: Seq[JdiProxy] = Seq.empty): JdiProxy =
    valueProxy(invokeUnboxed[Value](on, onScalaType, name, args))

  /**
   * Invokes a method on a proxy. Returns unboxed value.
   *
   * Tries to call normal method, if it fails proceeds to vararg version and String concatenation.
   * If all above fails, throws `java.lang.NoSuchMethodError`
   *
   * @param proxy
   * @param onRealType Scala type of object laying under proxy (e.g. for '1' in code '1.toDouble' it will be RichInt)
   * if you are not aware which type Scala see for object or you are not interested in e.g. AnyVal method calls just pass None here
   * @param name
   * @param args list of list of arguments to pass to method (flattened)
   * @return JdiProxy with a result of a method call
   */
  final def invokeUnboxed[Result <: Value](proxy: JdiProxy, onRealType: Option[String], name: String,
    args: Seq[JdiProxy] = Seq.empty): Result = {

    def noSuchMethod: Nothing = {
      val tpeName = proxy.referenceType.name
      val argsString = args.map(_.referenceType.name).mkString("(", ", ", ")")
      throw new NoSuchMethodError(s"field of type $tpeName has no method named $name with arguments: $argsString")
    }

    (tryInvokeUnboxed(proxy, onRealType, name, args) getOrElse noSuchMethod)
      .asInstanceOf[Result]
  }

  /** invokeUnboxed method that returns option instead of throwing an exception */
  private[expression] def tryInvokeUnboxed(proxy: JdiProxy,
    onRealType: Option[String],
    name: String,
    methodArgs: Seq[JdiProxy] = Seq.empty): Option[Value] = {

    proxy match {
      case StaticCallClassJdiProxy(_, classType) => tryInvokeJavaStaticMethod(classType, name, methodArgs)
      case _ =>
        val standardMethod = new StandardMethod(proxy, name, methodArgs, this)
        def varArgMethod = new VarArgMethod(proxy, name, methodArgs, this)
        def stringConcat = new StringConcatenationMethod(proxy, name, methodArgs)
        def anyValMethod = new AnyValMethod(proxy, name, methodArgs, onRealType, this, this)
        def javaField = new JavaField(proxy, name, methodArgs, this)

        standardMethod() orElse
          varArgMethod() orElse
          stringConcat() orElse
          anyValMethod() orElse
          javaField()
    }
  }

  /** TODO - document this, it's an API */
  final def invokeJavaStaticMethod[Result <: JdiProxy](
    classType: ClassType,
    methodName: String,
    methodArgs: Seq[JdiProxy]): Result =
    tryInvokeJavaStaticMethod(classType, methodName, methodArgs)
      .map(valueProxy(_)).getOrElse {
        throw new NoSuchMethodError(s"class ${classType.name} has no static method named $methodName")
      }.asInstanceOf[Result]

  /** TODO - document this, it's an API */
  final def tryInvokeJavaStaticMethod(classType: ClassType, methodName: String, methodArgs: Seq[JdiProxy]): Option[Value] =
    new JavaStaticMethod(classType, methodName, methodArgs, this).apply()

  /** TODO - document this, it's an API */
  final def getJavaStaticField[Result <: JdiProxy](referenceType: ReferenceType, fieldName: String): Result = {
    val fieldAccessor = new JavaStaticFieldGetter(referenceType, fieldName)
    val value = fieldAccessor.getValue()
    valueProxy(value).asInstanceOf[Result]
  }

  /** TODO - document this, it's an API */
  final def setJavaStaticField[Result <: JdiProxy](classType: ClassType, fieldName: String, newValue: Any): Unit = {
    val fieldAccessor = new JavaStaticFieldSetter(classType, fieldName)
    fieldAccessor setValue newValue
  }

  /**
   * Creates new instance of given class.
   *
   * WARNING - this method is used in reflective compilation.
   * If you change it's name, package or behavior, make sure to change it also.
   *
   * @param className class for object to create
   * @param args list of list of arguments to pass to method
   * @param implicits list of implicit arguments
   * @throws NoSuchMethodError when matching constructor is not found
   * @throws IllegalArgumentException when result is not of requested type
   */
  final def newInstance(
    className: String,
    args: Seq[JdiProxy] = Seq.empty): JdiProxy = {

    def noSuchConstructor: Nothing = throw new NoSuchMethodError(s"class $className" +
      s" has no constructor with arguments: ${args.map(_.referenceType.name).mkString(", ")}")

    tryNewInstance(className, args).getOrElse(noSuchConstructor)
  }

  /** newInstance method that returns None if matching constructor is not found */
  private[context] def tryNewInstance(
    className: String,
    methodArgs: Seq[JdiProxy]): Option[JdiProxy] = {

    @tailrec def tryNext(name: String): Option[JdiProxy] = {
      val proxy = Try((name match {
        case Scala.Array(typeParam) => new ArrayConstructor(name, methodArgs, this)()
        case _ => new StandardConstructor(name, methodArgs, this).apply()
      }).map(valueProxy))

      proxy match {
        case Success(some: Some[_]) => some
        case Success(none) if !name.contains('.') => none
        case _ => tryNext(name.reverse.replaceFirst("\\.", "\\$").reverse)
      }
    }

    tryNext(className)
  }
}

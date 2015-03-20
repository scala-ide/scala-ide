/*
 * Copyright (c) 2014 - 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package context

import scala.annotation.tailrec
import scala.reflect.NameTransformer
import scala.util.Success
import scala.util.Try

import org.scalaide.debug.internal.expression.Names.Scala
import org.scalaide.debug.internal.expression.context.invoker.AnyValMethod
import org.scalaide.debug.internal.expression.context.invoker.ArrayConstructor
import org.scalaide.debug.internal.expression.context.invoker.JavaField
import org.scalaide.debug.internal.expression.context.invoker.JavaStaticFieldGetter
import org.scalaide.debug.internal.expression.context.invoker.JavaStaticFieldSetter
import org.scalaide.debug.internal.expression.context.invoker.JavaStaticMethod
import org.scalaide.debug.internal.expression.context.invoker.JavaStaticVarArgMethod
import org.scalaide.debug.internal.expression.context.invoker.JavaVarArgConstructorMethod
import org.scalaide.debug.internal.expression.context.invoker.JavaVarArgMethod
import org.scalaide.debug.internal.expression.context.invoker.LocalVariableInvoker
import org.scalaide.debug.internal.expression.context.invoker.MethodInvoker
import org.scalaide.debug.internal.expression.context.invoker.ScalaVarArgMethod
import org.scalaide.debug.internal.expression.context.invoker.StandardConstructorMethod
import org.scalaide.debug.internal.expression.context.invoker.StandardMethod
import org.scalaide.debug.internal.expression.context.invoker.StringConcatenationMethod
import org.scalaide.debug.internal.expression.context.invoker.VarArgConstructorMethod
import org.scalaide.debug.internal.expression.proxies.JdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.UnitJdiProxy

import com.sun.jdi.ClassType
import com.sun.jdi.ReferenceType
import com.sun.jdi.Value

private[context] trait JdiMethodInvoker {
  self: JdiContext =>

  /**
   * Invokes a method on a proxy. Wraps `invokeUnboxed` with a `valueProxy`.
   *
   * WARNING - this method is used in reflective compilation.
   * If you change its name, package or behavior, make sure to change it also.
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

    (tryInvokeUnboxed(proxy, onRealType, name, args) getOrElse noSuchMethod).asInstanceOf[Result]
  }

  /** invokeUnboxed method that returns option instead of throwing an exception */
  private[expression] def tryInvokeUnboxed(
    proxy: JdiProxy,
    onRealType: Option[String],
    methodName: String,
    methodArgs: Seq[JdiProxy] = Seq.empty): Option[Value] = {

    val encodedName = NameTransformer.encode(methodName)

    val standardMethod = new StandardMethod(proxy, encodedName, methodArgs, this)
    def varArgMethod = new ScalaVarArgMethod(proxy, encodedName, methodArgs, this)
    def javaVarArgMethod = new JavaVarArgMethod(proxy, methodName, methodArgs, this)
    def stringConcat = new StringConcatenationMethod(proxy, encodedName, methodArgs)
    def anyValMethod = new AnyValMethod(proxy, encodedName, methodArgs, onRealType, this, this)
    def javaField = new JavaField(proxy, methodName, methodArgs, this)

    standardMethod() orElse
      varArgMethod() orElse
      javaVarArgMethod() orElse
      stringConcat() orElse
      anyValMethod() orElse
      javaField()
  }

  /**
   * Invokes Java-style static method.
   *
   * @param classType type on which method is invoked
   * @param methodName name of method to invoke
   * @tparam Result type of result (subclass of `JdiProxy`)
   * @throws ClassCastException if result is not of given type
   * @throws NoSuchMethodError if method does not exist
   * @return value returned from method
   */
  final def invokeJavaStaticMethod[Result <: JdiProxy](
    classType: ClassType,
    methodName: String,
    methodArgs: Seq[JdiProxy]): Result = {
    val javaStaticMethod = new JavaStaticMethod(classType, methodName, methodArgs, this)
    def javaStaticVarArgMethod = new JavaStaticVarArgMethod(classType, methodName, methodArgs, this)
    def noSuchMethod() = throw new NoSuchMethodError(s"class ${classType.name} has no static method named $methodName")

    val value = javaStaticMethod() orElse
      javaStaticVarArgMethod() getOrElse
      noSuchMethod()

    valueProxy(value).asInstanceOf[Result]
  }

  /**
   * Gets value from Java-style static field.
   *
   * @param referenceType type from which field is got
   * @param fieldName name of field to get value from
   * @tparam Result type of result (subclass of `JdiProxy`)
   * @throws ClassCastException if result is not of given type
   * @throws NoSuchMethodError if method does not exist
   * @return value of given field
   */
  final def getJavaStaticField[Result <: JdiProxy](referenceType: ReferenceType, fieldName: String): Result = {
    val fieldAccessor = new JavaStaticFieldGetter(referenceType, fieldName)
    def noSuchMethod() = throw new NoSuchMethodError(s"type ${referenceType.name} has no static method named $fieldName")

    val value = fieldAccessor() getOrElse noSuchMethod()
    valueProxy(value).asInstanceOf[Result]
  }

  /**
   * Sets new value to a Java-style static field.
   *
   * @param classType ClassType on which field is set
   * @param fieldName name of field
   * @param newValue new value (as instance of JdiProxy)
   * @throws NoSuchMethodError if method does not exist
   * @return UnitJdiProxy
   */
  final def setJavaStaticField(classType: ClassType, fieldName: String, newValue: JdiProxy): UnitJdiProxy = {
    val fieldAccessor = new JavaStaticFieldSetter(classType, fieldName, newValue, this)
    val value = fieldAccessor().getOrElse {
      throw new NoSuchFieldError(s"type ${classType.name} has no static field named $fieldName")
    }
    valueProxy(value).asInstanceOf[UnitJdiProxy]
  }

  /**
   * Sets value for local variable.
   *
   * @param variableName name of variable
   * @param newValue new value (as instance of JdiProxy)
   * @return UnitJdiProxy
   */
  final def setLocalVariable(variableName: String, newValue: JdiProxy): UnitJdiProxy = {
    val nameEncoded = NameTransformer.encode(variableName)
    val invoker = new LocalVariableInvoker(nameEncoded, newValue, this, () => this.currentFrame())
    val result = invoker().getOrElse {
      throw new NoSuchFieldError(s"field $variableName not found in current frame")
    }
    valueProxy(result).asInstanceOf[UnitJdiProxy]
  }

  /**
   * Creates new instance of given class.
   *
   * WARNING - this method is used in reflective compilation.
   * If you change its name, package or behavior, make sure to change it also.
   *
   * @param className class for object to create
   * @param args list of list of arguments to pass to method
   * @throws NoSuchMethodError when matching constructor is not found
   */
  final def newInstance(
    className: String,
    args: Seq[JdiProxy] = Seq.empty): JdiProxy = {

    def noSuchConstructor: Nothing = throw new NoSuchMethodError(s"class $className " +
      s"has no constructor with arguments: ${args.map(_.referenceType.name).mkString("(", ", ", ")")}")

    tryNewInstance(className, args).getOrElse(noSuchConstructor)
  }

  /** newInstance method that returns None if matching constructor is not found */
  private[context] def tryNewInstance(
    className: String,
    methodArgs: Seq[JdiProxy]): Option[JdiProxy] = {

    def standardConstructor(clsName: String) = new StandardConstructorMethod(clsName, methodArgs, this)
    def varArgConstructor(clsName: String) = new VarArgConstructorMethod(clsName, methodArgs, this)
    def javaVarArgConstructor(clsName: String) = new JavaVarArgConstructorMethod(clsName, methodArgs, this)
    def arrayConstructor(clsName: String) = new ArrayConstructor(clsName, methodArgs, this)

    @tailrec def tryNext(clsName: String, constructor: String => MethodInvoker): Option[JdiProxy] = {
      val proxy = Try((clsName match {
        case Scala.Array(_) => arrayConstructor(clsName).apply()
        case standardClass => constructor(standardClass).apply()
      }).map(valueProxy))

      proxy match {
        case Success(some: Some[_]) => some
        case Success(other) if !clsName.contains('.') => other
        case Success(other) =>
          val newClsName = clsName.reverse.replaceFirst("\\.", "\\$").reverse
          if (newClsName != clsName) tryNext(newClsName, constructor) else None
        case util.Failure(ex: com.sun.jdi.InvocationException) =>
          val newClsName = clsName.reverse.replaceFirst("\\.", "\\$").reverse
          if (newClsName != clsName) tryNext(newClsName, constructor) else None
        case util.Failure(exception) => throw exception
      }
    }

    tryNext(className, standardConstructor) orElse
      tryNext(className, varArgConstructor) orElse
      tryNext(className, javaVarArgConstructor)
  }
}

/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.context

import scala.annotation.tailrec
import scala.collection.JavaConversions.asScalaBuffer
import scala.collection.JavaConversions.seqAsJavaList
import scala.reflect.ClassTag
import scala.reflect.NameTransformer
import scala.reflect.classTag
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.Names.Scala
import org.scalaide.debug.internal.expression.proxies.ArrayJdiProxy
import org.scalaide.debug.internal.expression.proxies.JdiProxy
import org.scalaide.debug.internal.expression.proxies.JdiProxyWrapper
import org.scalaide.debug.internal.expression.proxies.SimpleJdiProxy
import org.scalaide.debug.internal.expression.proxies.StaticCallClassJdiProxy
import org.scalaide.debug.internal.expression.proxies.StringJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.BoxedJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.IntJdiProxy

import com.sun.jdi.ArrayType
import com.sun.jdi.ClassNotLoadedException
import com.sun.jdi.ClassType
import com.sun.jdi.InterfaceType
import com.sun.jdi.Method
import com.sun.jdi.ObjectReference
import com.sun.jdi.PrimitiveType
import com.sun.jdi.ReferenceType
import com.sun.jdi.Type
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
   * @param proxy
   * @param onRealType Scala type (not jvm implementation) of object laying under proxy (e.g. for 1.toDouble it will be RichInt)
   *                   if you are not aware which type Scala see for object or you are not interested in e.g. AnyVal method calls just pass None here
   * @param methodName
   * @param args list of list of arguments to pass to method
   * @param implicits list of implicit arguments
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
   * @param onScalaType Scala type (not jvm implementation) of object laying under proxy (e.g. for 1.toDouble it will be RichInt)
   *                    if you are not aware which type Scala see for object or you are not interested in e.g. AnyVal method calls just pass None here
   * @param methodName
   * @param args list of list of arguments to pass to method
   * @param implicits list of implicit arguments
   * @return jdi unboxed Value with a result of a method call
   */
  final def invokeUnboxed[Result <: Value](proxy: JdiProxy, onRealType: Option[String], name: String,
                                           args: Seq[JdiProxy] = Seq.empty): Result = {

    def noSuchMethod: Nothing = throw new NoSuchMethodError(s"field of type ${proxy.referenceType.name}" +
      s" has no method named $name with arguments: ${args.map(_.referenceType.name).mkString(", ")}")

    (tryInvokeUnboxed(proxy, onRealType, name, args) getOrElse noSuchMethod)
      .asInstanceOf[Result]
  }

  /** invokeUnboxed method that returns option instead of throwing an exception */
  private[expression] def tryInvokeUnboxed(proxy: JdiProxy,
                                           onRealType: Option[String],
                                           name: String,
                                           args: Seq[JdiProxy] = Seq.empty): Option[Value] = {

    proxy match {
      case StaticCallClassJdiProxy(_, classType) => tryInvokeJavaStaticMethod(classType, name, args)
      case _ => tryInvokeUnboxedForInstance(proxy, onRealType, name, args)
    }
  }

  private def tryInvokeUnboxedForInstance(proxy: JdiProxy, onRealType: Option[String],
                                          name: String,
                                          methodArgs: Seq[JdiProxy]): Option[Value] = {
    val standardMethod = StandardMethod(proxy, name, methodArgs)
    def varArgMethod = VarArgsMethod(proxy, name, methodArgs)
    def stringConcat = StringConcatenationMethod(proxy, name, methodArgs)
    def anyValMethod = AnyValMethodCalls(proxy, name, methodArgs, onRealType)
    def javaField = JavaFieldCalls(proxy, name, methodArgs)

    standardMethod() orElse
      varArgMethod() orElse
      stringConcat() orElse
      anyValMethod() orElse
      javaField()
  }

  final def invokeJavaStaticMethod[Result <: JdiProxy](
                                                        classType: ClassType,
                                                        methodName: String,
                                                        methodArgs: Seq[JdiProxy]): Result =
    tryInvokeJavaStaticMethod(classType, methodName, methodArgs)
      .map(valueProxy(_)).getOrElse {
      throw new NoSuchMethodError(s"class ${classType.name} has no static method named $name")
    }.asInstanceOf[Result]

  final def tryInvokeJavaStaticMethod(classType: ClassType, methodName: String, methodArgs: Seq[JdiProxy]): Option[Value] =
    JavaStaticMethod(classType, methodName, methodArgs).apply()

  final def getJavaStaticField[Result <: JdiProxy](referenceType: ReferenceType, fieldName: String): Result = {
    val fieldAccessor = JavaStaticFieldGetter(referenceType, fieldName)
    val value = fieldAccessor.getValue()
    valueProxy(value).asInstanceOf[Result]
  }

  final def setJavaStaticField[Result <: JdiProxy](classType: ClassType, fieldName: String, newValue: Any): Unit = {
    val fieldAccessor = JavaStaticFieldSetter(classType, fieldName)
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
  private def tryNewInstance(
                              className: String,
                              methodArgs: Seq[JdiProxy]): Option[JdiProxy] = {

    @tailrec def tryNext(name: String): Option[JdiProxy] = {
      val proxy = Try((name match {
        case Scala.Array(typeParam) => ArrayConstructorMethod(name, methodArgs)()
        case _ => ConstructorMethod(name, methodArgs)()
      }).map(valueProxy))

      proxy match {
        case Success(Some(_)) => proxy.get
        case _ if !name.contains('.') => proxy.get
        case _ => tryNext(name.reverse.replaceFirst("\\.", "\\$").reverse)
      }
    }

    tryNext(className)
  }

  /** Common interface for method invokers. */
  private trait MethodType {

    /**
     * Checks if type underlying given proxy conforms to given type.
     *
     * @param proxy object to check
     * @param tpe type to check
     */
    protected final def conformsTo(proxy: JdiProxy, tpe: Type): Boolean =
      if (proxy.__underlying == null) tpe.isInstanceOf[ClassType]
      else (tpe, proxy, proxy.__underlying.referenceType) match {

        case (arrayType: ArrayType, arrayProxy: ArrayJdiProxy[_], _) => true

        case (_, wrapper: JdiProxyWrapper, _) => conformsTo(wrapper.__outer, tpe)

        case _ if tpe == proxy.referenceType => true

        case (primitive: PrimitiveType, boxed: BoxedJdiProxy[_, _], _) => primitive.name == boxed.primitiveName

        case (objectType, _, refType: ReferenceType) if objectType.name == "java.lang.Object" => true

        case (refType: ClassType, _, classType: ClassType) => isSuperClassOf(refType)(classType)

        case (interfaceType: InterfaceType, _, classType: ClassType) => classType.allInterfaces().contains(interfaceType)

        case _ => false
      }

    private def isSuperClassOf(parentClass: ClassType)(thisClass: ClassType): Boolean = {
      parentClass == thisClass || Option(thisClass.superclass()).map(isSuperClassOf(parentClass)).getOrElse(false)
    }

    /**
     * Runs `argumentTypes()` on given method, but catches all `ClassNotLoadedException`s and loads
     * required classes using provided context.
     * Context is called by-name cause it's only needed when something fails.
     */
    protected final def argumentTypesLoaded(method: Method, context: => JdiContext): Seq[Type] = {
      Try(method.argumentTypes()) match {
        case Success(types) => types
        case Failure(cnl: ClassNotLoadedException) =>
          context.loadClass(cnl.className)
          argumentTypesLoaded(method, context)
        case Failure(otherError) => throw otherError
      }
    }

    def apply(): Option[Value]
  }

  /**
   * Base operation on standard methods.
   */
  private abstract class BaseMethodOperation extends MethodType {

    // name of method
    val encoded: String

    // reference to search (could be object or ClassType in the case of Java static members)
    def referenceType: ReferenceType

    // arguments of call
    val args: Seq[JdiProxy]

    // method match for this call
    private def methodMatch(method: Method) =
      method.argumentTypeNames().size() == args.length &&
        argumentTypesLoaded(method, args.head.proxyContext).zip(args).forall {
          case (tpe, proxy) => conformsTo(proxy, tpe)
        }

    /**
     * Generates arguments for given call - transform boxed primitives to unboxed ones if needed
     */
    protected final def generateArguments(method: Method): Seq[Value] =
      method.argumentTypes().zip(args).map(mapSingleArgument)

    private def mapSingleArgument(args: (Type, JdiProxy)): Value = args match {
      case (primitive, parent: JdiProxyWrapper) => mapSingleArgument(primitive -> parent.__outer)
      case (expected: PrimitiveType, value: BoxedJdiProxy[_, _]) => value.primitive
      case (a, proxy) => proxy.__underlying
    }

    //search for all methods
    private def allMethods = referenceType.methodsByName(encoded)

    //found methods
    protected final def matching = allMethods.filter(methodMatch)
  }

  private case class ArrayConstructorMethod(className: String, args: Seq[JdiProxy]) {
    def apply(): Option[Value] = args match {
      case List(proxy: IntJdiProxy) =>
        val arrayType = JdiMethodInvoker.this.arrayClassByName(className)
        Some(arrayType.newInstance(proxy._IntMirror))
      case other => None
    }
  }

  // provides support for constructors
  private case class ConstructorMethod(className: String, args: Seq[JdiProxy]) extends BaseMethodOperation {
    override def referenceType: ClassType = classByName(className)

    override val encoded: String = Scala.constructorMethodName

    override def apply(): Option[Value] = {
      for {
        requestedMethod <- matching.headOption
        finalArgs = generateArguments(requestedMethod)
      } yield {
        referenceType.newInstance(currentThread, requestedMethod, finalArgs, ObjectReference.INVOKE_SINGLE_THREADED)
      }
    }
  }

  /**
   * Calls standard method on given `ObjectReference` in context of debug.
   */
  private case class StandardMethod(proxy: JdiProxy, name: String, args: Seq[JdiProxy]) extends BaseMethodOperation {

    override val encoded: String = NameTransformer.encode(name)

    override def referenceType: ReferenceType = proxy.referenceType

    override def apply(): Option[Value] =
      for {
        requestedMethod <- matching.headOption
        finalArgs = generateArguments(requestedMethod)
      } yield {
        proxy.__underlying.invokeMethod(currentThread, requestedMethod, finalArgs, ObjectReference.INVOKE_SINGLE_THREADED)
      }
  }

  /**
   * Calls standard static Java methods in context of debug.
   */
  private case class JavaStaticMethod(referenceType: ClassType, methodName: String, args: Seq[JdiProxy]) extends BaseMethodOperation {

    override val encoded: String = methodName

    override def apply(): Option[Value] =
      for {
        requestedMethod <- matching.find(_.isStatic())
        finalArgs = generateArguments(requestedMethod)
      } yield referenceType.invokeMethod(currentThread, requestedMethod, finalArgs, ObjectReference.INVOKE_SINGLE_THREADED)
  }

  private trait WithJavaStaticField {
    protected val referenceType: ReferenceType
    protected val fieldName: String

    protected val field = {
      Option(referenceType.fieldByName(fieldName)).getOrElse {
        throw new NoSuchFieldError(s"type ${referenceType.name} has no static field named $name")
      }
    }
  }

  private case class JavaStaticFieldGetter(referenceType: ReferenceType, fieldName: String) extends WithJavaStaticField {

    def getValue(): Value = referenceType.getValue(field)
  }

  private case class JavaStaticFieldSetter(referenceType: ClassType, fieldName: String) extends WithJavaStaticField {

    def setValue(newValue: Any): Unit = {
      val newValueProxy = newValue.asInstanceOf[JdiProxy].__underlying
      referenceType.setValue(field, newValueProxy)
    }
  }

  /**
   * Checks which calls can be potential access to Java field and tries to apply them.
   */
  private case class JavaFieldCalls(proxy: JdiProxy, name: String, methodArgs: Seq[JdiProxy]) {

    def apply(): Option[Value] = methodArgs match {
      case Seq() => tryGetValue()
      case Seq(newValue) if name.endsWith("_=") =>
        // this case is not tested because, when it really could be used, Toolbox throws exception earlier
        val fieldName = name.dropRight(2) // drop _= at the end of Scala setter
        trySetValue(fieldName, newValue.__underlying)
      case _ => None
    }

    private def refType = proxy.__underlying.referenceType()

    private def tryGetValue() = {
      val field = Option(refType.fieldByName(name))
      field map proxy.__underlying.getValue
    }

    private def trySetValue(fieldName: String, newValue: Value) = {
      val field = Option(refType.fieldByName(fieldName))
      field.map { f =>
        proxy.__underlying.setValue(f, newValue)
        jvm.mirrorOfVoid()
      }
    }
  }

  /**
   * Calls vararg method on given `ObjectReference` in context of debug.
   */
  private case class VarArgsMethod(proxy: JdiProxy, name: String, args: Seq[JdiProxy]) extends MethodType {
    private val seqName = "scala.collection.Seq"

    private def toSeqObj = objectByName(seqName)

    private def emptyMethod = methodOn(toSeqObj, "empty")

    private def addMethod = methodOn(seqName, "$plus$colon")

    private def canBuildFrom = toSeqObj.invokeMethod(currentThread, methodOn(toSeqObj, "canBuildFrom"), List[Value](),
      ObjectReference.INVOKE_SINGLE_THREADED)

    private def emptyList = toSeqObj.invokeMethod(currentThread, emptyMethod, List[Value](),
      ObjectReference.INVOKE_SINGLE_THREADED).asInstanceOf[ObjectReference]

    private def packToVarArg(proxies: Seq[JdiProxy]): Value = proxies.foldRight(emptyList) {
      (value, current) =>
        current.invokeMethod(currentThread, addMethod, List(value.__underlying, canBuildFrom),
          ObjectReference.INVOKE_SINGLE_THREADED).asInstanceOf[ObjectReference]
    }

    private def candidates = proxy.__underlying.referenceType().methodsByName(name)
      .filter(method => method.argumentTypeNames.size <= args.size)

    private def testResArgs(rest: Seq[Type]): Boolean = rest.reverse.zip(args).forall {
      case (tpe, proxy) => conformsTo(proxy, tpe)
    }

    private def tryCallSingleVarArgMethod(method: Method): Option[Value] = {
      argumentTypesLoaded(method, proxy.proxyContext).toSeq.reverse match {
        case vararg +: normal if vararg.name == seqName && testResArgs(normal) =>
          Some(proxy.__underlying.invokeMethod(currentThread, method, Seq(packToVarArg(args.drop(normal.size))),
            ObjectReference.INVOKE_SINGLE_THREADED))
        case _ => None
      }
    }

    override def apply(): Option[Value] =
      candidates.flatMap(method => tryCallSingleVarArgMethod(method)).headOption
  }

  /**
   * Custom handler for string concatenation (`obj + String` or `String + obj`).
   *
   * Those calls are replaced with `String.concat(a, b)`.
   */
  private case class StringConcatenationMethod(proxy: JdiProxy, name: String, args: Seq[JdiProxy]) extends MethodType {
    private val context = proxy.proxyContext

    private def stringify(proxy: JdiProxy) = StringJdiProxy(context, context.callToString(proxy))

    private def callConcatMethod(proxy: JdiProxy, arg: JdiProxy) =
      context.tryInvokeUnboxed(proxy, None, "concat", Seq(stringify(arg)))

    override def apply(): Option[Value] = (name, args) match {
      case ("+" | "$plus", Seq(arg)) =>
        (proxy.referenceType.name, arg.referenceType.name) match {
          case (Java.boxed.String, _) => callConcatMethod(proxy, arg)
          case (_, Java.boxed.String) => callConcatMethod(stringify(proxy), arg)
          case _ => None
        }
      case _ => None
    }
  }

  /**
   * Custom handler for AnyVal calls
   * Call like on.method(restOfParams) is replaced with CompanionObject.method(on, restOfParams) or (new BoxingClass(on).method(restOfParams)
   */
  private case class AnyValMethodCalls(proxy: JdiProxy, methodName: String, args: Seq[JdiProxy], realThisType: Option[String]) extends MethodType {
    private val context = proxy.proxyContext

    private def companionObject = for {
      companionObjectName <- realThisType
      objectReference <- tryObjectByName(companionObjectName)
    } yield new SimpleJdiProxy(context, objectReference)

    private def invokeDelegate: Option[Value] = for {
      companionObject <- companionObject
      extensionName = methodName + "$extension"
      newArgs = proxy +: args
      value <- context.tryInvokeUnboxed(companionObject, None, extensionName, newArgs)
    } yield value

    private def invokedBoxed: Option[Value] = for {
      className <- realThisType
      boxed <- tryNewInstance(className, Seq(proxy))
      res <- context.tryInvokeUnboxed(boxed, None, methodName, args)
    } yield res

    /** invoke delegate or box value and invoke method */
    override def apply(): Option[Value] = invokeDelegate orElse invokedBoxed
  }

}

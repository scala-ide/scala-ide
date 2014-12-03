/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.context

import scala.collection.JavaConversions._

import com.sun.jdi.ClassObjectReference
import com.sun.jdi.ClassType
import com.sun.jdi.ObjectReference

import javax.xml.bind.DatatypeConverter

/**
 * Part of `JdiContext` responsible for loading classes on debugged jvm.
 */
trait JdiClassLoader {
  self: JdiContext =>

  /**
   * Load class on debugged jvm.
   * Needed for JDI to work sometimes.
   */
  final def loadClass(name: String): Unit = {
    val classObj = jvm.classesByName("java.lang.Class").head.asInstanceOf[ClassType]
    val byName = classObj.methodsByName("forName").head
    val classMirror = jvm.mirrorOf(name)
    classObj.invokeMethod(currentThread, byName, List(classMirror), ObjectReference.INVOKE_SINGLE_THREADED)
  }

  /**
   * Load given class (bytes) on debugged JVM.
   * Class is sent as string encoded with base64.
   *
   * @param name name of loaded class
   * @param code bytes of class to load
   */
  final def loadClass(name: String, code: Array[Byte]): Unit = {

    // both vals create method java.lang.Class defineClass(code: byte[], off: Int, len: Int)
    val methodSignature = "([BII)Ljava/lang/Class;"
    val methodName = "defineClass"

    // obtain class loader from this class for top stackframe
    val classLoaderRef = currentFrame.thisObject.referenceType.classLoader
    val defineClassMethod = classLoaderRef.referenceType.methodsByName(methodName, methodSignature).head

    // encode with base64
    val localByteString = DatatypeConverter.printBase64Binary(code)

    // send to JDI
    val remoteByteStrings = jvm.mirrorOf(localByteString)

    val dateTypeConverterClazzReference = classByName("javax.xml.bind.DatatypeConverter")
    val parseMetod = dateTypeConverterClazzReference.methodsByName("parseBase64Binary").head

    // encoded
    val remoteByteArray = dateTypeConverterClazzReference.invokeMethod(currentThread, parseMetod, List(remoteByteStrings),
      ObjectReference.INVOKE_SINGLE_THREADED)

    // load the class
    classLoaderRef.invokeMethod(currentThread, defineClassMethod, List(remoteByteArray, jvm.mirrorOf(0), jvm.mirrorOf(code.length)),
      ObjectReference.INVOKE_SINGLE_THREADED).asInstanceOf[ClassObjectReference]
  }
}
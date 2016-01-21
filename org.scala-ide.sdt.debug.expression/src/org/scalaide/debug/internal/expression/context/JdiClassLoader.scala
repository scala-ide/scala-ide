/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package context

import scala.collection.JavaConversions._

import com.sun.jdi.ClassObjectReference

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
    val classObj = classByName("java.lang.Class")
    val byName = methodOn(classObj, "forName", arity = 1)
    val classMirror = jvm.mirrorOf(name)
    classObj.invokeMethod(currentThread, byName, List(classMirror))
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
    val parseMetod = methodOn(dateTypeConverterClazzReference, "parseBase64Binary", arity = 1)

    // encoded
    val remoteByteArray = dateTypeConverterClazzReference.invokeMethod(currentThread, parseMetod, List(remoteByteStrings))

    // load the class
    val args = List(remoteByteArray, jvm.mirrorOf(0), jvm.mirrorOf(code.length))
    classLoaderRef.invokeMethod(currentThread, defineClassMethod, args).asInstanceOf[ClassObjectReference]
  }
}

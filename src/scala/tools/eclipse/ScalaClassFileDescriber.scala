/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import java.io.{ DataInputStream, InputStream, IOException }

import scala.annotation.switch

import org.eclipse.core.runtime.QualifiedName
import org.eclipse.core.runtime.content.{ IContentDescriber, IContentDescription }

object ScalaClassFileDescriber {
  final val JAVA_MAGIC = 0xCAFEBABE
  final val CONSTANT_UTF8 = 1
  final val CONSTANT_UNICODE = 2
  final val CONSTANT_INTEGER = 3
  final val CONSTANT_FLOAT = 4
  final val CONSTANT_LONG = 5
  final val CONSTANT_DOUBLE = 6
  final val CONSTANT_CLASS = 7
  final val CONSTANT_STRING = 8
  final val CONSTANT_FIELDREF = 9
  final val CONSTANT_METHODREF = 10
  final val CONSTANT_INTFMETHODREF = 11
  final val CONSTANT_NAMEANDTYPE = 12

  def isScala(contents : InputStream) : Boolean = {
    try {
      val in = new DataInputStream(contents)
      
      if (in.readInt() != JAVA_MAGIC)
        return false
      if (in.skipBytes(4) != 4)
        return false
  
      val poolSize = in.readUnsignedShort
      var scalaSigIndex = -1
      var scalaIndex = -1
      var i = 1
      while (i < poolSize) {
        (in.readByte().toInt: @switch) match {
          case CONSTANT_UTF8 =>
            if (scalaIndex == -1 || scalaSigIndex == -1) {
              val str = in.readUTF()
              if (scalaIndex == -1 && str == "Scala")
                scalaIndex = i
              else if (scalaSigIndex == -1 && str == "ScalaSig")
                scalaSigIndex = i
            } else {
              val toSkip = in.readUnsignedShort()
              if (in.skipBytes(toSkip) != toSkip) return false
            }
          case CONSTANT_UTF8 | CONSTANT_UNICODE => 
            val toSkip = in.readUnsignedShort()
            if (in.skipBytes(toSkip) != toSkip) return false
          case CONSTANT_CLASS | CONSTANT_STRING =>
            if (in.skipBytes(2) != 2) return false
          case CONSTANT_FIELDREF | CONSTANT_METHODREF | CONSTANT_INTFMETHODREF
             | CONSTANT_NAMEANDTYPE | CONSTANT_INTEGER | CONSTANT_FLOAT =>
            if (in.skipBytes(4) != 4) return false 
          case CONSTANT_LONG | CONSTANT_DOUBLE =>
            if (in.skipBytes(8) != 8) return false
            i += 1
          case _ =>
            return false
        }
        i += 1
      }
      
      if (scalaIndex == -1 && scalaSigIndex == -1)
        return false
        
      if (in.skipBytes(6) != 6)
        return false
      
      val numInterfaces = in.readUnsignedShort()
      val iToSkip = numInterfaces*2
      if (in.skipBytes(iToSkip) != iToSkip)
        return false
      
      def skipFieldsOrMethods() {
        val num = in.readUnsignedShort()
        var i = 0
        while (i < num) {
          i += 1
          if (in.skipBytes(6) != 6)
            return false

          val numAttributes = in.readUnsignedShort()
          var j = 0
          while (j < numAttributes) {
            j += 1
            val attrNameIndex = in.readUnsignedShort()
            if (attrNameIndex == scalaIndex || attrNameIndex == scalaSigIndex)
              return true
            val numToSkip = in.readInt()
            if (in.skipBytes(numToSkip) != numToSkip)
              return false
          }
        }
      }
  
      skipFieldsOrMethods()      
      skipFieldsOrMethods()
      
      val numAttributes = in.readUnsignedShort()
      var j = 0
      while (j < numAttributes) {
        j += 1
        val attrNameIndex = in.readUnsignedShort()
        if (attrNameIndex == scalaIndex || attrNameIndex == scalaSigIndex)
          return true
        val numToSkip = in.readInt()
        if (in.skipBytes(numToSkip) != numToSkip)
          return false
      }
      false
    } catch {
      case ex : IOException => false
    }
  }
}

class ScalaClassFileDescriber extends IContentDescriber {
  import IContentDescriber.{ INVALID, VALID }
  import ScalaClassFileDescriber._

  override def describe(contents : InputStream, description : IContentDescription) : Int =
    if (isScala(contents)) VALID else INVALID

  override def getSupportedOptions : Array[QualifiedName] = new Array[QualifiedName](0)
}

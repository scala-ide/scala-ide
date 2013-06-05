/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import java.io.{ DataInputStream, InputStream, IOException }
import scala.annotation.switch
import scala.collection.mutable.HashMap
import org.eclipse.core.runtime.QualifiedName
import org.eclipse.core.runtime.content.{ IContentDescriber, IContentDescription }
import scala.tools.eclipse.logging.HasLogger

object ScalaClassFileDescriber extends HasLogger {
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

  def isScala(contents : InputStream) : Option[String] = {
    try {
      val in = new DataInputStream(contents)

      if (in.readInt() != JAVA_MAGIC)
        return None
      if (in.skipBytes(4) != 4)
        return None

      var sourceFile : String = null
      var isScala = false

      val pool = new HashMap[Int, String]

      val poolSize = in.readUnsignedShort
      var scalaSigIndex = -1
      var scalaIndex = -1
      var sourceFileIndex = -1
      var i = 1
      while (i < poolSize) {
        (in.readByte().toInt: @switch) match {
          case CONSTANT_UTF8 =>
            val str = in.readUTF()
            pool(i) = str
            if (scalaIndex == -1 || scalaSigIndex == -1 || sourceFileIndex == -1) {
              if (scalaIndex == -1 && str == "Scala")
                scalaIndex = i
              else if (scalaSigIndex == -1 && str == "ScalaSig")
                scalaSigIndex = i
              else if (sourceFileIndex == -1 && str == "SourceFile")
                sourceFileIndex = i
            }
          case CONSTANT_UNICODE =>
            val toSkip = in.readUnsignedShort()
            if (in.skipBytes(toSkip) != toSkip) return None
          case CONSTANT_CLASS | CONSTANT_STRING =>
            if (in.skipBytes(2) != 2) return None
          case CONSTANT_FIELDREF | CONSTANT_METHODREF | CONSTANT_INTFMETHODREF
             | CONSTANT_NAMEANDTYPE | CONSTANT_INTEGER | CONSTANT_FLOAT =>
            if (in.skipBytes(4) != 4) return None
          case CONSTANT_LONG | CONSTANT_DOUBLE =>
            if (in.skipBytes(8) != 8) return None
            i += 1
          case other =>
            logger.debug("Unknown constant pool id: " + other)
            return None
        }
        i += 1
      }

      if (scalaIndex == -1 && scalaSigIndex == -1)
        return None

      if (in.skipBytes(6) != 6)
        return None

      val numInterfaces = in.readUnsignedShort()
      val iToSkip = numInterfaces*2
      if (in.skipBytes(iToSkip) != iToSkip)
        return None

      def skipFieldsOrMethods() : Boolean = {
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
            isScala ||= (attrNameIndex == scalaIndex || attrNameIndex == scalaSigIndex)
            val numToSkip = in.readInt()
            if (in.skipBytes(numToSkip) != numToSkip)
              return false
          }
        }
        true
      }

      if (!skipFieldsOrMethods())
        return None
      if (!skipFieldsOrMethods())
        return None

      val numAttributes = in.readUnsignedShort()
      var j = 0
      while (j < numAttributes) {
        j += 1
        val attrNameIndex = in.readUnsignedShort()
        if (attrNameIndex == sourceFileIndex) {
          in.readInt()
          val index = in.readUnsignedShort()
          sourceFile = pool(index)
          if (isScala)
            return Some(sourceFile)
        } else {
          isScala ||= (attrNameIndex == scalaIndex || attrNameIndex == scalaSigIndex)
          if (isScala && sourceFile != null)
            return Some(sourceFile)
          val numToSkip = in.readInt()
          if (in.skipBytes(numToSkip) != numToSkip)
            return None
        }
      }
      None
    } catch {
      case ex : IOException => None
    }
  }
}

class ScalaClassFileDescriber extends IContentDescriber {
  import IContentDescriber.{ INVALID, VALID }
  import ScalaClassFileDescriber._

  override def describe(contents : InputStream, description : IContentDescription) : Int =
    if (isScala(contents).isDefined) VALID else INVALID

  override def getSupportedOptions : Array[QualifiedName] = new Array[QualifiedName](0)
}

package org.scalaide.debug.internal.classfile

import java.io.File
import scala.tools.nsc.io.AbstractFile
import scala.tools.nsc.symtab.classfile.AbstractFileReader

/** A one-off classfile parser used for testing.
 *
 *  Once this class is initialized, it can return the constant pool, methods and field
 *  information. Used for testing only.
 */
class ClassfileParser(classFile: File) {
  val file = AbstractFile.getFile(classFile)
  val reader = new AbstractFileReader(file)

  def parseHeader() {
    if (!(reader.nextInt == 0xCAFEBABE))
      throw new IllegalArgumentException("Invalid classfile: " + classFile.getName())
  }

  parseHeader()
  reader.skip(4) // major/minor version

  val count = reader.nextChar
  private val poolStartIndex = reader.bp

  val pool = new ConstantPool(reader.buf.slice(reader.bp, reader.buf.length), count)
  reader.skip(pool.lastPoolIndex)

  private val poolEndIndex = reader.bp

  def constantPoolBytes: Array[Byte] = reader.buf.slice(poolStartIndex, poolEndIndex)

  reader.skip(2) // access flags

  val name: String = pool.getClassRef(reader.nextChar)
  val superClass: String = pool.getClassRef(reader.nextChar)
  val interfaces: Seq[String] = parseInterfaces()
  val fields: Map[String, String] = parseFields()
  val methods: Map[String, MethodDef] = parseMethods()

  private def parseInterfaces(): Seq[String] = {
    val count = reader.nextChar
    for (i <- 0 until count) yield pool.getClassRef(reader.nextChar)
  }

  private def parseFields(): Map[String, String] = {
    val count = reader.nextChar
    (for (i <- 0 until count) yield {
      reader.skip(2) // access flags dropped
      val name = pool.getString(reader.nextChar)
      val descr = pool.getString(reader.nextChar)
      skipAttributes()
      name -> descr
    }).toMap
  }

  private def skipAttributes() {
    val attrCount = reader.nextChar
    for (i <- 0 until attrCount) {
      reader.skip(2); reader.skip(reader.nextInt)
    }
  }

  private def parseCodeAttribute(): Array[Byte] = {
    val attrCount = reader.nextChar
    var bytecode: Array[Byte] = null
    for (i <- 0 until attrCount) yield {
      val name = pool.getString(reader.nextChar)
      val attrLen = reader.nextInt
      val afterAttr = reader.bp + attrLen
      if (name == "Code") {
        reader.skip(2) // max_stack
        reader.skip(2) // max_locals
        val codeLen = reader.nextInt
        bytecode = reader.nextBytes(codeLen)
        // other things may follow, like exception handlers
      }
      reader.bp = afterAttr
    }
    bytecode
  }

  private def parseMethods(): Map[String, MethodDef] = {
    val count = reader.nextChar
    (for (i <- 0 until count) yield {
      reader.skip(2) // access flags dropped
      val name = pool.getString(reader.nextChar)
      val descr = pool.getString(reader.nextChar)

      name -> MethodDef(name, descr, parseCodeAttribute)
    }).toMap
  }
}

case class MethodDef(name: String, descriptor: String, bytecode: Array[Byte])

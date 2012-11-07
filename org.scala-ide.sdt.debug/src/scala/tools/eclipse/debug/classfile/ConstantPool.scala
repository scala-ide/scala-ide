package scala.tools.eclipse.debug.classfile

import java.io.File
import scala.tools.nsc.io.AbstractFile
import scala.tools.nsc.symtab.classfile.AbstractFileReader

/** A JVM constant pool. `bytes` are parsed when the object is instantiated,
 *  and then it's ready to retrieve values from the pool.
 *
 *  Because of the way in which the classfile format is defined, there is
 *  no way to use the pool without parsing it first (each entry may have a
 *  different length, so there is no way to compute the offset of an entry
 *  based on its index).
 *
 *  @param bytes The bytes constituting the constant pool
 *  @param length The number of constant pool entries. Any bytes not included in `length`
 *        are ignored
 *
 *  @see http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html
 */
class ConstantPool(bytes: Array[Byte], length: Int) {
  import ConstantPool._

  private val starts = new Array[Int](length)
  private var cpLastIndex = -1

  /** Return the number of entries in this constant pool */
  def size: Int = length

  // populate the constant pool
  init()

  def init() {
    var i = 1 // according to the JVM Spec, the constant pool index starts at 1!
    var idx = 0
    while (i < length) {
      starts(i) = idx
      i += 1

      bytes(idx) match {
        case CONSTANT_Utf8 =>
          idx += 3 + getU2(idx + 1)
        case CONSTANT_Class | CONSTANT_String | CONSTANT_MethodType =>
          idx += 3
        case CONSTANT_MethodHandle =>
          idx += 4
        case CONSTANT_Fieldref | CONSTANT_Methodref | CONSTANT_InterfaceMethodref
          | CONSTANT_NameAndType | CONSTANT_Integer | CONSTANT_Float | CONSTANT_InvokeDynamic =>
          idx += 5
        case CONSTANT_Long | CONSTANT_Double =>
          idx += 9
          i += 1 // long and double constants occupy *two* slots
        case _ =>
          throw IllegalConstantPoolEntry(i, bytes(idx), idx)
      }
    }
    cpLastIndex = idx
  }

  /** The index in `bytes` of the last byte in the constant pool. */
  def lastPoolIndex: Int = cpLastIndex

  /** Return a String from the constant pool. */
  def getString(i: Int): String = {
    val idx = starts(i)
    assert(bytes(idx) == CONSTANT_Utf8, bytes(idx))

    new String(bytes, idx + 3, getU2(idx + 1))
  }

  /** Return a method reference from  the constant pool. */
  def getMethodRef(i: Int): MethodRef = {
    val idx = starts(i)
    assert(bytes(idx) == CONSTANT_Methodref || bytes(idx) == CONSTANT_InterfaceMethodref, bytes(idx))

    val clsName = getClassRef(getU2(idx + 1))
    val (method, signature) = getNameAndType(getU2(idx + 3))

    MethodRef(clsName, method, signature)
  }

  /** Return a class reference from the constant pool. */
  def getClassRef(i: Int): String = {
    val idx = starts(i)
    assert(bytes(idx) == CONSTANT_Class, bytes(idx))

    getString(getU2(idx + 1))
  }

  /** Return a name and a type from the constant pool. */
  def getNameAndType(i: Int): (String, String) = {
    val idx = starts(i)
    assert(bytes(idx) == CONSTANT_NameAndType, bytes(idx))

    (getString(getU2(idx + 1)), getString(getU2(idx + 3)))
  }

  /** Return an unsigned 1-byte value as an Int. */
  private def getU1(idx: Int): Int = {
    bytes(idx) & 0xFF
  }

  /** Return an unsigned 2-byte value as a Char (they are unsigned on the JVM). */
  private def getU2(idx: Int): Char =
    ((getU1(idx) << 8) + getU1(idx + 1)).toChar
}

object ConstantPool {
  case class MethodRef(className: String, methodName: String, signature: String)

  case class IllegalConstantPoolEntry(idx: Int, value: Int, offset: Int)
    extends RuntimeException("Illegal constant pool entry at index %d, value %d, offset %d".format(idx, value, offset))

  // Taken from the JVM Spec
  final val CONSTANT_Class = 7
  final val CONSTANT_Fieldref = 9
  final val CONSTANT_Methodref = 10
  final val CONSTANT_InterfaceMethodref = 11
  final val CONSTANT_String = 8
  final val CONSTANT_Integer = 3
  final val CONSTANT_Float = 4
  final val CONSTANT_Long = 5
  final val CONSTANT_Double = 6
  final val CONSTANT_NameAndType = 12
  final val CONSTANT_Utf8 = 1
  final val CONSTANT_MethodHandle = 15
  final val CONSTANT_MethodType = 16
  final val CONSTANT_InvokeDynamic = 18

  /** Get the constant pool out of a Java class file. */
  def fromFile(classFile: File): ConstantPool = {
    val file = AbstractFile.getFile(classFile)
    val reader = new AbstractFileReader(file)

    def parseHeader() {
      if (!(reader.nextInt == 0xCAFEBABE))
        throw new IllegalArgumentException("Invalid classfile: " + classFile.getName())
    }

    parseHeader()
    reader.skip(4) // major/minor version
    val count = reader.nextChar
    new ConstantPool(reader.buf.slice(reader.bp, reader.buf.length), count)
  }
}

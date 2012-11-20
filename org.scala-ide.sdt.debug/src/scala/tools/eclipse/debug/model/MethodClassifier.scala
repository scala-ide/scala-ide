package scala.tools.eclipse.debug.model

import com.sun.jdi.Method
import scala.tools.eclipse.debug.classfile.ConstantPool
import scala.tools.eclipse.util.Utils._

object MethodClassifier extends Enumeration {
  // more classifications may be added, but for the moment these are the ones that make sense in the debugger
  val Synthetic, Getter, Setter, DefaultGetter, Bridge, TraitConstructor, Forwarder /*, DelayedInit, LazyCompute, LiftedException */ = Value
}

/** Classifies `scalac` synthetic methods according to their purpose.
 *
 *  May cache results, so do not reuse an instance of this class across debugging
 *  sessions (but *do* reuse it in the same session).
 *
 *  Note: Alternative designs have been explored. Making each flag in the enumeration
 *        a proper class does not play out because it would move detection strategies
 *        in the companion object, preventing caching (like the constant pool)
 *
 *  This class needs to be thread-safe.
 *
 *  TODO: Cache expensive operations (currently `Forwarder` is the most expensive).
 */
class MethodClassifier {

  import MethodClassifier._

  private val defaultR = """.*\$default\$\d+$""".r

  /** Is the given method of the `kind` type?   */
  def is(kind: MethodClassifier.Value, method: Method): Boolean = {
    kind match {
      case Synthetic =>
        method.isSynthetic()

      case Getter =>
        method.declaringType().fieldByName(method.name()) ne null

      case Setter =>
        val name = method.name()
        (name.endsWith("_$eq")
          && (method.declaringType().fieldByName(name.substring(0, name.length - 4)) ne null))

      case DefaultGetter =>
        defaultR.findFirstIn(method.name()).isDefined

      case Forwarder => debugTimed("Testing flag Forwarder for %s".format(method)) {
        (method.virtualMachine().canGetBytecodes()
          && method.virtualMachine().canGetConstantPool()
          && isForwarderBytecode(method))
      }

      case Bridge =>
        method.isBridge()

      case TraitConstructor =>
        method.name() == "$init$"
    }
  }

  /** Return all kinds that qualify this method. May be more than one (for instance, a method
   *  may be both `Synthetic` and `Bridge`, or `Synthetic` and `DefaultGetter`).
   */
  def allKindsOf(method: Method): Set[MethodClassifier.Value] = {
    MethodClassifier.values.filter(is(_, method))
  }

  /** TODO: cache the constant pool.
   */
  private[debug] def isForwarderBytecode(method: Method): Boolean = {
    val bytecode = method.bytecodes()
    val cpool = method.declaringType().constantPool()
    val cpoolSize = method.declaringType().constantPoolCount()

    isForwarderBytecode(bytecode, cpool, cpoolSize, method.name())
  }

  /** Check that the `bytecode` is a forwarder call.
   *
   *  Heuristic:
   *   - the bytecode ends with a `return` bytecode
   *   - `return` is immediately preceded by a static call to a method defined in a implementation class
   *     (name ends in `$class`)
   *   - the static method has the same name as the current method.
   *   - don't look at methods that are larger than a costant (MAX_CODE_SIZE). This saves parsing the constant
   *     pool when it's practically impossible to be a forwarder call.
   *
   *  This might get expensive: the constant pool has to be fully parsed in order to retrieve the name of
   *  the class and method that is called.
   *
   *  (private[debug] for testing).
   */
  private[debug] def isForwarderBytecode(bytecode: Array[Byte], cpool: Array[Byte], cpoolSize: Int, name: String): Boolean = {
    import JVMOpcodes._
    val MAX_CODE_SIZE = 50 // a method with 22 args is less than 50 bytes long in bytecode

    def forwarderSequence(bytes: Array[Byte]): Boolean = bytes match {
      case Array(`invoke_static`, idx1, idx2, ret) if returnOpcode(ret) =>
        val idx = ((idx1 & 0xFF) << 8) + (idx2 & 0xFF) // mask needed for making Byte unsigned
        val pool = new ConstantPool(cpool, cpoolSize)
        val ConstantPool.MethodRef(clsName, methodName, _) = pool.getMethodRef(idx)
        (clsName.endsWith("$class") && name == methodName)

      case _ =>
        false
    }

    ((bytecode.length < MAX_CODE_SIZE) // avoid the expensive constant pool check if the method is too long
      && (bytecode.length > 4)
      && forwarderSequence(bytecode.slice(bytecode.length - 4, bytecode.length)))
  }
}

/** A partial list of JVM opcodes that are useful for the method classifier */
object JVMOpcodes {
  // return instructions
  final val areturn = 0xB0.toByte
  final val dreturn = 0xAF.toByte
  final val freturn = 0xAE.toByte
  final val ireturn = 0xAC.toByte
  final val lreturn = 0xAD.toByte
  final val _return = 0xB1.toByte
  // invoke
  final val invoke_static = 0xB8.toByte

  final val returnOpcode = Set(areturn, dreturn, freturn, ireturn, lreturn, _return)
}

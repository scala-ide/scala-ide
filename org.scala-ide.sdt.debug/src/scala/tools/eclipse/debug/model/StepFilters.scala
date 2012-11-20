package scala.tools.eclipse.debug.model

import com.sun.jdi.Location
import scala.tools.eclipse.logging.HasLogger
import com.sun.jdi.ReferenceType
import com.sun.jdi.Method
import scala.tools.eclipse.debug.ScalaDebugPlugin
import scala.tools.eclipse.debug.preferences.DebuggerPreferences
import org.eclipse.core.internal.localstore.IsSynchronizedVisitor

/** Utility methods for deciding when a location should be filtered out from stepping into.
  * 
  * This class needs to be thread-safe.
  */
class StepFilters extends HasLogger {

  val classifier = new MethodClassifier

  private lazy val prefStore = ScalaDebugPlugin.plugin.getPreferenceStore()

  /** Return `true` if the debugger should never stop at this location (but may
   *  stop further down in the call-graph).
   *
   *  Examples are: synthetic, bridges, getters, setters.
   */
  private def isTransparentMethod(location: Location): Boolean = {
    (location.method.isBridge()
      || MethodClassifier.values.exists { flag =>
        prefStore.getBoolean(DebuggerPreferences.BASE_FILTER + flag.toString) && classifier.is(flag, location.method)
      })
  }

  /** Return true if it is a filtered location. */
  def isTransparentLocation(location: Location): Boolean = {
    val typeName = location.declaringType.name
    // TODO: use better pattern matching
    // TODO: check for bridge methods?
    if (typeName.startsWith("scala.collection")
      || typeName.startsWith("scala.runtime")
      || hiddenTypes(typeName)
      || isTransparentMethod(location))
      true
    else if (typeName.contains("$$anonfun$")) {
      !findAnonFunction(location.declaringType).exists(_ == location.method)
    } else
      false
  }

  /** Types that we should never step into, nor in anything called from their methods. */
  val hiddenTypes = Set("java.lang.ClassLoader",
    "scala.runtime.BoxesRunTime")

  /** Is this location opaque? Returns `true` for all locations in which the
   *  debugger should not stop. It won't stop in anything below this call either (any
   *  methods called by methods at this location).
   */
  def isOpaqueLocation(location: Location): Boolean =
    (location.method.isConstructor
      || hiddenTypes(location.declaringType.name))

  /** Return the method containing the actual code of the anon func, if it is contained
   *  in the given range, <code>None</code> otherwise.
   */
  def anonFunctionsInRange(refType: ReferenceType, range: Range): Option[Method] = {
    findAnonFunction(refType).filter(method => range.contains(method.location.lineNumber))
  }

  /** Return the method containing the actual code of the anon func.
   *  Return <code>None</code> if no method can be identified has being it.
   */
  def findAnonFunction(refType: ReferenceType): Option[Method] = {
    // TODO: check super type at some point
    import scala.collection.JavaConverters._
    val methods = refType.methods.asScala.filter(method => !method.isBridge && method.name.startsWith("apply"))

    methods.size match {
      case 1 =>
        // one non bridge apply method, just use it
        methods.headOption
      case 2 =>
        // this is more complex.
        // the compiler may have 'forgotten' to flag the 'apply' as a bridge method,
        // or both the 'apply' and the 'apply$__$sp' contains the actual code

        // if the 'apply' and the 'apply$__$sp' contains the same code, we are in the optimization case, the 'apply' method
        // will be used, otherwise, the 'apply$__$sp" will be used.
        val applyMethod = methods.find(_.name == "apply")
        val applySpMethod = methods.find(_.name.startsWith("apply$"))
        (applyMethod, applySpMethod) match {
          case (Some(m1), Some(m2)) if sameBytecode(m1, m2) => applyMethod
          case (Some(_), Some(_))                           => applySpMethod
          case (Some(_), None)                              => applyMethod
          case (None, _)                                    => applySpMethod
        }
      case _ =>
        // doesn't contain apply methods, so it is not an anonFunction
        None
    }
  }

  private def sameBytecode(m1: Method, m2: Method): Boolean = m1.bytecodes.sameElements(m2.bytecodes)

}
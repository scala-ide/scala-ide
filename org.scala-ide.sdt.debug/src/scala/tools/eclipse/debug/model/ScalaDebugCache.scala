package scala.tools.eclipse.debug.model

import scala.actors.Actor
import scala.collection.JavaConverters.asScalaBufferConverter
import scala.tools.eclipse.debug.BaseDebuggerActor
import scala.tools.eclipse.debug.BaseDebuggerActor._
import scala.tools.eclipse.debug.PoisonPill
import scala.tools.eclipse.debug.ScalaDebugPlugin
import scala.tools.eclipse.debug.preferences.DebuggerPreferences
import scala.tools.eclipse.logging.HasLogger

import com.sun.jdi.Location
import com.sun.jdi.Method
import com.sun.jdi.ReferenceType
import com.sun.jdi.event.ClassPrepareEvent

object ScalaDebugCache {
  private final val OuterTypeNameRegex = """([^\$]*)(\$.*)?""".r

  /** Types that we should never step into, nor in anything called from their methods. */
  private final val HiddenTypes = Set(
    "java.lang.ClassLoader",
    "scala.runtime.BoxesRunTime")

  /** Return the the name of the lowest (outer) type containing the type with the given name (everything before the first '$').
   */
  private[model] def extractOuterTypeName(typeName: String) = typeName match {
    case OuterTypeNameRegex(outerTypeName, nestedTypeName) =>
      outerTypeName
  }

  private lazy val prefStore = ScalaDebugPlugin.plugin.getPreferenceStore()
		  
  def apply(debugTarget: ScalaDebugTarget, scalaDebugTargetActor: BaseDebuggerActor): ScalaDebugCache = {
    val debugCache = new ScalaDebugCache(debugTarget) {
      val actor = new ScalaDebugCacheActor(this, debugTarget, scalaDebugTargetActor)
    }
    debugCache.actor.start()
    debugCache
  }

}

/** A cache used to keep the list of nested classes of a outer class.
 *  It is used by for the line breakpoints and step-over.
 *
 *  Most of the methods are synchronous calls made to the underlying actor.
 */
abstract class ScalaDebugCache(val debugTarget: ScalaDebugTarget) extends HasLogger {
  import ScalaDebugCache._

  @volatile
  private[debug] var running = false

  private[debug] val actor: ScalaDebugCacheActor

  /** Return the list of type which are nested under the same outer type as the type with the given name,
   *  and which are currently loaded in the debugged VM.
   */
  def getLoadedNestedTypes(typeName: String): Set[ReferenceType] = {
    val outerTypeName = extractOuterTypeName(typeName)
    syncSend(actor, LoadedNestedTypes(outerTypeName)) match {
      case Some(LoadedNestedTypesAnswer(types)) =>
        types
      case None =>
        logger.info("TIMEOUT waiting for debug cache actor in getLoadedNestedTypes")
        Set()
      case unknown =>
        logger.error("Unknown return value to LoadedNestedTypes message: %s".format(unknown))
        Set()
    }
  }

  /** Adds the given actor as a listener for class prepare events in the debugged VM,
   *  for types which are nested under the same outer type as the type withe the given name.
   *  The event is sent as a ClassPrepareEvent to the actor.
   *
   *  This method is synchronous. Once this method returns, all subsequent ClassPrepareEvents
   *  will be sent to the listener actor.
   *
   *  Does nothing if the actor has already been added for the same outer type.
   */
  def addClassPrepareEventListener(listener: Actor, typeName: String) {
    syncSend(actor, AddClassPrepareEventListener(listener, extractOuterTypeName(typeName)))
  }

  /** Removes the given actor as being a listener for class prepare events in the debugged VM,
   *  for types which are nested under the same outer type as the type with the given name.
   *
   *  Does nothing if the actor was not registered as a listener for the outer type of the type with the given name.
   *
   *  This method is asynchronous. There might be residual ClassPrepareEvents being
   *  sent to this listener.
   */
  def removeClassPrepareEventListener(listener: Actor, typeName: String) {
    actor ! RemoveClassPrepareEventListener(listener, extractOuterTypeName(typeName))
  }

  private var typeCache = Map[ReferenceType, TypeCache]()
  private val typeCacheLock = new Object()

  /** Return the method containing the actual code of the anon function, if it is contained
   *  in the given range, <code>None</code> otherwise.
   */
  def getAnonFunctionsInRange(refType: ReferenceType, range: Range): Option[Method] = {
    getCachedAnonFunction(refType).filter(method => range.contains(method.location.lineNumber))
  }
 
  /** Return the method containing the actual code of the anon function.
   */
  def getAnonFunction(refType: ReferenceType): Option[Method] = {
    getCachedAnonFunction(refType)
  }

  /** Return true if it is a filtered location. */
  def isTransparentLocation(location: Location): Boolean = {
    getCachedMethodFlags(location.method()).isTransparent
  }

  /** Is this location opaque? Returns `true` for all locations in which the
   *  debugger should not stop. It won't stop in anything below this call either (any
   *  methods called by methods at this location).
   */
  def isOpaqueLocation(location: Location): Boolean = {
    getCachedMethodFlags(location.method()).isOpaque
  }

  /** Returns the anon function for the given type, if it exists. The cache is checked
   *  before doing the actual search.
   */
  private def getCachedAnonFunction(refType: ReferenceType): Option[Method] = {
    typeCacheLock synchronized {
      typeCache get refType match {
        case None =>
          val anonFunction = findAnonFunction(refType)
          typeCache = typeCache + ((refType, TypeCache(Some(anonFunction), Map())))
          anonFunction
        case Some(TypeCache(Some(cachedMethod), _)) =>
          cachedMethod
        case Some(cache @ TypeCache(None, _)) =>
          val anonFunction = findAnonFunction(refType)
          typeCache = typeCache + ((refType, cache.copy(anonMethod = Some(anonFunction))))
          anonFunction
      }
    }
  }

  /** Returns the anon function for the given type, if it exists.
   */
  private def findAnonFunction(refType: ReferenceType): Option[Method] = {
    val allMethods = refType.methods

    import scala.collection.JavaConverters._
    // TODO: check super type at some point
    val methods = allMethods.asScala.filter(method => !method.isBridge && method.name.startsWith("apply"))

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
          case (Some(_), Some(_)) => applySpMethod
          case (Some(_), None) => applyMethod
          case (None, _) => applySpMethod
        }
      case _ =>
        // doesn't contain apply methods, so it is not an anonFunction
        None
    }
  }

  /** Returns the flags for the given method. The cache is checked
   *  before doing the actual computation.
   */
  private def getCachedMethodFlags(method: Method): MethodFlags = {
    typeCacheLock synchronized {
      val refType = method.declaringType()
      typeCache get refType match {
        case None =>
          val methodFlags = createMethodFlags(method)
          typeCache = typeCache + ((refType, TypeCache(None, Map((method, methodFlags)))))
          methodFlags
        case Some(cache) =>
          cache.methods get method match {
            case None =>
              val methodFlags = createMethodFlags(method)
              typeCache = typeCache + ((refType, cache.copy(methods = cache.methods + ((method, methodFlags)))))
              methodFlags
            case Some(methodFlags) =>
              methodFlags
          }
      }
    }
  }

  /** Create the flags for the given method.
   */
  private def createMethodFlags(method: Method): MethodFlags = {
    val typeName = method.declaringType.name
    val hidden = HiddenTypes(typeName)

    // TODO: use better pattern matching
    val transparentMethod = (hidden
      || typeName.startsWith("scala.collection")
      || typeName.startsWith("scala.runtime")
      || method.isBridge()
      || MethodClassifier.values.exists { flag => prefStore.getBoolean(DebuggerPreferences.BASE_FILTER + flag.toString) && MethodClassifier.is(flag, method) }
      || (typeName.contains("$$anonfun$")) && !getCachedAnonFunction(method.declaringType).exists(_ == method))

    val opaqueMethod = hidden || method.isConstructor()

    MethodFlags(transparentMethod, opaqueMethod)
  }

  private def sameBytecode(m1: Method, m2: Method): Boolean = m1.bytecodes.sameElements(m2.bytecodes)

  def dispose() {
    actor ! PoisonPill
  }

}

private[model] case class LoadedNestedTypes(outerTypeName: String)
private[model] case class LoadedNestedTypesAnswer(types: Set[ReferenceType])
private[model] case class AddClassPrepareEventListener(actor: Actor, outerTypeName: String)
private[model] case class RemoveClassPrepareEventListener(actor: Actor, outerTypeName: String)

protected[debug] class ScalaDebugCacheActor(debugCache: ScalaDebugCache, debugTarget: ScalaDebugTarget, scalaDebugTargetActor: BaseDebuggerActor) extends BaseDebuggerActor with HasLogger {

  private var nestedTypesCache = Map[String, NestedTypesCache]()

  override protected def behavior: Behavior = {
    case e: ClassPrepareEvent =>
      classLoaded(e)
      reply(false)
    case LoadedNestedTypes(outerTypeName) =>
      reply(LoadedNestedTypesAnswer(getLoadedNestedTypes(outerTypeName)))
    case AddClassPrepareEventListener(actor, outerTypeName) =>
      addClassPreparedEventListener(actor, outerTypeName)
      reply(true)
    case RemoveClassPrepareEventListener(actor, outerTypeName) =>
      removeClassPreparedEventListener(actor, outerTypeName)
      reply(true)
  }

  override protected def postStart() {
    link(scalaDebugTargetActor)
    debugCache.running = true
  }

  private def classLoaded(event: ClassPrepareEvent) {
    val refType = event.referenceType()
    val topLevelTypeName = ScalaDebugCache.extractOuterTypeName(refType.name())
    nestedTypesCache.get(topLevelTypeName) match {
      case Some(cache) =>
        // store the new type
        nestedTypesCache = nestedTypesCache + ((topLevelTypeName, cache.copy(types = cache.types + refType)))
        // dispatch to listeners
        cache.listeners.foreach { a => 
          if (syncSend(a, event).isEmpty)
            logger.info("TIMOUT waiting for the listener actor in `classLoaded`")
        }
      case None =>
        logger.warn("Received ClassPrepareEvent for not expected type: %s".format(refType.name()))
    }
  }

  private def getLoadedNestedTypes(outerTypeName: String): Set[ReferenceType] = {
    nestedTypesCache.get(outerTypeName) match {
      case Some(cache) =>
        cache.types
      case None =>
        initializedRequestsAndCache(outerTypeName).types
    }
  }

  private def initializedRequestsAndCache(outerTypeName: String): NestedTypesCache = {
    val simpleRequest = JdiRequestFactory.createClassPrepareRequest(outerTypeName, debugTarget)
    val patternRequest = JdiRequestFactory.createClassPrepareRequest(outerTypeName + "$*", debugTarget)
    debugTarget.eventDispatcher.setActorFor(ScalaDebugCacheActor.this, simpleRequest)
    debugTarget.eventDispatcher.setActorFor(ScalaDebugCacheActor.this, patternRequest)
    simpleRequest.enable()
    patternRequest.enable()

    // define the filter to find the type and its nested types,
    // trying to minimize string operations (startsWith has shortcut if 'name' is too short)
    val nameLength = outerTypeName.length()
    val nestedTypeFilter = (refType: ReferenceType) => {
      val name = refType.name
      name.startsWith(outerTypeName) && (name.length == nameLength || name.charAt(nameLength) == '$')
    }

    import scala.collection.JavaConverters._
    val types = debugTarget.virtualMachine.allClasses().asScala.filter(nestedTypeFilter).toSet

    val cache = new NestedTypesCache(types, Set())
    nestedTypesCache = nestedTypesCache + ((outerTypeName, cache))
    cache
  }

  private def addClassPreparedEventListener(listener: Actor, outerTypeName: String) {
    val cache = nestedTypesCache.get(outerTypeName) match {
      case Some(cache) =>
        cache
      case None =>
        initializedRequestsAndCache(outerTypeName)
    }
    nestedTypesCache = nestedTypesCache + ((outerTypeName, cache.copy(listeners = cache.listeners + listener)))
  }

  private def removeClassPreparedEventListener(listener: Actor, outerTypeName: String) {
    val cache = nestedTypesCache.get(outerTypeName) match {
      case Some(cache) =>
        nestedTypesCache = nestedTypesCache + ((outerTypeName, cache.copy(listeners = cache.listeners - listener)))
      case None =>
    }
  }

  override protected def preExit() {
    // no need to disable the requests. This actor is shutdown only when the debug session is shut down
    unlink(scalaDebugTargetActor)
    debugCache.running = false
  }

}

case class NestedTypesCache(types: Set[ReferenceType], listeners: Set[Actor])

case class TypeCache(anonMethod: Option[Option[Method]] = None, methods: Map[Method, MethodFlags])
case class MethodFlags(isTransparent: Boolean, isOpaque: Boolean)

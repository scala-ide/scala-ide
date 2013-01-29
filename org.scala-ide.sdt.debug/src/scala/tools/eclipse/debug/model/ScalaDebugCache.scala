package scala.tools.eclipse.debug.model

import com.sun.jdi.ReferenceType
import scala.actors.Actor
import scala.tools.eclipse.debug.BaseDebuggerActor
import com.sun.jdi.event.ClassPrepareEvent
import scala.tools.eclipse.logging.HasLogger
import com.sun.jdi.request.ClassPrepareRequest
import scala.tools.eclipse.debug.PoisonPill

object ScalaDebugCache {

  private final val outerTypeNameRegex = """([^\$]*)(\$.*)?""".r

  /** Return the the name of the lowest (outer) type containing the type with the given name (everything before the first '$').
   */
  private[model] def extractOuterTypeName(typeName: String) = typeName match {
    case outerTypeNameRegex(outerTypeName, nestedTypeName) =>
      outerTypeName
  }

  def apply(debugTarget: ScalaDebugTarget, scalaDebugTargetActor: BaseDebuggerActor): ScalaDebugCache = {
    val debugCache = new ScalaDebugCache(debugTarget) {
      val actor = new ScalaDebugCacheActor(this, debugTarget, scalaDebugTargetActor)
    }
    debugCache.actor.start
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
    actor !? LoadedNestedTypes(outerTypeName) match {
      case LoadedNestedTypesAnswer(types) =>
        types
      case unknown =>
        logger.error("Unknown return value to LoadedNestedTypes message: %s".format(unknown))
        Set()
    }
  }

  /** Adds the given actor as a listener for class prepare events in the debugged VM,
   *  for types which are nested under the same outer type as the type withe the given name.
   *  The event is sent as a ClassPrepareEvent to the actor.
   *  
   *  Does nothing if the actor has already been added for the same outer type.
   */
  def addClassPrepareEventListener(listener: Actor, typeName: String) {
    actor !? AddClassPrepareEventListener(listener, extractOuterTypeName(typeName))
  }

  /** Removes the given actor as being a listener for class prepare events in the debugged VM,
   *  for types which are nested under the same outer type as the type with the given name.
   *  
   *  Does nothing if the actor was not registered as a listener for the outer type of the type with the given name.
   */
  def removeClassPrepareEventListener(listener: Actor, typeName: String) {
    actor !? RemoveClassPrepareEventListener(listener, extractOuterTypeName(typeName))
  }

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
      registerClassPreparedEventListener(actor, outerTypeName)
      reply(true)
    case RemoveClassPrepareEventListener(actor, outerTypeName) =>
      deregisterClassPreparedEventListener(actor, outerTypeName)
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
        cache.listeners.foreach {
          a => a !? event
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

  private def registerClassPreparedEventListener(listener: Actor, outerTypeName: String) {
    val cache = nestedTypesCache.get(outerTypeName) match {
      case Some(cache) =>
        cache
      case None =>
        initializedRequestsAndCache(outerTypeName)
    }
    nestedTypesCache = nestedTypesCache + ((outerTypeName, cache.copy(listeners = cache.listeners + listener)))
  }

  private def deregisterClassPreparedEventListener(listener: Actor, outerTypeName: String) {
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

package scala.tools.eclipse.debug.model

import com.sun.jdi.ReferenceType
import scala.actors.Actor
import scala.tools.eclipse.debug.BaseDebuggerActor
import com.sun.jdi.event.ClassPrepareEvent
import scala.tools.eclipse.logging.HasLogger
import com.sun.jdi.request.ClassPrepareRequest
import scala.tools.eclipse.debug.PoisonPill

object ScalaDebugCache {

  final val topLevelNameRegex = """([^\$]*)(\$.*)?""".r

  def extractTopLevelTypeName(typeName: String) = typeName match {
    case topLevelNameRegex(topLevelTypeName, innerName) =>
      topLevelTypeName
  }

  def apply(debugTarget: ScalaDebugTarget, scalaDebugTargetActor: BaseDebuggerActor): ScalaDebugCache = {
    val debugCache = new ScalaDebugCache(debugTarget) {
      val actor = new ScalaDebugCacheActor(this, debugTarget, scalaDebugTargetActor)
    }
    debugCache.actor.start
    debugCache
  }

}

/** A cache used to keep the list of nested classes of a top level class.
 *  It is used by for the line breakpoints and step-over.
 *  
 *  Most of the methods are synchronous calls made to the underlying actor.
 */
abstract class ScalaDebugCache(val debugTarget: ScalaDebugTarget) extends HasLogger {

  @volatile
  private[debug] var running = false

  private[debug] val actor: ScalaDebugCacheActor

  def getLoadedNestedTypes(topLevelTypeName: String): Set[ReferenceType] = {
    actor !? LoadedNestedTypes(topLevelTypeName) match {
      case LoadedNestedTypesAnswer(types) =>
        types
      case unknown =>
        logger.error("Unknown return value to LoadedNestedTypes message: %s".format(unknown))
        Set()
    }
  }

  def registerClassPrepareEventListener(listener: Actor, topLevelTypeName: String) {
    actor !? RegisterClassPrepareEventListener(listener, topLevelTypeName)
  }

  def deregisterClassPrepareEventListener(listener: Actor, topLevelTypeName: String) {
    actor !? DeregisterClassPrepareEventListener(listener, topLevelTypeName)
  }

  def dispose() {
    actor ! PoisonPill
  }

}

case class LoadedNestedTypes(topLevelTypeName: String)
case class LoadedNestedTypesAnswer(types: Set[ReferenceType])
case class RegisterClassPrepareEventListener(actor: Actor, topLevelTypeName: String)
case class DeregisterClassPrepareEventListener(actor: Actor, topLevelTypeName: String)

protected[debug] class ScalaDebugCacheActor(debugCache: ScalaDebugCache, debugTarget: ScalaDebugTarget, scalaDebugTargetActor: BaseDebuggerActor) extends BaseDebuggerActor with HasLogger {

  val nestedTypesCache = scala.collection.mutable.HashMap[String, NestedTypesCache]()

  override protected def behavior: Behavior = {
    case e: ClassPrepareEvent =>
      classLoaded(e)
      reply(false)
    case LoadedNestedTypes(topLevelTypeName) =>
      reply(LoadedNestedTypesAnswer(getLoadedNestedTypes(topLevelTypeName)))
    case RegisterClassPrepareEventListener(actor, topLevelTypeName) =>
      registerClassPreparedEventListener(actor, topLevelTypeName)
      reply(true)
    case DeregisterClassPrepareEventListener(actor, topLevelTypeName) =>
      deregisterClassPreparedEventListener(actor, topLevelTypeName)
      reply(true)
  }

  override protected def postStart() {
    link(scalaDebugTargetActor)
    debugCache.running = true
  }

  private def classLoaded(event: ClassPrepareEvent) {
    val refType = event.referenceType()
    val topLevelTypeName = ScalaDebugCache.extractTopLevelTypeName(refType.name())
    nestedTypesCache.get(topLevelTypeName) match {
      case Some(cache) =>
        // store the new type
        cache.types = cache.types + refType
        // dispatch to listeners
        cache.listeners.foreach {
          a => a !? event
        }
      case None =>
        logger.warn("Received ClassPrepareEvent for not expected type: %s".format(refType.name()))
    }
  }

  private def getLoadedNestedTypes(topLevelTypeName: String): Set[ReferenceType] = {
    nestedTypesCache.get(topLevelTypeName) match {
      case Some(cache) =>
        cache.types
      case None =>
        initializedRequestsAndCache(topLevelTypeName).types
    }
  }

  private def initializedRequestsAndCache(topLevelTypeName: String): NestedTypesCache = {
    val simpleRequest = JdiRequestFactory.createClassPrepareRequest(topLevelTypeName, debugTarget)
    val patternRequest = JdiRequestFactory.createClassPrepareRequest(topLevelTypeName + "$*", debugTarget)
    debugTarget.eventDispatcher.setActorFor(ScalaDebugCacheActor.this, simpleRequest)
    debugTarget.eventDispatcher.setActorFor(ScalaDebugCacheActor.this, patternRequest)
    simpleRequest.enable()
    patternRequest.enable()

    // define the filter to find the type and its nested types,
    // trying to minimize string operations (startsWith has shortcut if 'name' is too short)
    val nameLength = topLevelTypeName.length()
    val nestedTypeFilter = (refType: ReferenceType) => {
      val name = refType.name
      name.startsWith(topLevelTypeName) && (name.length == nameLength || name.charAt(nameLength) == '$')
    }
    
    import scala.collection.JavaConverters._
    val types = debugTarget.virtualMachine.allClasses().asScala.filter(nestedTypeFilter).toSet
    
    val cache = new NestedTypesCache(List(simpleRequest, patternRequest), types, Set())
    nestedTypesCache += topLevelTypeName -> cache
    cache
  }

  private def registerClassPreparedEventListener(actor: Actor, topLevelTypeName: String) {
    val cache = nestedTypesCache.get(topLevelTypeName) match {
      case Some(cache) =>
        cache
      case None =>
        initializedRequestsAndCache(topLevelTypeName)
    }
    cache.listeners = cache.listeners + actor
  }

  private def deregisterClassPreparedEventListener(actor: Actor, topLevelTypeName: String) {
    val cache = nestedTypesCache.get(topLevelTypeName) match {
      case Some(cache) =>
        cache.listeners = cache.listeners - actor
      case None =>
    }
  }

  override protected def preExit() {
    // no need to disable the request. This actor is shutdown only when the debug session is shut down
    unlink(scalaDebugTargetActor)
    debugCache.running = false
  }

}

class NestedTypesCache(val requests: List[ClassPrepareRequest], @volatile var types: Set[ReferenceType], @volatile var listeners: Set[Actor])

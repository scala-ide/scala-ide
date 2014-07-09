package org.scalaide.debug.internal.model

import org.scalaide.debug.internal.ScalaDebugPlugin
import org.scalaide.debug.internal.ScalaDebugger
import org.scalaide.logging.HasLogger

import org.eclipse.debug.core.ILogicalStructureProvider
import org.eclipse.debug.core.ILogicalStructureType
import org.eclipse.debug.core.model.IValue

import com.sun.jdi.ClassType

import org.scalaide.debug.internal.JDIUtil._

class ScalaLogicalStructureProvider extends ILogicalStructureProvider {

  override def getLogicalStructureTypes(value: IValue): Array[ILogicalStructureType] = {
    value match {
      case objectReference: ScalaObjectReference =>
        if (isScalaCollection(objectReference)) {
          Array(ScalaCollectionLogicalStructureType)
        } else AkkaActorLogicalStructure.enclosingActor(objectReference) match {
          case Some(actorReference) => Array(AkkaActorLogicalStructure)
          case _                    => Array()
        }
      case _ =>
        Array() // TODO: return fixed empty Array
    }
  }

  private def isScalaCollection(objectReference: ScalaObjectReference): Boolean = {
    objectReference.wrapJDIException("Exception while checking if passed object reference is a scala collection type") {
      objectReference.referenceType match {
        case classType: ClassType =>
          implements(classType, "scala.collection.TraversableOnce")
        case _ => // TODO: ScalaObjectReference should always reference objects of class type, never of array type. Can we just cast?
          false
      }
    }
  }
}

object ScalaCollectionLogicalStructureType extends ILogicalStructureType with HasLogger {

  // Members declared in org.eclipse.debug.core.ILogicalStructureType

  override def getDescription(): String = "Flat the Scala collections"

  override val getId: String = ScalaDebugPlugin.id + ".logicalstructure.collection"

  // Members declared in org.eclipse.debug.core.model.ILogicalStructureTypeDelegate

  override def getLogicalStructure(value: IValue): IValue = {

    val scalaValue = value.asInstanceOf[ScalaObjectReference]

    val thread = ScalaDebugger.currentThread
    try {
      // the way to call toArray on a collection is slightly different between Scala 2.9 and 2.10
      // the base object to use to get the Manisfest and the method signature are different
      val (manifestObject, toArraySignature) = if (scalaValue.getDebugTarget.is2_10Compatible(thread)) {
        (scalaValue.getDebugTarget().objectByName("scala.reflect.ClassManifestFactory", false, null), "(Lscala/reflect/ClassTag;)Ljava/lang/Object;")
      } else {
        (scalaValue.getDebugTarget().objectByName("scala.reflect.Manifest", false, null), "(Lscala/reflect/ClassManifest;)Ljava/lang/Object;")
      }

      // get Manifest.Any, needed to call toArray(..)
      val anyManifestObject = manifestObject.invokeMethod("Any", thread) match {
        case o: ScalaObjectReference =>
          o
        case _ =>
          // in case something changes in the next versions of Scala
          throw new Exception("Unexpected return value for Manifest.Any()")
      }

      scalaValue.invokeMethod("toArray", toArraySignature, thread, anyManifestObject)
    } catch {
      case e: Exception =>
        // fail gracefully in case of problem
        logger.debug("Failed to compute logical structure for '%s'".format(scalaValue), e)
        scalaValue
    }
  }

  override def providesLogicalStructure(value: IValue): Boolean = true // TODO: check that as it is created by the provider, it is never used with other values

  // Members declared in org.eclipse.debug.core.model.ILogicalStructureTypeDelegate2

  override def getDescription(value: IValue): String = getDescription
}

object AkkaActorLogicalStructure extends ILogicalStructureType with HasLogger {
  def getDescription(): String =
    "Actor logical structure"

  def getDescription(v: IValue): String =
    getDescription

  def getId(): String =
    ScalaDebugPlugin.id + "logicalstructure.actor"

  def getLogicalStructure(obj: IValue): IValue = obj match {
    case ref: ScalaObjectReference =>
      val Some(actor) = enclosingActor(ref)
      implicit val target = actor.getDebugTarget()
      val actorContext = actor.invokeMethod("context", ScalaDebugger.currentThread).asInstanceOf[ScalaObjectReference]
      val sender = actor.invokeMethod("sender", ScalaDebugger.currentThread)
      val parent = actorContext.invokeMethod("parent", "()Lakka/actor/ActorRef;", ScalaDebugger.currentThread)
      val supervisingStrategy = actor.invokeMethod("supervisorStrategy", ScalaDebugger.currentThread)

      VirtualValue("Actor", actor.invokeMethod("self", ScalaDebugger.currentThread).getValueString())
        .withFields(
          VirtualVariable("<parent>", parent.getReferenceTypeName(), parent),
          VirtualVariable("<sender>", sender.getReferenceTypeName(), sender),
          VirtualVariable("<supervisorStrategy>", supervisingStrategy.getReferenceTypeName(), supervisingStrategy))
        .withFields(
          ref.getVariables(): _*)
    case _ =>
      obj
  }

  def enclosingActor(obj: ScalaObjectReference): Option[ScalaObjectReference] = {
    obj.wrapJDIException("Exception while computing logical structures for actor") {
      def walkOuterPath(obj: ScalaObjectReference): Option[ScalaObjectReference] = {
        if (implements(obj.classType, "akka.actor.Actor")) Some(obj)
        else (for {
          _ <- Option(obj.referenceType().fieldByName("$outer"))
          outer = obj.fieldValue("$outer").asInstanceOf[ScalaObjectReference]
        } yield walkOuterPath(outer)).flatten
      }

      walkOuterPath(obj)
    }
  }

  def providesLogicalStructure(v: IValue): Boolean = !v.isInstanceOf[VirtualValue]
}
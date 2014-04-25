/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.model

import scala.collection.JavaConverters.asScalaBufferConverter

import org.eclipse.debug.core.ILogicalStructureProvider
import org.eclipse.debug.core.ILogicalStructureType
import org.eclipse.debug.core.model.IValue
import org.scalaide.debug.internal.ScalaDebugPlugin
import org.scalaide.debug.internal.ScalaDebugger
import org.scalaide.logging.HasLogger

import com.sun.jdi.BooleanValue
import com.sun.jdi.ClassType
import com.sun.jdi.ReferenceType

class ScalaLogicalStructureProvider extends ILogicalStructureProvider {

  override def getLogicalStructureTypes(value: IValue): Array[ILogicalStructureType] = {
    value match {
      case objectReference: ScalaObjectReference if ScalaLogicalStructureProvider.isScalaCollection(objectReference) =>
        Array(ScalaCollectionLogicalStructureType)
      case _ =>
        ScalaLogicalStructureProvider.emptyLogicalStructureTypes
    }
  }

}

object ScalaLogicalStructureProvider extends HasLogger {

  private lazy val emptyLogicalStructureTypes: Array[ILogicalStructureType] = Array.empty

  def isScalaCollection(objectReference: ScalaObjectReference): Boolean =
    objectReference.wrapJDIException("Exception while checking if passed object reference is a Scala collection type") {
      checkIfImplements(objectReference.referenceType(), "scala.collection.TraversableOnce")
    }

  def isTraversableLike(objectReference: ScalaObjectReference): Boolean =
    objectReference.wrapJDIException("Exception while checking if passed object reference is TraversableLike") {
      checkIfImplements(objectReference.referenceType(), "scala.collection.TraversableLike")
    }

  /**
   * Checks 'implements' with Java meaning
   */
  def implements(classType: ClassType, interfaceName: String): Boolean = {
    import scala.collection.JavaConverters._
    classType.allInterfaces.asScala.exists(_.name == interfaceName)
  }

  private def checkIfImplements(refType: ReferenceType, interfaceName: String) = refType match {
    case classType: ClassType =>
      implements(classType, interfaceName)
    case _ => // TODO: ScalaObjectReference should always reference objects of class type, never of array type. Can we just cast?
      false
  }

  // All these methods, when using default ScalaDebugger.currentThread, won't work in ExpressionEvaluator's tree view,
  // if user will switch debugged thread. It's problem that thread cannot be just cached (JDI limitation).
  // Right now we only inform user that there's problem with chosen thread and he has to change it to this proper one
  // to be able to call these operations correctly.
  def hasDefiniteSize(collectionRef: ScalaObjectReference, thread: ScalaThread = ScalaDebugger.currentThread): Boolean =
    collectionRef.wrapJDIException("Exception while checking if collection has definite size") {
      collectionRef.invokeMethod("hasDefiniteSize", "()Z", thread)
        .asInstanceOf[ScalaPrimitiveValue].underlying
        .asInstanceOf[BooleanValue]
        .value()
    }

  def callIsEmpty(collectionRef: ScalaObjectReference, thread: ScalaThread = ScalaDebugger.currentThread): Boolean =
    collectionRef.wrapJDIException("Exception while checking if collection is empty") {
      collectionRef.invokeMethod("isEmpty", "()Z", thread)
        .asInstanceOf[ScalaPrimitiveValue].underlying
        .asInstanceOf[BooleanValue]
        .value()
    }

  def callToArray(collectionRef: ScalaObjectReference, thread: ScalaThread = ScalaDebugger.currentThread): ScalaArrayReference =
    collectionRef.wrapJDIException("Exception while converting collection to Array") {
      // the way to call toArray on a collection is slightly different between Scala 2.9 and 2.10
      // the base object to use to get the Manifest and the method signature are different
      val (manifestObject, toArraySignature) = if (collectionRef.getDebugTarget.is2_10Compatible(thread)) {
        // Sometimes there's problem that ClassManifestFactory cannot be loaded. In such cases e.g. variables view just shows normal view instead of logical structure.
        // Similar situation occurs in the case of expression evaluator's tree view when user creates e.g. List and there wasn't any collection used in debugged code.
        // But when user will evaluate e.g. Set or Map, everything starts working correctly even for this mentioned List.
        (collectionRef.getDebugTarget().objectByName("scala.reflect.ClassManifestFactory", false, null), "(Lscala/reflect/ClassTag;)Ljava/lang/Object;")
      } else {
        (collectionRef.getDebugTarget().objectByName("scala.reflect.Manifest", false, null), "(Lscala/reflect/ClassManifest;)Ljava/lang/Object;")
      }

      // get Manifest.Any, needed to call toArray(..)
      val anyManifestObject = manifestObject.invokeMethod("Any", thread) match {
        case o: ScalaObjectReference =>
          o
        case _ =>
          // in case something changes in the next versions of Scala
          throw new Exception("Unexpected return value for Manifest.Any()")
      }

      collectionRef.invokeMethod("toArray", toArraySignature, thread, anyManifestObject)
        .asInstanceOf[ScalaArrayReference]
    }

  def splitCollection(traversableLikeRef: ScalaObjectReference, splitAtIndex: Int, thread: ScalaThread = ScalaDebugger.currentThread): (ScalaObjectReference, ScalaObjectReference) =
    traversableLikeRef.wrapJDIException("Exception while splitting collection at index $index") {
      val arg = ScalaValue(splitAtIndex, traversableLikeRef.getDebugTarget())
      val tupleWithParts = traversableLikeRef.invokeMethod("splitAt", "(I)Lscala/Tuple2;", thread, arg)
        .asInstanceOf[ScalaObjectReference]

      val firstPart = getElementOfTuple(tupleWithParts, 1, thread)
      val secondPart = getElementOfTuple(tupleWithParts, 2, thread)
      (firstPart, secondPart)
    }

  private def getElementOfTuple(tupleRef: ScalaObjectReference, elementNumber: Int, thread: ScalaThread) = {
    require(elementNumber > 0, s"Tuple element number must be positive")

    tupleRef.invokeMethod(s"_$elementNumber", "()Ljava/lang/Object;", thread)
      .asInstanceOf[ScalaObjectReference]
  }
}

object ScalaCollectionLogicalStructureType extends ILogicalStructureType with HasLogger {

  // Members declared in org.eclipse.debug.core.ILogicalStructureType

  override def getDescription(): String = "Flat the Scala collections"

  override val getId: String = ScalaDebugPlugin.id + ".logicalstructure.collection"

  // Members declared in org.eclipse.debug.core.model.ILogicalStructureTypeDelegate

  override def getLogicalStructure(value: IValue): IValue =
    callToArray(value).getOrElse(value)

  override def providesLogicalStructure(value: IValue): Boolean = true // TODO: check that as it is created by the provider, it is never used with other values

  // Members declared in org.eclipse.debug.core.model.ILogicalStructureTypeDelegate2

  override def getDescription(value: IValue): String = getDescription

  // other methods

  /**
   * Tries to call toArray on given value.
   */
  private def callToArray(value: IValue): Option[IValue] = {
    val scalaValue = value.asInstanceOf[ScalaObjectReference]

    try {
      Some(ScalaLogicalStructureProvider.callToArray(scalaValue))
    } catch {
      case e: Exception =>
        // fail gracefully in case of problem
        logger.debug("Failed to compute logical structure for '%s'".format(scalaValue), e)
        None
    }
  }
}

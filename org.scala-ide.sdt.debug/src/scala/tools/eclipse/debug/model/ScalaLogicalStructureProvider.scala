package scala.tools.eclipse.debug.model

import scala.tools.eclipse.debug.ScalaDebugPlugin
import scala.tools.eclipse.debug.ScalaDebugger
import scala.tools.eclipse.logging.HasLogger

import org.eclipse.debug.core.ILogicalStructureProvider
import org.eclipse.debug.core.ILogicalStructureType
import org.eclipse.debug.core.model.IValue

import com.sun.jdi.ClassType

class ScalaLogicalStructureProvider extends ILogicalStructureProvider {

  override def getLogicalStructureTypes(value: IValue) : Array[ILogicalStructureType] = {
    value match {
      case objectReference: ScalaObjectReference =>
        if (isScalaCollection(objectReference)) {
          Array(ScalaCollectionLogicalStructureType)
        } else {
          Array() // TODO: return fixed empty Array
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

  /**
   * Checks 'implements' with Java meaning
   */
  private def implements(classType: ClassType, interfaceName: String): Boolean = {
    import scala.collection.JavaConverters._
    classType.allInterfaces.asScala.exists(_.name == interfaceName)
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
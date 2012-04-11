package scala.tools.eclipse.debug.model

import org.eclipse.debug.core.ILogicalStructureProvider
import org.eclipse.debug.core.model.IValue
import org.eclipse.debug.core.ILogicalStructureType
import com.sun.jdi.ClassType
import org.eclipse.debug.core.DebugPlugin
import org.eclipse.debug.ui.DebugUITools
import com.sun.jdi.ObjectReference
import java.util.ArrayList
import scala.tools.eclipse.debug.ScalaDebugger

object ScalaLogicalStructureProvider {
  
  def isScalaCollection(objectReference: ScalaObjectReference): Boolean = {
    objectReference.objectReference.referenceType match {
      case classType: ClassType =>
        implements(classType, "scala.collection.TraversableOnce")
      case _ => // TODO: ScalaObjectReference should always reference objects of class type, never of array type. Can we just cast?
        false
    }
  }
  
  /**
   * Checks 'implements' with Java meaning
   */
  def implements(classType: ClassType, interfaceName: String): Boolean = {
    import scala.collection.JavaConverters._
    classType.allInterfaces.asScala.exists(_.name == interfaceName)
  }
  
}

class ScalaLogicalStructureProvider extends ILogicalStructureProvider {
  import ScalaLogicalStructureProvider._

  def getLogicalStructureTypes(value: IValue) : Array[ILogicalStructureType] = {
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
  
}

object ScalaCollectionLogicalStructureType extends ILogicalStructureType {
  
  // Members declared in org.eclipse.debug.core.ILogicalStructureType
  
  def getDescription(): String = "Flat the Scala collections"
  
  def getId(): String = "org.scala-ide.sdt.debug.logicalstructure.collection"
  
  // Members declared in org.eclipse.debug.core.model.ILogicalStructureTypeDelegate
  
  def getLogicalStructure(value: IValue): IValue = {
    
    val scalaValue= value.asInstanceOf[ScalaObjectReference]
    
    val objectReference= scalaValue.objectReference

    // TODO: get(0)....
    val manifestObjectEntity = objectReference.virtualMachine.classesByName("scala/reflect/Manifest$").get(0).asInstanceOf[ClassType]
    val manifestObjectField = manifestObjectEntity.fieldByName("MODULE$")
    val manifestObject = manifestObjectEntity.getValue(manifestObjectField).asInstanceOf[ObjectReference]
    val manifestMethod = manifestObjectEntity.concreteMethodByName("Any", "()Lscala/reflect/Manifest;")

    val anyValManifestObject= ScalaDebugger.currentThread.invokeMethod(manifestObject, manifestMethod)
    
    val toArrayMethod = objectReference.referenceType.asInstanceOf[ClassType].concreteMethodByName("toArray", "(Lscala/reflect/ClassManifest;)Ljava/lang/Object;")
    
    import scala.collection.JavaConverters._
    
    ScalaValue(ScalaDebugger.currentThread.invokeMethod(objectReference, toArrayMethod, anyValManifestObject), scalaValue.getScalaDebugTarget)
  }
  
  def providesLogicalStructure(value: IValue): Boolean = true // TODO: check that as it is created by the provider, it is never used with other values
  
  // Members declared in org.eclipse.debug.core.model.ILogicalStructureTypeDelegate2
  
  def getDescription(value: IValue): String = getDescription
}
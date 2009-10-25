/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.javaelements

import java.util.{ Map => JMap }

import org.eclipse.jdt.ui.JavaUI
import org.eclipse.jface.text.{ Position => JFacePosition }
import org.eclipse.jface.text.source.Annotation

import scala.tools.eclipse.contribution.weaving.jdt.IScalaOverrideIndicator

import scala.tools.eclipse.ScalaPresentationCompiler

trait ScalaOverrideIndicatorBuilder { self : ScalaPresentationCompiler =>
  import ScalaOverrideIndicatorBuilder._

  class OverrideIndicatorBuilderTraverser(scu : ScalaCompilationUnit, annotationMap : JMap[AnyRef, AnyRef]) extends Traverser {
    override def traverse(tree: Tree): Unit = {
      tree match {
        case defn@(_ : ClassDef | _ : ModuleDef) if defn.symbol != null =>
          val opc = new overridingPairs.Cursor(defn.symbol)
          while (opc.hasNext) {
            if (!opc.overridden.isClass && opc.overriding.pos.isOpaqueRange) {
              val text = (if (opc.overridden.isDeferred) "implements" else "overrides")+" "+opc.overridden.fullNameString

              val start = opc.overriding.pos.startOrPoint
              val end = opc.overriding.pos.endOrPoint
              
              val packageName = opc.overridden.enclosingPackage.fullNameString
              val typeNames = enclosingTypeNames(opc.overridden).mkString(".")
              val methodName = opc.overridden.name.toString
              val paramTypes = opc.overridden.tpe.paramss.flatMap(_.map(_.tpe))
              val methodTypeSignatures = paramTypes.map(mapParamTypeSignature(_))
              
              val position= new JFacePosition(start, end-start)
              val soi = new ScalaOverrideIndicator(
                scu,
                packageName,
                typeNames,
                methodName,
                methodTypeSignatures,
                text,
                opc.overridden.isDeferred
              )
              
              annotationMap.put(soi, position)
            }
            opc.next
          }
        case _ =>
      }
  
      super.traverse(tree)
    }
  }
}

object ScalaOverrideIndicatorBuilder {

  val ANNOTATION_TYPE= "org.eclipse.jdt.ui.overrideIndicator"

  class ScalaOverrideIndicator(
    scu : ScalaCompilationUnit,
    packageName : String,
    typeNames : String,
    methodName : String,
    methodTypeSignatures : List[String],
    text : String,
    isDeferred : Boolean
  ) extends Annotation(ANNOTATION_TYPE, false, text) with IScalaOverrideIndicator {
  
    override def isOverwriteIndicator : boolean = isDeferred
  
    override def open() {
      val tpe0 = JDTUtils.resolveType(scu.newSearchableEnvironment().nameLookup, packageName, typeNames, 0)
      tpe0 match {
        case Some(tpe) =>
          val method = tpe.getMethod(methodName, methodTypeSignatures.toArray)
          if (method.exists)
            JavaUI.openInEditor(method, true, true);
        case _ =>
      }
    }
  }
}
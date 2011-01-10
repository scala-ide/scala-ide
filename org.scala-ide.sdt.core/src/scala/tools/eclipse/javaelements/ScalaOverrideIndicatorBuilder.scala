/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.javaelements

import java.util.{ Map => JMap }

import org.eclipse.jdt.ui.JavaUI
import org.eclipse.jdt.internal.core.Openable
import org.eclipse.jface.text.{ Position => JFacePosition }
import org.eclipse.jface.text.source.Annotation

import scala.tools.eclipse.contribution.weaving.jdt.IScalaOverrideIndicator

import org.eclipse.ui.texteditor.ITextEditor
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility

import scala.tools.eclipse.ScalaPresentationCompiler

trait ScalaOverrideIndicatorBuilder { self : ScalaPresentationCompiler =>
  class OverrideIndicatorBuilderTraverser(scu : ScalaCompilationUnit, annotationMap : JMap[AnyRef, AnyRef]) extends Traverser {
    val ANNOTATION_TYPE= "org.eclipse.jdt.ui.overrideIndicator"

    case class ScalaIndicator(text : String, file : Openable, pos : Int) extends Annotation(ANNOTATION_TYPE, false, text) 
    with IScalaOverrideIndicator {
      def open = {
        EditorUtility.openInEditor(file, true) match { 
          case editor : ITextEditor => editor.selectAndReveal(pos, 0)
          case _ =>
        }
      }
    }

    case class JavaIndicator(
      packageName : String,
      typeNames : String,
      methodName : String,
      methodTypeSignatures : List[String],
      text : String
    ) extends Annotation(ANNOTATION_TYPE, false, text) with IScalaOverrideIndicator {
      def open() {
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
    
    override def traverse(tree: Tree): Unit = {
      tree match {
        case defn: DefTree if defn.symbol ne NoSymbol =>
          for(base <- defn.symbol.allOverriddenSymbols) {
            val text = (if (base.isDeferred && !defn.symbol.isDeferred) "implements " else "overrides ") + base.fullName
            val position = {
              val start = defn.symbol.pos.startOrPoint
              val end = defn.symbol.pos.endOrPoint
              new JFacePosition(start, end-start)
            }

            if (base.isJavaDefined) {
              val packageName = base.enclosingPackage.fullName
              val typeNames = enclosingTypeNames(base).mkString(".")
              val methodName = base.name.toString
              val paramTypes = base.tpe.paramss.flatMap(_.map(_.tpe))
              val methodTypeSignatures = paramTypes.map(mapParamTypeSignature(_))
              annotationMap.put(JavaIndicator(packageName, typeNames, methodName, methodTypeSignatures, text), position)
            } else locate(base, scu) map {
              case (f, pos) =>  annotationMap.put(ScalaIndicator(text, f, pos), position)
            }
          }
        case _ =>
      }
  
      super.traverse(tree)
    }
  }
}

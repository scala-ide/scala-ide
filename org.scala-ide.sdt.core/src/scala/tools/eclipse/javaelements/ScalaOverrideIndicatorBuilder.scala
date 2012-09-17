/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.javaelements

import java.util.{ Map => JMap }
import org.eclipse.jdt.ui.JavaUI
import org.eclipse.jdt.internal.core.Openable
import org.eclipse.jface.text.{ Position => JFacePosition }
import org.eclipse.jface.text.source
import scala.tools.eclipse.contribution.weaving.jdt.IScalaOverrideIndicator
import org.eclipse.ui.texteditor.ITextEditor
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility
import scala.tools.eclipse.{ ScalaPresentationCompiler, ScalaPlugin }
import scala.tools.eclipse.logging.HasLogger

object ScalaOverrideIndicatorBuilder {
  val OVERRIDE_ANNOTATION_TYPE = "org.eclipse.jdt.ui.overrideIndicator"
}

case class JavaIndicator(scu: ScalaCompilationUnit,
  packageName: String,
  typeNames: String,
  methodName: String,
  methodTypeSignatures: List[String],
  text: String,
  val isOverwrite: Boolean) extends source.Annotation(ScalaOverrideIndicatorBuilder.OVERRIDE_ANNOTATION_TYPE, false, text) with IScalaOverrideIndicator {

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

trait ScalaOverrideIndicatorBuilder { self : ScalaPresentationCompiler =>
  import ScalaOverrideIndicatorBuilder.OVERRIDE_ANNOTATION_TYPE
  
  case class ScalaIndicator(scu: ScalaCompilationUnit, text: String, base: Symbol, val isOverwrite: Boolean)
    extends source.Annotation(OVERRIDE_ANNOTATION_TYPE, false, text) with IScalaOverrideIndicator {
    def open = {
      ask { () => locate(base, scu) } map {
        case (file, pos) =>
          EditorUtility.openInEditor(file, true) match {
            case editor: ITextEditor => editor.selectAndReveal(pos, 0)
            case _                   =>
          }
      }
    }
  }
  
  class OverrideIndicatorBuilderTraverser(scu : ScalaCompilationUnit, annotationMap : JMap[AnyRef, AnyRef]) extends Traverser with HasLogger {
    override def traverse(tree: Tree): Unit = {
      tree match {
        case defn: DefTree if (defn.symbol ne NoSymbol) && defn.symbol.pos.isOpaqueRange =>
          try {
            for(base <- defn.symbol.allOverriddenSymbols) {
              val isOverwrite = base.isDeferred && !defn.symbol.isDeferred
              val text = (if (isOverwrite) "implements " else "overrides ") + base.fullName
              val position = new JFacePosition(defn.pos.startOrPoint, 0)
  
              if (base.isJavaDefined) {
                val packageName = base.enclosingPackage.fullName
                val typeNames = enclosingTypeNames(base).mkString(".")
                val methodName = base.name.toString
                val paramTypes = base.tpe.paramss.flatMap(_.map(_.tpe))
                val methodTypeSignatures = paramTypes.map(mapParamTypeSignature(_))
                annotationMap.put(JavaIndicator(scu, packageName, typeNames, methodName, methodTypeSignatures, text, isOverwrite), position)
              } else annotationMap.put(ScalaIndicator(scu, text, base, isOverwrite), position)
            }
          } catch {
            case ex: Throwable => eclipseLog.error("Error creating override indicators for %s".format(scu.file.path), ex) 
          }
        case _ =>
      }
  
      super.traverse(tree)
    }
  }
}

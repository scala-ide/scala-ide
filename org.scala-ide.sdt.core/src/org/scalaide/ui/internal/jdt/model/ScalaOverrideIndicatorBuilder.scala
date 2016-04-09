package org.scalaide.ui.internal.jdt.model

import java.util.{ Map => JMap }

import scala.reflect.internal.util.Position
import scala.tools.eclipse.contribution.weaving.jdt.IScalaOverrideIndicator

import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility
import org.eclipse.jdt.ui.JavaUI
import org.eclipse.jface.text.{ Position => JFacePosition }
import org.eclipse.jface.text.source
import org.eclipse.ui.texteditor.ITextEditor
import org.scalaide.core.compiler.IScalaPresentationCompiler
import org.scalaide.core.compiler.IScalaPresentationCompiler.Implicits.RichResponse
import org.scalaide.core.internal.compiler.ScalaPresentationCompiler
import org.scalaide.core.internal.jdt.model.ScalaCompilationUnit
import org.scalaide.core.internal.jdt.util.JDTUtils
import org.scalaide.logging.HasLogger

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

  def open(): Unit = {
    val tpe0 = JDTUtils.resolveType(scu.scalaProject.newSearchableEnvironment().nameLookup, packageName, typeNames, 0)
    tpe0 foreach { (tpe) =>
        val method = tpe.getMethod(methodName, methodTypeSignatures.toArray)
        if (method.exists)
          JavaUI.openInEditor(method, true, true);
    }
  }
}

/**
 * An override indicator annotation for Scala targets.
 *
 * It is important that this class does not hold on to compiler symbols. Instances are
 * long-lived and attached to the UI, surviving many rounds of compilation (some editors may
 * stay open and forgotten), when the presentation compiler is restarted. If it had a
 * reference to such a symbol it would indirectly retain the full PC in memory.
 *
 * See https://www.assembla.com/spaces/scala-ide/tickets/1002293
 */
case class ScalaIndicator(
    scu: ScalaCompilationUnit,
    pos: Position,
    text: String,
    val isOverwrite: Boolean)
  extends source.Annotation(ScalaOverrideIndicatorBuilder.OVERRIDE_ANNOTATION_TYPE, false, text)
  with IScalaOverrideIndicator
  with HasLogger {

  override def open() = {
    scu.scalaProject.presentationCompiler { compiler =>
      import compiler._

      // There's a bit of duplicated logic in here to recover the overridden symbol
      // but this way we don't need to keep a reference to a compiler symbol, which
      // may lead to memory leaks
      compiler.askTypeAt(pos).getOption() foreach { tree =>
        if ((tree.symbol ne NoSymbol) && (tree.symbol.pos.isOpaqueRange)) {
          val allOverriden = tree.symbol.allOverriddenSymbols
          logger.debug(s"Found ${allOverriden.size} overriden symbols")
          if (allOverriden.headOption.isDefined)
            openDeclaration(compiler)(allOverriden.head)
          else
            logger.warn(s"Couldn't find overriden symbol for ${tree.symbol}")
        }
      }
    }
  }

  private def openDeclaration(compiler: IScalaPresentationCompiler)(sym: compiler.Symbol): Unit = {
    import compiler._

    asyncExec { findDeclaration(sym, scu.scalaProject.javaProject) }.getOption().flatten map {
      case (file, pos) =>
        EditorUtility.openInEditor(file, true) match {
          case editor: ITextEditor => editor.selectAndReveal(pos, sym.name.decodedName.length)
          case _ =>
        }
    }

  }
}


trait ScalaOverrideIndicatorBuilder { self : ScalaPresentationCompiler =>

  class OverrideIndicatorBuilderTraverser(scu : ScalaCompilationUnit, annotationMap : JMap[AnyRef, AnyRef]) extends Traverser with HasLogger {
    override def traverse(tree: Tree): Unit = {
      tree match {
        case defn: DefTree if (defn.symbol ne NoSymbol) && defn.symbol.pos.isOpaqueRange =>
          try {
            for (base <- defn.symbol.allOverriddenSymbols.headOption) {
              val isOverwrite = base.isDeferred && !defn.symbol.isDeferred
              val text = (if (isOverwrite) "implements " else "overrides ") + base.fullName
              val position = new JFacePosition(defn.pos.start, 0)

              if (base.isJavaDefined) {
                val packageName = base.enclosingPackage.fullName
                val typeNames = enclosingTypeNames(base).mkString(".")
                val methodName = base.name.toString
                val paramTypes = base.tpe.paramss.flatMap(_.map(_.tpe))
                val methodTypeSignatures = paramTypes.map(mapParamTypeSignature(_))
                annotationMap.put(JavaIndicator(scu, packageName, typeNames, methodName, methodTypeSignatures, text, isOverwrite), position)
              } else
                annotationMap.put(ScalaIndicator(scu, defn.pos, text, isOverwrite), position)
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

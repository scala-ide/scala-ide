/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.ui.internal.editor.decorators.implicits

import scala.reflect.internal.util.Position

import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.hyperlink.IHyperlink
import org.eclipse.jface.text.source.Annotation
import org.scalaide.core.compiler.IScalaPresentationCompiler.Implicits.RichResponse
import org.scalaide.core.compiler.InteractiveCompilationUnit
import org.scalaide.logging.HasLogger
import org.scalaide.ui.editor.InteractiveCompilationUnitEditor
import org.scalaide.ui.editor.ScalaEditorAnnotation

object ImplicitAnnotation {
  final val ID = "scala.tools.eclipse.semantichighlighting.implicits.implicitConversionsOrArgsAnnotation"
}

abstract class ImplicitAnnotation(text: String) extends Annotation(ImplicitAnnotation.ID, /*isPersistent*/ false, text) with ScalaEditorAnnotation

/** The source of this implicit conversion is computed lazily, only when needed. */
class ImplicitConversionAnnotation(pos: Position, region: IRegion, text: String) extends ImplicitAnnotation(text) with HasLogger {

  def sourceLink(editor: InteractiveCompilationUnitEditor): List[IHyperlink] = {
    val icu: InteractiveCompilationUnit = editor.getInteractiveCompilationUnit

    icu.withSourceFile { (sourcefile, compiler) =>
      import compiler.ApplyImplicitView

      /* Return a range position for the given line without leading whitespace. */
      def trimLinePos(line: Int) = {
        var offset = sourcefile.lineToOffset(line)
        while (sourcefile.content(offset).isWhitespace)
          offset += 1
        val start = offset
        while (!sourcefile.isEndOfLine(offset))
          offset += 1
        compiler.rangePos(sourcefile, start, start, offset)
      }

      // We ask for a type tree of the whole line, since `ApplyImplicitView` has the exact same
      // range position as the argument to which it is applied and `askTypeAt` returns the innermost
      // tree, leaving out the implicit application. We filter inside `collect` on the precise position
      val syms = compiler.askTypeAt(trimLinePos(pos.line)).getOption().toList flatMap { tree =>
        tree.collect {
          case tree: ApplyImplicitView if pos.includes(tree.pos) => tree.fun.symbol
        }
      }

      syms flatMap { sym =>
        compiler.mkHyperlink(sym,
          name = s"Open Implicit (${sym.name})",
          region,
          icu.scalaProject.javaProject)
      }
    }.toList.flatten
  }
}

class ImplicitArgAnnotation(text: String) extends ImplicitAnnotation(text)

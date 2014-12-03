/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.ui.internal.editor.decorators.implicits

import scala.reflect.internal.util.SourceFile
import org.scalaide.ui.internal.editor.decorators.BaseSemanticAction
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text.Position
import org.eclipse.jface.text.Region
import org.eclipse.jface.text.source.Annotation
import org.eclipse.jface.text.source.ISourceViewer
import org.scalaide.core.IScalaPlugin
import org.scalaide.core.internal.compiler.ScalaPresentationCompiler
import org.scalaide.core.internal.jdt.model.ScalaCompilationUnit
import org.scalaide.ui.internal.preferences.ImplicitsPreferencePage
import org.scalaide.core.compiler.IScalaPresentationCompiler

/**
 * Semantic action for highlighting implicit conversions and parameters.
 */
class ImplicitHighlightingPresenter(sourceViewer: ISourceViewer)
  extends BaseSemanticAction(
    sourceViewer,
    ImplicitAnnotation.ID,
    Some("implicit")) {

  protected override def findAll(compiler: ScalaPresentationCompiler, scu: ScalaCompilationUnit, sourceFile: SourceFile): Map[Annotation, Position] =
    ImplicitHighlightingPresenter.findAllImplicitConversions(compiler, scu, sourceFile)
}

object ImplicitHighlightingPresenter {
  final val DisplayStringSeparator = " => "

  private def pluginStore: IPreferenceStore = IScalaPlugin().getPreferenceStore

  def findAllImplicitConversions(compiler: IScalaPresentationCompiler, scu: ScalaCompilationUnit, sourceFile: SourceFile) = {
    import compiler.Tree
    import compiler.Traverser
    import compiler.Apply
    import compiler.Select
    import compiler.ApplyImplicitView
    import compiler.ApplyToImplicitArgs

    def mkPosition(pos: compiler.Position, txt: String): Position = {
      val start = pos.start
      val end = if (pluginStore.getBoolean(ImplicitsPreferencePage.P_FIRST_LINE_ONLY)) {
        val eol = txt.indexOf('\n')
        if (eol > -1) eol else txt.length
      } else txt.length

      new Position(start, end)
    }

    def mkImplicitConversionAnnotation(t: ApplyImplicitView) = {
      import org.scalaide.core.compiler.IScalaPresentationCompiler.Implicits.RichResponse
      val txt = new String(sourceFile.content, t.pos.start, math.max(0, t.pos.end - t.pos.start)).trim()
      val pos = mkPosition(t.pos, txt)
      val region = new Region(pos.offset, pos.getLength)
      val msg = compiler.asyncExec{
        val sname = t.fun.symbol.nameString
        s"Implicit conversion found: `$txt`$DisplayStringSeparator`$sname($txt): ${t.tpe}`"
      }.getOption()

      val annotation = new ImplicitConversionAnnotation(
          () => compiler.mkHyperlink(t.symbol, name = "Open Implicit", region, scu.scalaProject.javaProject), msg.getOrElse(""))
      (annotation, pos)
    }

    def mkImplicitArgumentAnnotation(t: ApplyToImplicitArgs) = {
      val txt = new String(sourceFile.content, t.pos.start, math.max(0, t.pos.end - t.pos.start)).trim()
      // Defensive, but why x.symbol is null (see bug 1000477) for "Some(x.flatten))"
      // TODO find the implicit args value
      val argsStr = t.args match {
        case null => ""
        case l => l.map { x =>
          if ((x.symbol ne null) && (x.symbol ne compiler.NoSymbol))
            x.symbol.fullName
          else
            "<error>"
        }.mkString("( ", ", ", " )")
      }
      val annotation = new ImplicitArgAnnotation(s"Implicit arguments found: `$txt`$DisplayStringSeparator`$txt$argsStr`")
      val pos = mkPosition(t.pos, txt)
      (annotation, pos)
    }

    var implicits = Map[Annotation, Position]()

    new Traverser {
      override def traverse(t: Tree): Unit = {
        t match {
          case v: ApplyImplicitView =>
            val (annotation, pos) = mkImplicitConversionAnnotation(v)
            implicits += (annotation -> pos)
          case v: ApplyToImplicitArgs if !pluginStore.getBoolean(ImplicitsPreferencePage.P_CONVERSIONS_ONLY) =>
            val (annotation, pos) = mkImplicitArgumentAnnotation(v)
            implicits += (annotation -> pos)
          case _ =>
        }
        super.traverse(t)
      }
    }.traverse(compiler.askLoadedTyped(sourceFile, keepLoaded = false).get.fold(identity _, _ => compiler.EmptyTree))

    implicits
  }
}

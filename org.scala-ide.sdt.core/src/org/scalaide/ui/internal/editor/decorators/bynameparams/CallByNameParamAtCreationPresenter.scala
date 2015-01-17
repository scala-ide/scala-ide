package org.scalaide.ui.internal.editor.decorators.bynameparams

import org.scalaide.core.extensions.SemanticHighlightingParticipant
import org.scalaide.core.internal.jdt.model.ScalaCompilationUnit
import scala.reflect.internal.util.SourceFile
import org.scalaide.core.compiler.IScalaPresentationCompiler
import org.eclipse.jface.text.Position
import org.eclipse.jface.text.source.Annotation
import org.scalaide.logging.HasLogger
import org.eclipse.jface.text.source.ISourceViewer
import org.scalaide.ui.internal.editor.decorators.BaseSemanticAction
import org.scalaide.core.internal.compiler.ScalaPresentationCompiler
import org.scalaide.ui.internal.preferences.CallByNameParamCreationPreferencePage
import org.scalaide.core.IScalaPlugin

final class CallByNameParamAtCreationPresenter(sourceViewer: ISourceViewer) extends
  BaseSemanticAction(sourceViewer, CallByNameParamAtCreationAnnotation.ID, Some("callByNameParamCreation")) {

  import CallByNameParamAtCreationPresenter._

  protected override def findAll(compiler: ScalaPresentationCompiler, scu: ScalaCompilationUnit, sourceFile: SourceFile): Map[Annotation, Position] =
    CallByNameParamAtCreationPresenter.findByNameParamCreations(compiler, scu, sourceFile, prefStoreCfg)
}

object CallByNameParamAtCreationPresenter extends HasLogger {
  final case class Cfg(firstLineOnly: Boolean)

  private def prefStoreCfg: Cfg = {
    val prefStore = IScalaPlugin().getPreferenceStore
    Cfg(prefStore.getBoolean(CallByNameParamCreationPreferencePage.P_FIRST_LINE_ONLY))
  }

  /**
   * Finds all places in the source where call-by-name parameters are created.
   *
   * See #1002340 for further information.
   */
  def findByNameParamCreations(compiler: IScalaPresentationCompiler, scu: ScalaCompilationUnit, sourceFile: SourceFile, cfg: Cfg): Map[Annotation, Position] = {
    def findByNameParamCreations(tree: compiler.Tree) = {

      object traverser extends compiler.Traverser {
        var result: Map[Annotation, Position] = Map()

        override def traverse(tree: compiler.Tree) {
          result = tree match {
            case compiler.Apply(fun, args) if (fun.tpe != null) => result ++ processArgs(fun.tpe, args)
            case _ => result
          }

          super.traverse(tree)
        }

        def processArgs(funTpe: compiler.Type, args: List[compiler.Tree]): Map[Annotation, Position] = {
          if (funTpe.params.size != args.size /* <- possible for faulty code */) {
            Map()
          } else {
            val byNameArgs = funTpe.params.zip(args).withFilter { case (param, _) =>
               param.isByNameParam
            }.map(_._2)

            (for (arg <- byNameArgs) yield {
              val txt = toText(arg)
              (toAnnotation(arg, txt), toPosition(arg, txt))
            }).toMap
          }
        }

        def toText(arg: compiler.Tree): String = {
          sourceFile.content.view(arg.pos.start, arg.pos.end).mkString("")
        }

        def toAnnotation(arg: compiler.Tree, txt: String): Annotation = {
          new CallByNameParamAtCreationAnnotation(s"Call-by-name parameter creation: () => $txt")
        }

        def toPosition(arg: compiler.Tree, txt: String): Position = {
          val start = arg.pos.start
          val length = {
            if (cfg.firstLineOnly) {
              val eol = txt.indexOf('\n')
              if (eol > -1) eol else txt.length
            } else {
              txt.length
            }
          }
          new Position(start, length)
        }
      }

      traverser.traverse(tree)
      traverser.result
    }

    compiler.askLoadedTyped(sourceFile, false).get match {
      case Left(tree) => findByNameParamCreations(tree)
      case Right(th) =>
        logger.error("Error while searching for call-by-name parameter creations.", th)
        Map()
    }
  }
}

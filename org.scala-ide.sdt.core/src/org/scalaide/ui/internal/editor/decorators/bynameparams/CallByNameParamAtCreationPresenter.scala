package org.scalaide.ui.internal.editor.decorators.bynameparams

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
  private def prefStoreCfg: Boolean = {
    val prefStore = IScalaPlugin().getPreferenceStore
    prefStore.getBoolean(CallByNameParamCreationPreferencePage.PFirstLineOnly)
  }

  /**
   * Finds all places in the source where call-by-name parameters are created.
   *
   * See #1002340 for further information.
   */
  def findByNameParamCreations(compiler: IScalaPresentationCompiler, scu: ScalaCompilationUnit, sourceFile: SourceFile, firstLineOnly: Boolean = prefStoreCfg): Map[Annotation, Position] = {
    def findByNameParamCreations(tree: compiler.Tree) = {

      object traverser extends compiler.Traverser {
        var result: Map[Annotation, Position] = Map()

        override def traverse(tree: compiler.Tree): Unit = {
          result = tree match {
            case compiler.Apply(fun, args) if (fun.tpe != null) => result ++ processArgs(fun.tpe, args)
            case _ => result
          }

          super.traverse(tree)
        }

        def isSynthetic(tree: compiler.Tree) = {
          Option(tree.symbol).exists(_.isSynthetic)
        }

        def isByNameParam(param: compiler.Symbol) = {
          param.isByNameParam || referencesByNameParam(param)
        }

        /*
         * This should cover partially applied functions referencing by-name-params (see #1002381).
         */
        def referencesByNameParam(param: compiler.Symbol) = {
          import org.scalaide.core.compiler.IScalaPresentationCompiler.Implicits.RichResponse
          if (!param.hasRawInfo) {
            false
          } else {
            compiler.asyncExec {
              param.rawInfo.typeSymbol match {
                case cs: compiler.ClassSymbol => cs.name == compiler.tpnme.BYNAME_PARAM_CLASS_NAME
                case _ => false
              }
            }.getOrElse(false)()
          }
        }

        def processArgs(funTpe: compiler.Type, args: List[compiler.Tree]): Map[Annotation, Position] = {
          if (funTpe.params.size != args.size) {
            // This might happen for code that does not compile cleanly; run the Unit-Tests for this class with an appropriate breakpoint
            // if you are interested in details.
            Map()
          } else {
            val byNameArgs = funTpe.params.zip(args).withFilter { case (param, arg) =>
              isByNameParam(param) && !isSynthetic(arg)
            }.map(_._2)

            (for (arg <- byNameArgs) yield {
              val txt = toText(arg)
              (toAnnotation(txt), toPosition(arg, txt))
            }).toMap
          }
        }

        def toText(arg: compiler.Tree): String = {
          sourceFile.content.view(arg.pos.start, arg.pos.end).mkString("")
        }

        def toAnnotation(txt: String): Annotation = {
          new CallByNameParamAtCreationAnnotation(s"Call-by-name parameter creation: () => $txt")
        }

        def toPosition(arg: compiler.Tree, txt: String): Position = {
          val start = arg.pos.start
          val length = {
            if (firstLineOnly) {
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

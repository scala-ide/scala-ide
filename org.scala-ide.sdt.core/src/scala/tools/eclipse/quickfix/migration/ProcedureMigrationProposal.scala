package scala.tools.eclipse.quickfix.migration

import scala.tools.eclipse.logging.HasLogger
import scala.tools.eclipse.quickfix.BasicCompletionProposal
import scala.tools.eclipse.util.parsing.ScalariformParser
import scala.tools.eclipse.util.parsing.ScalariformUtils
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.Position
import org.eclipse.text.edits.ReplaceEdit
import scalariform.parser.FunDefOrDcl
import scalariform.parser.ProcFunBody

case class ProcedureMigrationProposal(name: String, location: Position) extends BasicCompletionProposal(95, s"Convert procedure `$name` to method") with HasLogger {
  override def apply(document: IDocument): Unit = try {
    val source = document.get
    val Some((scalariformCU, _)) = ScalariformParser.safeParse(source)
    val nodes = ScalariformUtils.toStream(scalariformCU).toArray
    val Some(funDef) = nodes.reverse.collectFirst {
      case funDef: FunDefOrDcl if funDef.tokens.headOption.map(_.offset < location.offset).getOrElse(false) => funDef
    }

    val offset = funDef.nameToken.offset + funDef.nameToken.text.length
    val returnUnitText = funDef.funBodyOpt match {
      case Some(_) => ": Unit ="
      case None => ": Unit"
    }
    new ReplaceEdit(offset, 0, returnUnitText).apply(document)
  } catch {
    case e: Throwable => logger.error("Problem converting procedure to method", e)
  }
}

package scala.tools.eclipse.completion
import scala.tools.eclipse.ScalaPresentationCompiler
import scala.reflect.internal.util.SourceFile
import org.eclipse.jdt.core.search.SearchEngine
import org.eclipse.jdt.core.search.IJavaSearchConstants
import org.eclipse.jdt.core.search.SearchPattern
import org.eclipse.jdt.core.search.TypeNameRequestor
import org.eclipse.jdt.core.IJavaElement
import scala.collection.mutable
import org.eclipse.core.runtime.NullProgressMonitor
import scala.tools.eclipse.logging.HasLogger
import scala.tools.eclipse.InteractiveCompilationUnit
import scala.collection.mutable.MultiMap
import scala.tools.eclipse.util.Utils
import scala.tools.eclipse.ScalaPlugin
import scala.tools.eclipse.completion.CompletionContext.ContextType

/** Base class for Scala completions. No UI dependency, can be safely used in a
 *  headless testing environment.
 *
 *  @see scala.tools.eclipse.ui.ScalaCompletionProposalComputer
 */
class ScalaCompletions extends HasLogger {
  import org.eclipse.jface.text.IRegion

  def findCompletions(region: IRegion)(position: Int, scu: InteractiveCompilationUnit)
                             (sourceFile: SourceFile, compiler: ScalaPresentationCompiler): List[CompletionProposal] = {
    val wordStart = region.getOffset
    val wordAtPosition = (if (position <= wordStart) "" else scu.getContents.slice(wordStart, position).mkString.trim).toArray
    val typed = new compiler.Response[compiler.Tree]
    val pos = compiler.rangePos(sourceFile, position, position, position)
    compiler.askTypeAt(pos, typed)
    val t1 = typed.get.left.toOption

    val listedTypes = new mutable.HashMap[String, mutable.Set[CompletionProposal]] with MultiMap[String, CompletionProposal]

    def isAlreadyListed(fullyQualifiedName: String, display: String) =
      listedTypes.entryExists(fullyQualifiedName, _.display == display)

    def addCompletions(completions: List[compiler.Member], matchName: Array[Char], start: Int, prefixMatch: Boolean, contextType: ContextType) {
      def nameMatches(sym: compiler.Symbol) = {
        val name = sym.decodedName.toString.toArray
        if (prefixMatch) {
          prefixMatches(name, matchName)
        } else {
          exactMatches(name, matchName)
        }
      }

      def completionFilter(sym: compiler.Symbol, viaView: compiler.Symbol = compiler.NoSymbol,
        inherited: Option[Boolean] = None) = {
        if (contextType == CompletionContext.NewContext)
          sym.isConstructor && viaView == compiler.NoSymbol && !inherited.getOrElse(false)
        else
          !sym.isConstructor && nameMatches(sym)
      }


      val context = CompletionContext(contextType)

      compiler.askOption { () =>
        for (completion <- completions) {
          val completionProposal = completion match {
            case compiler.TypeMember(sym, tpe, true, inherited, viaView) if completionFilter(sym, viaView, Some(inherited)) =>
              Some(compiler.mkCompletionProposal(matchName, start, sym, tpe, inherited, viaView, context))
            case compiler.ScopeMember(sym, tpe, true, _) if completionFilter(sym) =>
              Some(compiler.mkCompletionProposal(matchName, start, sym, tpe, false, compiler.NoSymbol, context))
            case _ => None
          }

          completionProposal foreach { proposal =>
            if (!isAlreadyListed(proposal.fullyQualifiedName, proposal.display)) {
              listedTypes.addBinding(proposal.fullyQualifiedName, proposal)
            }
          }
        }
      }
    }

    def fillTypeCompletions(pos: Int, contextType: ContextType = CompletionContext.DefaultContext,
                            matchName: Array[Char] = wordAtPosition, start: Int = wordStart, prefixMatch: Boolean = true) {
      def typeCompletionsAt(pos: Int): List[compiler.Member] = {
        val cpos = compiler.rangePos(sourceFile, pos, pos, pos)
        val completed = new compiler.Response[List[compiler.Member]]
        compiler.askTypeCompletion(cpos, completed)
        completed.get.left.toOption.getOrElse(Nil)
      }
      addCompletions(typeCompletionsAt(pos), matchName, start, prefixMatch, contextType)
    }

    def fillScopeCompletions(pos: Int, contextType: ContextType = CompletionContext.DefaultContext,
                             matchName: Array[Char] = wordAtPosition, start: Int = wordStart, prefixMatch: Boolean = true) {
      def scopeCompletionsAt(pos: Int): List[compiler.Member] = {
        val cpos = compiler.rangePos(sourceFile, pos, pos, pos)
        val completed = new compiler.Response[List[compiler.Member]]
        compiler.askScopeCompletion(cpos, completed)
        completed.get.left.toOption.getOrElse(Nil)
      }

      addCompletions(scopeCompletionsAt(pos), matchName, start, prefixMatch, contextType)
      // try and find type in the classpath as well

      // first try and determine if there is a package name prefixing the word being completed
      val packageName = for {
        e <- t1 if (e.pos.isDefined && pos > e.pos.startOrPoint)
        length = pos - e.pos.startOrPoint
        // get text of tree element, removing all whitespace
        content = sourceFile.content.slice(e.pos.startOrPoint, position).filterNot {c => c.isWhitespace}
        // see if it looks like qualified type reference
        if (length > matchName.length + 1 && content.find {c => !c.isUnicodeIdentifierPart && c != ','} == None)
      } yield content.slice(0, content.length - matchName.length - 1)

      logger.info(s"Search for: [${packageName.map(_.mkString)}].${matchName.mkString}")
      if (matchName.length > 0 || packageName.isDefined) {
        // requestor receives JDT search results
        val requestor = new TypeNameRequestor() {
          override def acceptType(modifiers: Int, packageNameArray: Array[Char], simpleTypeName: Array[Char],
            enclosingTypeName: Array[Array[Char]], path: String) {
            val packageName = new String(packageNameArray)
            def stripEndingDollar(str: String) = if (str.endsWith("$")) str.init else str
            val enclosingName = for {
              chars <- enclosingTypeName
              name = new String(chars) if name != "package$"
            } yield stripEndingDollar(name)
            def addDots(parts: Seq[String]) = parts filter (_.nonEmpty) mkString "."
            val packageWithEnclosing = addDots(packageName +: enclosingName)
            val simpleName = stripEndingDollar(new String(simpleTypeName))
            val fullyQualifiedName = addDots(packageName +: enclosingName :+ simpleName)

            logger.info(s"Found type: $fullyQualifiedName")

            if (simpleName.indexOf("$") < 0 && !isAlreadyListed(fullyQualifiedName, simpleName)) {
              logger.info(s"Adding type: $fullyQualifiedName")
              listedTypes.addBinding(fullyQualifiedName, CompletionProposal(
                MemberKind.Object,
                CompletionContext(contextType),
                start,
                simpleName,
                simpleName,
                packageWithEnclosing,
                50,
                true,
                () => List(),
                List(),
                fullyQualifiedName,
                true))
            }
          }
        }

        // launch the JDT search, for a type in the package, starting with the given prefix
        new SearchEngine().searchAllTypeNames(
          packageName.getOrElse(null),
          SearchPattern.R_EXACT_MATCH,
          matchName,
          if (prefixMatch) SearchPattern.R_PREFIX_MATCH else SearchPattern.R_EXACT_MATCH,
          IJavaSearchConstants.TYPE,
          SearchEngine.createJavaSearchScope(Array[IJavaElement](scu.scalaProject.javaProject), true),
          requestor,
          if (ScalaPlugin.plugin.noTimeoutMode) {
            IJavaSearchConstants.WAIT_UNTIL_READY_TO_SEARCH
          } else {
            IJavaSearchConstants.FORCE_IMMEDIATE_SEARCH
          },
          null)
      }
    }

    t1 match {
      case Some(compiler.New(name)) =>
        fillTypeCompletions(name.pos.endOrPoint, CompletionContext.NewContext,
          Array(), name.pos.start, false)
      case Some(compiler.Select(qualifier, name)) if qualifier.pos.isDefined && qualifier.pos.isRange =>
        // completion on qualified type
        fillTypeCompletions(qualifier.pos.end)
      case Some(compiler.Import(expr, _)) =>
        // completion on `imports`
        fillTypeCompletions(expr.pos.endOrPoint, CompletionContext.ImportContext)
      case Some(compiler.Apply(fun, _)) =>
        fun match {
          case compiler.Select(qualifier: compiler.New, name) =>
            fillTypeCompletions(qualifier.pos.endOrPoint, CompletionContext.NewContext,
              Array(), qualifier.pos.start, false)
          case compiler.Select(qualifier, name) if qualifier.pos.isDefined && qualifier.pos.isRange =>
            fillTypeCompletions(qualifier.pos.endOrPoint, CompletionContext.ApplyContext,
              name.decoded.toArray, qualifier.pos.end + 1, false)
          case _ =>
            val funName = scu.getContents.slice(fun.pos.startOrPoint, fun.pos.endOrPoint)
            fillScopeCompletions(fun.pos.endOrPoint, CompletionContext.ApplyContext, funName,
              fun.pos.startOrPoint, false)
        }
      case _ =>
        fillScopeCompletions(position)
    }

    listedTypes.values.flatten.toList
  }
}

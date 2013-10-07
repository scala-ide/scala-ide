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
    var contextType: CompletionContextType = DefaultContext

    import scala.reflect.runtime.universe._
    logger.info(showRaw(t1.get))

    val listedTypes = new mutable.HashMap[String, mutable.Set[CompletionProposal]] with MultiMap[String, CompletionProposal]

    def isAlreadyListed(fullyQualifiedName: String, display: String) =
      listedTypes.entryExists(fullyQualifiedName, _.display == display)

    def addCompletions(completed: compiler.Response[List[compiler.Member]], prefix: Array[Char], start: Int) {
      def nameMatches(sym: compiler.Symbol) = prefixMatches(sym.decodedName.toString.toArray, prefix)

      for (completions <- completed.get.left.toOption) {
        compiler.askOption { () =>
          for (completion <- completions) {
            val completionProposal = completion match {
              case compiler.TypeMember(sym, tpe, true, inherited, viaView) if !sym.isConstructor && nameMatches(sym) =>
                Some(compiler.mkCompletionProposal(prefix, start, sym, tpe, inherited, viaView)(contextType))
              case compiler.ScopeMember(sym, tpe, true, _) if !sym.isConstructor && nameMatches(sym) =>
                Some(compiler.mkCompletionProposal(prefix, start, sym, tpe, false, compiler.NoSymbol)(contextType))
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
    }

    def fillTypeCompletions(pos: Int, prefix: Array[Char], start: Int) {
      val cpos = compiler.rangePos(sourceFile, pos, pos, pos)
      val completed = new compiler.Response[List[compiler.Member]]
      compiler.askTypeCompletion(cpos, completed)
      addCompletions(completed, prefix, start)
    }

    def fillScopeCompletions(pos: Int, prefix: Array[Char], start: Int) {
      val cpos = compiler.rangePos(sourceFile, pos, pos, pos)
      val completed = new compiler.Response[List[compiler.Member]]
      compiler.askScopeCompletion(cpos, completed)
      addCompletions(completed, prefix, start)

      // try and find type in the classpath as well

      // first try and determine if there is a package name prefixing the word being completed
      val packageName = for {
        e <- t1 if (e.pos.isDefined && pos > e.pos.startOrPoint)
        length = pos - e.pos.startOrPoint
        // get text of tree element, removing all whitespace
        content = sourceFile.content.slice(e.pos.startOrPoint, position).filterNot {c => c.isWhitespace}
        // see if it looks like qualified type reference
        if (length > prefix.length + 1 && content.find {c => !c.isUnicodeIdentifierPart && c != ','} == None)
      } yield content.slice(0, content.length - prefix.length - 1)

      logger.info(s"Search for: [${packageName.map(_.mkString)}].${prefix.mkString}")
      if (prefix.length > 0 || packageName.isDefined) {
        // requestor receives JDT search results
        val requestor = new TypeNameRequestor() {
          override def acceptType(modifiers: Int, packageNameArray: Array[Char], simpleTypeName: Array[Char],
            enclosingTypeName: Array[Array[Char]], path: String) {
            val packageName = new String(packageNameArray)
            val simpleName = new String(simpleTypeName)
            val fullyQualifiedName = (if (packageName.length > 0) packageName + '.' else "") + simpleName

            logger.info(s"Found type: $fullyQualifiedName")

            if (simpleName.indexOf("$") < 0 && !isAlreadyListed(fullyQualifiedName, simpleName)) {
              logger.info(s"Adding type: $fullyQualifiedName")
              listedTypes.addBinding(fullyQualifiedName, CompletionProposal(
                MemberKind.Object,
                CompletionContext(contextType),
                start,
                simpleName,
                simpleName,
                packageName,
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
          prefix,
          SearchPattern.R_PREFIX_MATCH,
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
      case Some(compiler.Select(qualifier, name)) if qualifier.pos.isDefined && qualifier.pos.isRange =>
        // completion on qualified type
        fillTypeCompletions(qualifier.pos.end, wordAtPosition, wordStart)
      case Some(compiler.Import(expr, _)) =>
        // completion on `imports`
        fillTypeCompletions(expr.pos.endOrPoint, wordAtPosition, wordStart)
      case Some(compiler.Apply(fun, _)) =>
        contextType = ApplyContext
        fun match {
          case compiler.Select(qualifier, name) if qualifier.pos.isDefined && qualifier.pos.isRange =>
            fillTypeCompletions(qualifier.pos.endOrPoint, name.decoded.toArray, qualifier.pos.end + 1)
          case _ =>
            val funName = scu.getContents.slice(fun.pos.startOrPoint, fun.pos.endOrPoint)
            fillScopeCompletions(fun.pos.endOrPoint, funName, fun.pos.startOrPoint)
        }
      case _ =>
        fillScopeCompletions(position, wordAtPosition, wordStart)
    }

    listedTypes.values.flatten.toList
  }
}
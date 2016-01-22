package org.scalaide.core.completion

import org.eclipse.jdt.core.search.SearchEngine
import org.eclipse.jdt.core.search.IJavaSearchConstants
import org.eclipse.jdt.core.search.SearchPattern
import org.eclipse.jdt.core.search.TypeNameRequestor
import org.eclipse.jdt.core.IJavaElement
import scala.collection.mutable
import org.scalaide.logging.HasLogger
import org.scalaide.core.compiler.InteractiveCompilationUnit
import scala.collection.mutable.MultiMap
import org.scalaide.core.IScalaPlugin
import CompletionContext.ContextType
import org.scalaide.core.compiler.IScalaPresentationCompiler
import org.scalaide.core.compiler.IScalaPresentationCompiler.Implicits._
import org.scalaide.util.internal.Commons

/** Base class for Scala completions. No UI dependency, can be safely used in a
 *  headless testing environment.
 *
 *  @see org.scalaide.ui.internal.completion.ScalaCompletionProposalComputer
 */
class ScalaCompletions extends HasLogger {
  import org.eclipse.jface.text.IRegion

  /** Returns the list of possible completions, at the given position in the compilation unit,
   *  with the given region as completion prefix.
   */
  def findCompletions(region: IRegion, position: Int, icu: InteractiveCompilationUnit): List[CompletionProposal] =
    icu.scalaProject.presentationCompiler { compiler =>
      findCompletion(region, position, icu, compiler)
    }.getOrElse(Nil)

  private def findCompletion(region: IRegion, position: Int, icu: InteractiveCompilationUnit, compiler: IScalaPresentationCompiler): List[CompletionProposal] = {
    val wordStart = region.getOffset

    val sourceMap = icu.lastSourceMap()
    val sourceFile = sourceMap.sourceFile

    val scalaContents = sourceMap.scalaSource
    val wordAtPosition = if (position <= wordStart) "" else scalaContents.slice(wordStart, position).mkString.trim
    val defaultContext = if (scalaContents(wordStart - 1) != '.') CompletionContext.InfixMethodContext else CompletionContext.DefaultContext
    val pos = compiler.rangePos(sourceFile, position, position, position)
    val typed = compiler.askTypeAt(pos)
    val t1 = typed.getOption()

    val listedTypes = new mutable.HashMap[String, mutable.Set[CompletionProposal]] with MultiMap[String, CompletionProposal]

    def isAlreadyListed(fullyQualifiedName: String, display: String) =
      listedTypes.entryExists(fullyQualifiedName, _.display == display)

    def addCompletions(completions: List[compiler.Member], matchName: String, start: Int, prefixMatch: Boolean, contextType: ContextType): Unit = {
      def nameMatches(sym: compiler.Symbol) = {
        val name = sym.decodedName
        if (prefixMatch) {
          Commons.prefixMatches(name, matchName)
        } else {
          name == matchName
        }
      }

      def completionFilter(sym: compiler.Symbol, viaView: compiler.Symbol = compiler.NoSymbol,
        inherited: Option[Boolean] = None) = {
        if (contextType == CompletionContext.NewContext)
          sym.isConstructor && viaView == compiler.NoSymbol && !inherited.getOrElse(false)
        else
          !sym.isConstructor && nameMatches(sym)
      }

      compiler.asyncExec {
        for (completion <- completions) {
          val completionProposal = completion match {
            case compiler.TypeMember(sym, tpe, true, inherited, viaView) if completionFilter(sym, viaView, Some(inherited)) =>
              Some(compiler.mkCompletionProposal(matchName, start, sym, tpe, inherited, viaView, contextType, icu.scalaProject))
            case compiler.ScopeMember(sym, tpe, true, _) if completionFilter(sym) =>
              Some(compiler.mkCompletionProposal(matchName, start, sym, tpe, false, compiler.NoSymbol, contextType, icu.scalaProject))
            case _ => None
          }

          completionProposal foreach { proposal =>
            if (!isAlreadyListed(proposal.fullyQualifiedName, proposal.display)) {
              listedTypes.addBinding(proposal.fullyQualifiedName, proposal)
            }
          }
        }
      }.getOption()
    }

    def fillTypeCompletions(pos: Int, contextType: ContextType,
      matchName: String = wordAtPosition, start: Int = wordStart, prefixMatch: Boolean = true): Unit = {
      def typeCompletionsAt(pos: Int): List[compiler.Member] = {
        val cpos = compiler.rangePos(sourceFile, pos, pos, pos)
        val completed = compiler.askTypeCompletion(cpos)
        completed.getOrElse(Nil)()
      }
      addCompletions(typeCompletionsAt(pos), matchName, start, prefixMatch, contextType)
    }

    def fillScopeCompletions(pos: Int, contextType: ContextType,
      matchName: String = wordAtPosition, start: Int = wordStart, prefixMatch: Boolean = true): Unit = {
      def scopeCompletionsAt(pos: Int): List[compiler.Member] = {
        val cpos = compiler.rangePos(sourceFile, pos, pos, pos)
        val completed = compiler.askScopeCompletion(cpos)
        completed.getOrElse(Nil)()
      }

      addCompletions(scopeCompletionsAt(pos), matchName, start, prefixMatch, contextType)
      // try and find type in the classpath as well

      // first try and determine if there is a package name prefixing the word being completed
      val packageName = for {
        e <- t1 if (e.pos.isDefined && pos > e.pos.start)
        length = pos - e.pos.start
        // get text of tree element, removing all whitespace
        content = sourceFile.content.slice(e.pos.start, position).filterNot { c => c.isWhitespace }
        // see if it looks like qualified type reference
        if (length > matchName.length + 1 && content.find { c => !c.isUnicodeIdentifierPart && c != ',' } == None)
      } yield content.slice(0, content.length - matchName.length - 1)

      logger.info(s"Search for: [${packageName.map(_.mkString)}].${matchName.mkString}")
      if (matchName.length > 0 || packageName.isDefined) {
        // requestor receives JDT search results
        val requestor = new TypeNameRequestor() {
          override def acceptType(modifiers: Int, packageNameArray: Array[Char], simpleTypeName: Array[Char],
            enclosingTypeName: Array[Array[Char]], path: String): Unit = {
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
                contextType,
                start,
                simpleName,
                simpleName,
                packageWithEnclosing,
                50,
                true,
                () => List(),
                List(),
                fullyQualifiedName,
                true,
                () => None))
            }
          }
        }

        // launch the JDT search, for a type in the package, starting with the given prefix
        new SearchEngine().searchAllTypeNames(
          packageName.getOrElse(null),
          SearchPattern.R_EXACT_MATCH,
          matchName.toCharArray(),
          if (prefixMatch) SearchPattern.R_PREFIX_MATCH else SearchPattern.R_EXACT_MATCH,
          IJavaSearchConstants.TYPE,
          SearchEngine.createJavaSearchScope(Array[IJavaElement](icu.scalaProject.javaProject), true),
          requestor,
          if (IScalaPlugin().noTimeoutMode) {
            IJavaSearchConstants.WAIT_UNTIL_READY_TO_SEARCH
          } else {
            IJavaSearchConstants.FORCE_IMMEDIATE_SEARCH
          },
          null)
      }
    }

    t1 match {
      case Some(compiler.New(name)) =>
        fillTypeCompletions(name.pos.end, CompletionContext.NewContext,
          "", name.pos.start, false)
      case Some(compiler.Select(qualifier, name)) if qualifier.pos.isDefined && qualifier.pos.isRange =>
        // completion on qualified type
        fillTypeCompletions(qualifier.pos.end, defaultContext)
      case Some(compiler.Import(expr, _)) =>
        // completion on `imports`
        fillTypeCompletions(expr.pos.end, CompletionContext.ImportContext)
      case Some(compiler.Apply(fun, _)) =>
        fun match {
          case compiler.Select(qualifier: compiler.New, name) =>
            fillTypeCompletions(qualifier.pos.end, CompletionContext.NewContext,
              "", qualifier.pos.start, false)
          case compiler.Select(qualifier, name) if qualifier.pos.isDefined && qualifier.pos.isRange =>
            fillTypeCompletions(qualifier.pos.end, CompletionContext.ApplyContext,
              name.decoded, qualifier.pos.end + 1, false)
          case _ =>
            val funName = scalaContents.slice(fun.pos.start, fun.pos.end)
            fillScopeCompletions(fun.pos.end, CompletionContext.ApplyContext, new String(funName),
              fun.pos.start, false)
        }
      case _ =>
        fillScopeCompletions(position, defaultContext)
    }

    listedTypes.values.flatten.toList
  }
}

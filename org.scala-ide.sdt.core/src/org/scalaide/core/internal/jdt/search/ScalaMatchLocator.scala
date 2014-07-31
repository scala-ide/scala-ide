package org.scalaide.core.internal.jdt.search

import org.eclipse.jdt.core.search.SearchMatch
import org.eclipse.jdt.core.search.SearchPattern
import org.eclipse.jdt.core.search.SearchParticipant
import org.eclipse.jdt.core.search.TypeDeclarationMatch
import org.eclipse.jdt.core.search.TypeReferenceMatch
import org.eclipse.jdt.core.search.MethodReferenceMatch
import org.eclipse.jdt.core.search.FieldReferenceMatch
import org.eclipse.jdt.core.compiler.{ CharOperation => CharOp }
import org.eclipse.jdt.internal.compiler.ast.SingleTypeReference
import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration
import org.eclipse.jdt.internal.core.search.matching.MatchLocator
import org.eclipse.jdt.internal.core.search.matching.PossibleMatch
import scala.reflect.internal.util.RangePosition
import scala.reflect.internal.util.Position
import org.scalaide.core.compiler.ScalaPresentationCompiler
import org.scalaide.util.internal.ReflectionUtils
import org.eclipse.jdt.internal.core.search.matching.PatternLocator
import org.eclipse.jdt.internal.core.search.matching.FieldPattern
import org.eclipse.jdt.internal.core.search.matching.MethodPattern
import org.eclipse.jdt.internal.core.search.matching.TypeReferencePattern
import org.eclipse.jdt.internal.core.search.matching.TypeDeclarationPattern
import org.eclipse.jdt.internal.core.search.matching.OrPattern
import org.eclipse.jdt.core.IJavaElement
import org.scalaide.core.internal.jdt.model._

//FIXME should report all and let matcher to the selection OR only report matcher interest (pre select by type) OR ...

trait ScalaMatchLocator { self: ScalaPresentationCompiler =>
  val OneStar = Array('*')

  def MatchLocator(scu: ScalaCompilationUnit, matchLocator: MatchLocator, possibleMatch: PossibleMatch): Traverser =
     MatchLocator(scu, matchLocator, matchLocator.pattern, possibleMatch)

  def MatchLocator(scu: ScalaCompilationUnit, matchLocator: MatchLocator,
      pattern: SearchPattern, possibleMatch: PossibleMatch): MatchLocatorTraverser = {
    logger.info("Search pattern " + pattern)
    pattern match {
      case p: OrPattern =>
        val locators = MatchLocatorUtils.orPatterns(p) map { p => MatchLocator(scu, matchLocator, p, possibleMatch) }
        new OrLocator(scu, matchLocator, p, possibleMatch, locators)
      case p: TypeReferencePattern => new TypeReferenceLocator(scu, matchLocator, p, possibleMatch)
      case p: TypeDeclarationPattern => new TypeDeclarationLocator(scu, matchLocator, p, possibleMatch)
      case p: MethodPattern => new MethodLocator(scu, matchLocator, p, possibleMatch)
      case p: FieldPattern => new FieldLocator(scu, matchLocator, p, possibleMatch)
      case p => logger.debug("Could not handle pattern in match request: "+ p); NoLocator
    }
  }

  trait MatchLocatorTraverser extends Traverser {
    def scu: ScalaCompilationUnit
    def matchLocator: MatchLocator
    def possibleMatch: PossibleMatch

    override def traverse(tree: Tree): Unit = try {
      if (tree.pos.isRange && tree.pos.isDefined) {
        if(tree.pos.isOpaqueRange) report(tree)
        /* We need to customize the traversal of the Tree to ensure that the `Traverser.currentOwner`
         * gets updated only when a declaration is traversed (have a look at `enclosingDeclaration`).
         * This is done because reference matches are always reported on the enclosing declaration.
         * Ideally, we should create a Traverser subclass and move this ad-hoc logic there.*/
        tree match {
         case ClassDef(mods, name, tparams, impl) if tree.symbol.isAnonymousClass =>
           traverseTrees(mods.annotations); traverseTrees(tparams); traverse(impl)
         case TypeDef(mods, name, tparams, rhs) if !tree.symbol.isAliasType =>
           traverseTrees(mods.annotations); traverseTrees(tparams); traverse(rhs)
         case Function(vparams, body) => traverseTrees(vparams); traverse(body)
         case _ => super.traverse(tree)
        }
      }
    } catch {
      case t: TypeError => logError("Error while searching Scala tree", t)
    }

    def report(tree: Tree)

    import MatchLocatorUtils._
    def report(sm: SearchMatch) = {
      logger.info("found a match: " + sm)
      reportMethod.invoke(matchLocator, sm)
    }

    def fullyQualifiedName(qualification: Array[Char], simpleName: Array[Char]): Array[Char] =
      if(qualification == null || qualification.length == 0) simpleName
      else Array.concat(qualification, Array('.'), simpleName)

    def checkQualifier(s: Select, className: Array[Char], pat: SearchPattern) =  {
      (className eq null) || {
        s.qualifier.tpe.baseClasses exists { bc =>
          pat.matchesName(className, mapType(bc).toCharArray)
        }
      }
    }

    def posToLong(pos: Position): Long = pos.start << 32 | pos.end

    /** Returns the class/method/field symbol enclosing the tree node that is currently traversed.*/
    protected def enclosingDeclaration(): Symbol = {
      if(currentOwner.isLocalDummy) {
        // expressions in an entity's body are flagged as "local dummy", which is basically a synthetic owner of
        // the expression. Since these expression are effectively evaluated in the entity's primary constructor,
        // it makes sense to return the constructor symbol as the enclosing declaration owning the expression.
        // TODO: I now wonder how this will work with traits and nested method declarations. Need to test this!
        val constructor = currentOwner.enclClass.info.member(self.nme.CONSTRUCTOR)
        constructor
      }
      else currentOwner
    }

    /* simplified from org.eclipse.jdt.internal.core.search.matching.PatternLocator */
    /*def qualifiedPattern(simpleNamePattern: Array[Char], qualificationPattern: Array[Char]): Array[Char] =
      // NOTE: if case insensitive search then simpleNamePattern & qualificationPattern are assumed to be lowercase
      if (qualificationPattern == null)
        simpleNamePattern
      else if (simpleNamePattern eq null)
        CharOp.concat(qualificationPattern, OneStar, '.')
      else
        CharOp.concat(qualificationPattern, simpleNamePattern, '.')

    /**
     * See whether the given type can occur in a pattern (e.g. type vars cannot)
     */
    def canMatchPattern(tpe: Type): Boolean = tpe match {
      case _ : ErrorType | _ : WildcardType | _ : NoType => false
      case _ => true
    }

    def resolveLevelForType(qualifiedPattern: Array[Char], tpe: Type): Int = {
      if (qualifiedPattern eq null) AccurateMatch
      else if (tpe eq null) InaccurateMatch
      else if (!canMatchPattern(tpe)) ImpossibleMatch
      else {
        tpe match {
          case ThisType(_) | SingleType(_,_) =>
          case SingleType(pre, sym) =>
          // pre.sym.type
    // int(2)
  case TypeRef(pre, sym, args) =>
    // pre.sym[targs]
  case RefinedType(parents, defs) =>
    // parent1 with ... with parentn { defs }
  case AnnotatedType(annots, tp) =>
    // tp @annots
          case _ => PatternLocator.INACCURATE_MATCH
        }

        val qualifiedPackageName = tpe.qualifiedPackageName()
        val qualifiedSourceName = qualifiedSourceName(tpe)
        val fullyQualifiedTypeName = if (qualifiedPackageName.length == 0) qualifiedSourceName
          else CharOp.concat(qualifiedPackageName, qualifiedSourceName, '.')
        if (CharOp.`match`(qualifiedPattern, fullyQualifiedTypeName, this.isCaseSensitive))
          AccurateMatch
        else ImpossibleMatch
      }
    }*/
  }

  object NoLocator extends MatchLocatorTraverser {
    def scu: ScalaCompilationUnit = null
    def matchLocator: MatchLocator = null
    def possibleMatch: PossibleMatch = null
    def report(tree: Tree) {}
  }

  class OrLocator(val scu: ScalaCompilationUnit, val matchLocator: MatchLocator,
      val pattern: OrPattern, val possibleMatch: PossibleMatch,
      traversers: Seq[MatchLocatorTraverser]) extends MatchLocatorTraverser {
    def report(tree: Tree) = traversers foreach { t => t.report(tree) }
  }

  class MethodLocator(val scu: ScalaCompilationUnit, val matchLocator: MatchLocator, val pattern: MethodPattern, val possibleMatch: PossibleMatch) extends MatchLocatorTraverser {
    def report(tree: Tree) = tree match {
      case t: Select if t.symbol.isMethod => reportMethodReference(t, t.symbol, pattern)
      case _ =>
    }

    /** Does the method type match the desired number of parameters? Correctly handles
     *  vararg methods.
     *
     *  TODO: check for curried method definitions
     */
    def parameterSizeMatches(desiredCount: Int, tpe: MethodType): Boolean = {
      val paramss = tpe.paramss.flatten
      ((desiredCount == paramss.size)
        || ((desiredCount > paramss.size)
             && (paramss.nonEmpty && paramss.last == definitions.RepeatedParamClass))
      )
    }

    /** Does the method type match the pattern? */
    def checkSignature(tpe: MethodType, pat: MethodPattern): Boolean =
      (pat.parameterCount == -1) || (parameterSizeMatches(pat.parameterCount, tpe) && {
          val searchedParamTypes = pat.parameterQualifications.zip(pat.parameterSimpleNames) map {
            case (qualifier,name) => fullyQualifiedName(qualifier, name)
          }

          val currentParamTypes = tpe.paramss.flatten

          for (i <- 0 until currentParamTypes.size) {
            val tpeBaseClasses = currentParamTypes(i).tpe.baseClasses
            val noMatch = !tpeBaseClasses.exists { bc =>
              val tpe1 = searchedParamTypes(i)
              val tpe2 = mapType(bc).toCharArray
              pat.matchesName(tpe1, tpe2)
            }
            if (noMatch)
              return false
          }
          true
      })

    def reportMethodReference(tree: Tree, sym: Symbol, pat: MethodPattern) {
      if (!pat.matchesName(pat.selector, sym.name.toChars) || !sym.pos.isDefined) {
        logger.debug("Name didn't match: [%s] pos.isDefined: %b".format(sym.fullName, sym.pos.isDefined))
        return
      }

      val proceed = tree match {
        case t: Select => checkQualifier(t, fullyQualifiedName(pat.declaringQualification, pat.declaringSimpleName), pat)
        case _ => true
      }

      if (proceed) {
        logger.info("Qualifier matched")

        val hit = sym.tpe match {
          case t: MethodType => checkSignature(t, pat)
          case _ => pat.parameterCount <= 0 // negative means that pattern can match any number of arguments
        }

        if (hit) {
          getJavaElement(enclosingDeclaration, scu.scalaProject.javaProject).foreach { element =>
            val accuracy = SearchMatch.A_ACCURATE
            val (offset, length) =
              if (tree.isDef) (tree.pos.start + 4, tree.symbol.name.length)
              else (tree.pos.start, tree.pos.end - tree.pos.start)

            val insideDocComment = false
            val participant = possibleMatch.document.getParticipant
            val resource = possibleMatch.resource

            report(new MethodReferenceMatch(element, accuracy, offset, length, insideDocComment, participant, resource))
          }
        }
      }
    }
  }

  class FieldLocator(val scu: ScalaCompilationUnit, val matchLocator: MatchLocator, val pattern: FieldPattern, val possibleMatch: PossibleMatch) extends MatchLocatorTraverser {
    import MatchLocatorUtils._

    def report(tree: Tree) = tree match {
      case s @ Select(qualifier, _) =>
        report(qualifier)
        if(s.symbol.isValue || s.symbol.isVariable)
          reportVariableReference(s, pattern)
      case _ =>
    }

    def reportVariableReference(s: Select, pat: FieldPattern) {
      val searchedVar = pat.getIndexKey

      lazy val noPosition = !s.pos.isDefined
      lazy val nameNoMatch = !pat.matchesName(searchedVar, s.name.toChars)
      lazy val varNoMatch = !pat.matchesName(CharOp.concat(searchedVar, "_$eq".toCharArray), s.name.toChars)
      lazy val qualifierNoMatch = !checkQualifier(s, fullyQualifiedName(declaringQualificationField(pat), declaringSimpleNameField(pat)), pat)

      if (noPosition || (nameNoMatch && varNoMatch) || qualifierNoMatch) return

      getJavaElement(enclosingDeclaration, scu.scalaProject.javaProject).foreach { enclosingElement =>
        val accuracy = SearchMatch.A_ACCURATE
        val offset = s.pos.start
        val length = s.pos.end - offset
        val insideDocComment = false
        val participant = possibleMatch.document.getParticipant
        val resource = possibleMatch.resource

        report(new FieldReferenceMatch(enclosingElement, accuracy, offset, length, /*isReadAccess*/true, /*isWriteAccess*/false, insideDocComment, participant, resource))
      }
    }
  }

  class TypeReferenceLocator(val scu: ScalaCompilationUnit, val matchLocator: MatchLocator, val pattern: TypeReferencePattern, val possibleMatch: PossibleMatch) extends MatchLocatorTraverser {
    import MatchLocatorUtils._

    def report(tree: Tree) = {
      tree match {
        case t: TypeTree =>
          if (t.pos.isDefined)
            reportTypeReference(t.tpe, t.pos)
          t.tpe match {
            case TypeRef(pre, sym, args) =>
              args foreach { a => reportTypeReference(a, t.pos) }
            case TypeBounds(lo, hi) =>
              reportTypeReference(lo, t.pos)
              reportTypeReference(hi, t.pos)
            case _ =>
          }

        case v: ValOrDefDef =>
          val tpt = v.tpt
          val pos = tpt.pos
          tpt match {
            case tpt: TypeTree if pos.isDefined && !pos.isRange =>
              reportTypeReference(tpt.tpe,
                  new RangePosition(pos.source, pos.point, pos.point, pos.point + v.name.length))
            case _ =>
          }
        case id: Ident if !id.symbol.toString.startsWith("package") =>
          reportObjectReference(pattern, id.symbol, id.pos)
        case im: Import if !im.expr.symbol.toString.startsWith("package") =>
          reportObjectReference(pattern, im.expr.symbol, im.pos)
        case s : Select => s.symbol match {
          case symbol : ModuleSymbol => reportObjectReference(pattern, symbol, s.pos)
          case _ =>
        }
        case n: New =>
          reportTypeReference(n.tpe, n.tpt.pos)
        case _ =>
      }

      if (tree.symbol != null) atOwner(tree.symbol) {
        // This is executed inside `atOwner` so that the annotation's `currentOwner` is correctly set to
        // be the declaration (class or member) that is attached to the (about to be) traversed annotation.
        reportAnnotations(tree.symbol)
      }

    }

    def reportAnnotations(sym: Symbol) {
      for (annot @ AnnotationInfo(atp, args, assocs) <- sym.annotations) if (annot.pos.isDefined) {
        reportTypeReference(atp, annot.pos)
        traverseTrees(args)
      }
    }

    def reportObjectReference(pat: TypeReferencePattern, symbol: Symbol, pos: Position) {
        val searchedName = simpleName(pat)
        val symName = mapSimpleType(symbol).toCharArray
        // TODO: better char array handling
        if (pat.matchesName(searchedName, symName)) {
          val enclosingElement = scu match {
              case ssf: ScalaSourceFile =>
                ssf.getElementAt(pos.start) match {
                  case e: ScalaDefElement if e.isConstructor => e.getParent
                  case e => e
                }
              case _ => null
            }
          //since we consider only the object name (and not its fully qualified name),
          //the search is inaccurate
          val accuracy = SearchMatch.A_INACCURATE
          val offset = pos.start
          val length = pos.end - offset
          val insideDocComment = false
          val participant = possibleMatch.document.getParticipant
          val resource = possibleMatch.resource

          report(new TypeReferenceMatch(enclosingElement, accuracy, offset, length, insideDocComment, participant, resource))
        }
    }

    def reportTypeReference(tpe: Type, refPos: Position) {
      if (tpe eq null) return
      val patternFullyQualifiedName = fullyQualifiedName(qualification(pattern), simpleName(pattern))
      if(pattern.matchesName(patternFullyQualifiedName, mapType(tpe.typeSymbol).toCharArray)) {
        getJavaElement(enclosingDeclaration, scu.scalaProject.javaProject).foreach { enclosingElement =>
          val accuracy = SearchMatch.A_ACCURATE
          val offset = refPos.start
          val length = refPos.end - offset
          val insideDocComment = false
          val participant = possibleMatch.document.getParticipant
          val resource = possibleMatch.resource

          report(new TypeReferenceMatch(enclosingElement, accuracy, offset, length, insideDocComment, participant, resource))
        }
      }
    }
  }

  class TypeDeclarationLocator(val scu: ScalaCompilationUnit, val matchLocator: MatchLocator, val pattern: TypeDeclarationPattern, val possibleMatch: PossibleMatch) extends MatchLocatorTraverser {
    def report(tree: Tree) = tree match {
      case c: ClassDef if c.pos.isDefined =>
        reportTypeDefinition(c.symbol.tpe, c.pos)
      case _ =>
    }

    def reportTypeDefinition(tpe: Type, declPos: Position) {
      val decl = new TypeDeclaration(null)
      decl.name = tpe.typeSymbol.nameString.toCharArray
      if (matchLocator.patternLocator.`match`(decl, possibleMatch.nodeSet) > 0) {
        val element = scu match {
          case ssf: ScalaSourceFile => ssf.getElementAt(declPos.start)
          case scf: ScalaClassFile => scf
          case _ => null
        }
        //since we consider only the class name (and not its fully qualified name),
        //the search is inaccurate
        val accuracy = SearchMatch.A_INACCURATE
        val offset = declPos.start
        val length = declPos.end - offset
        val participant = possibleMatch.document.getParticipant
        val resource = possibleMatch.resource

        report(new TypeDeclarationMatch(element, accuracy, offset, length, participant, resource))
      }
    }
  }

  /*class MatchLocatorTraverser(scu: ScalaCompilationUnit, matchLocator: MatchLocator, possibleMatch: PossibleMatch) extends Traverser {
    import MatchLocatorUtils._

    override def traverse(tree: Tree): Unit = {
      if (tree.pos.isOpaqueRange && tree.pos.isDefined) {
        (tree, matchLocator.pattern) match {
          case (t : TypeTree, _) =>
            if (t.pos.isDefined)
              reportTypeReference(t.tpe, t.pos)
            t.tpe match {
              case TypeRef(pre, sym, args) =>
                args foreach { a => reportTypeReference(a, t.pos) }
              case TypeBounds(lo, hi) =>
                reportTypeReference(lo, t.pos)
                reportTypeReference(hi, t.pos)
              case _ =>
            }

          case (v : ValOrDefDef, _) =>
             val tpt = v.tpt
             val pos = tpt.pos
             tpt match {
               case tpt: TypeTree if pos.isDefined && !pos.isRange =>
                 reportTypeReference(tpt.tpe,
                   new RangePosition(pos.source, pos.point, pos.point, pos.point + v.name.length))
               case _ =>
             }
          case (id : Ident, pattern : TypeReferencePattern) if !id.symbol.toString.startsWith("package") =>
              reportObjectReference(pattern, id.symbol, id.pos)
          case (im : Import, pattern : TypeReferencePattern) if !im.expr.symbol.toString.startsWith("package") =>
              reportObjectReference(pattern, im.expr.symbol, im.pos)
          case (s : Select, pattern) => (s.symbol, pattern) match {
              case (symbol : ModuleSymbol, pattern : TypeReferencePattern) => reportObjectReference(pattern, symbol, s.pos)
              case (symbol : MethodSymbol, pattern : MethodPattern)        => reportValueOrMethodReference(s, pattern)
              case (symbol : MethodSymbol, pattern : FieldPattern)         => reportVariableReference(s, pattern)
              case (t, pat) => logError("Could not handle pattern in match request: "+ pat, null)
          }
          case (n: New, _) =>
            reportTypeReference(n.tpe, n.tpt.pos)
          case (c: ClassDef, _) if c.pos.isDefined =>
            reportTypeDefinition(c.symbol.tpe, c.pos)
          case (t, pat) => logError("Could not handle pattern in match request: "+ pat, null)
        }

        if (tree.symbol != null)
          reportAnnotations(tree.symbol)

        super.traverse(tree)
      }
    }


  }*/
}

object MatchLocatorUtils extends ReflectionUtils {
  private val mlClazz = classOf[MatchLocator]
  val reportMethod = getDeclaredMethod(mlClazz, "report", classOf[SearchMatch])

  private val orClazz = classOf[OrPattern]
  private val orPatternsField = getDeclaredField(orClazz, "patterns")
  def orPatterns(or: OrPattern): Array[SearchPattern] = orPatternsField.get(or).asInstanceOf[Array[SearchPattern]]

  private val fpClazz = classOf[FieldPattern]
  private val declaringSimpleNameField: java.lang.reflect.Field = getDeclaredField(fpClazz, "declaringSimpleName")
  private val declaringQualificationField: java.lang.reflect.Field = getDeclaredField(fpClazz, "declaringQualification")
  def declaringSimpleNameField(fp : FieldPattern): Array[Char] = declaringSimpleNameField.get(fp).asInstanceOf[Array[Char]]
  def declaringQualificationField(fp : FieldPattern): Array[Char] = declaringQualificationField.get(fp).asInstanceOf[Array[Char]]

  private val ftrClazz = classOf[TypeReferencePattern]
  private val simpleNameField: java.lang.reflect.Field = getDeclaredField(ftrClazz, "simpleName")
  private val qualificationField: java.lang.reflect.Field = getDeclaredField(ftrClazz, "qualification")
  def simpleName(trp : TypeReferencePattern): Array[Char] = simpleNameField.get(trp).asInstanceOf[Array[Char]]
  def qualification(trp : TypeReferencePattern): Array[Char] = qualificationField.get(trp).asInstanceOf[Array[Char]]
}

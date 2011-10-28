/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.javaelements

import org.eclipse.jdt.core.search.{ SearchMatch, SearchPattern, SearchParticipant, TypeDeclarationMatch, TypeReferenceMatch, MethodReferenceMatch, FieldReferenceMatch }

import org.eclipse.jdt.core.compiler.{ CharOperation => CharOp }
import org.eclipse.jdt.internal.compiler.ast.{ SingleTypeReference, TypeDeclaration }
import org.eclipse.jdt.internal.core.search.matching.{ MatchLocator, PossibleMatch }

import scala.tools.nsc.util.{ RangePosition, Position }

import scala.tools.eclipse.ScalaPresentationCompiler
import scala.tools.eclipse.util.ReflectionUtils
import org.eclipse.jdt.internal.core.search.matching.{ PatternLocator, FieldPattern, MethodPattern, TypeReferencePattern, TypeDeclarationPattern, OrPattern };

//FIXME should report all and let matcher to the selection OR only report matcher interest (pre select by type) OR ...

trait ScalaMatchLocator { self: ScalaPresentationCompiler =>
  val OneStar = Array('*')
  val ImpossibleMatch = 0
  val InaccurateMatch = 1
  val AccurateMatch = 2

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
      if (tree.pos.isOpaqueRange && tree.pos.isDefined) {
        report(tree)
        super.traverse(tree)
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

    def checkQualifier(s: Select, className: Array[Char], pat: SearchPattern) =  {
      (className eq null) || {
        s.qualifier.tpe.baseClasses exists { bc => 
          pat.matchesName(className, bc.name.toChars)
        }
      }
    }
    
    def posToLong(pos: Position): Long = pos.startOrPoint << 32 | pos.endOrPoint
    
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
  case AnnotatedType(annots, tp, selfsym) =>
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
      case t: DefDef => reportMethodReference(t, t.symbol, pattern)
      case _ =>
    }
    
    /** Does the method type match the desired number of parameters? Correctly handles
     *  vararg methods. 
     *  
     *  TODO: check for curried method definitions
     */
    def parameterSizeMatches(desiredCount: Int, tpe: MethodType): Boolean =
      ((desiredCount == tpe.paramTypes.size)
        || ((desiredCount > tpe.paramTypes.size)
             && (tpe.paramTypes.last.typeSymbol == definitions.RepeatedParamClass))
      )
    
    /** Does the method type match the pattern? */
    def checkSignature(tpe: MethodType, pat: MethodPattern): Boolean =
      (pat.parameterCount == -1) || (parameterSizeMatches(pat.parameterCount, tpe) && {
          val searchedParamTypes = pat.parameterSimpleNames
          val currentParamTypes = tpe.paramTypes
           
          for (i <- 0 to currentParamTypes.size - 1) 
            if (!currentParamTypes(i).baseClasses.exists { bc => 
              pat.matchesName(searchedParamTypes(i), bc.name.toChars)
            }) return false        
          true
      })
    
    def reportMethodReference(tree: Tree, sym: Symbol, pat: MethodPattern) {
      if (!pat.matchesName(pat.selector, sym.name.toChars) || !sym.pos.isDefined) {
        logger.debug("Name didn't match: [%s] pos.isDefined: %b".format(sym.name, sym.pos.isDefined))
        return
      }

      val proceed = tree match {
        case t: Select => checkQualifier(t, pat.declaringSimpleName, pat)
        case _ => true
      }
      
      if (proceed) {
        logger.info("Qualifier matched")

        val hit = sym.tpe match {
          case t: MethodType => checkSignature(t, pat)
          case _ => pat.parameterCount <= 0 // negative means that pattern can match any number of arguments
        }
        
        if (hit) {
          val enclosingElement = scu match {
            case ssf: ScalaSourceFile => ssf.getElementAt(tree.pos.startOrPoint)
            case _ => null
          }
          
          val accuracy = SearchMatch.A_INACCURATE
          val (offset, length) = if (tree.isDef)
            (tree.pos.startOrPoint + 4, tree.symbol.name.length)
          else (tree.pos.startOrPoint, tree.pos.endOrPoint - tree.pos.startOrPoint)
              
          val insideDocComment = false
          val participant = possibleMatch.document.getParticipant
          val resource = possibleMatch.resource

          report(new MethodReferenceMatch(enclosingElement, accuracy, offset, length, insideDocComment, participant, resource))
        }
      }
    }
  }
 
  class FieldLocator(val scu: ScalaCompilationUnit, val matchLocator: MatchLocator, val pattern: FieldPattern, val possibleMatch: PossibleMatch) extends MatchLocatorTraverser {
    import MatchLocatorUtils._

    def report(tree: Tree) = tree match {
      case s: Select => s.symbol match {
        case sym : MethodSymbol => reportVariableReference(s, pattern)
        case _ =>
      }
      case _ =>
    }
    
    def reportVariableReference(s: Select, pat: FieldPattern) {
      val searchedVar = pat.getIndexKey
        
      if (!s.pos.isDefined || 
          (!pat.matchesName(searchedVar, s.name.toChars) &&
           !pat.matchesName(CharOp.concat(searchedVar, "_$eq".toCharArray), s.name.toChars)) ||
           !checkQualifier(s, declaringSimpleName(pat), pat)) 
        return
  
      val enclosingElement = scu match {
        case ssf: ScalaSourceFile => ssf.getElementAt(s.pos.start)
        case _ => null
      }
      val accuracy = SearchMatch.A_INACCURATE
      val offset = s.pos.start
      val length = s.pos.end - offset
      val insideDocComment = false
      val participant = possibleMatch.document.getParticipant
      val resource = possibleMatch.resource

      report(new FieldReferenceMatch(enclosingElement, accuracy, offset, length, true, false, insideDocComment, participant, resource))
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
    
      if (tree.symbol != null) reportAnnotations(tree.symbol)
    
    }
    
    def reportAnnotations(sym: Symbol) {
      for (annot @ AnnotationInfo(atp, args, assocs) <- sym.annotations) if (annot.pos.isDefined) {
        reportTypeReference(atp, annot.pos)
        traverseTrees(args)
      }
    }
    
    def reportObjectReference(pat: TypeReferencePattern, symbol: Symbol, pos: Position) {
        val searchedName = simpleName(pat)
        val symName = symbol.name.toChars
        // TODO: better char array handling
        if (pat.matchesName(searchedName, symName) || pat.matchesName(searchedName, CharOp.append(symName, '$'))) {
          val enclosingElement = scu match {
              case ssf: ScalaSourceFile => ssf.getElementAt(pos.start)
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
      val ref = new SingleTypeReference(tpe.typeSymbol.nameString.toArray, posToLong(refPos))
      if (matchLocator.patternLocator.`match`(ref, possibleMatch.nodeSet) > 0) {
        val enclosingElement = scu match {
          case ssf: ScalaSourceFile => ssf.getElementAt(refPos.start)
          case _ => null
        }
        //since we consider only the class name (and not its fully qualified name), 
        //the search is inaccurate 
        // Matt: JUnit search results require ACCURATE matches to locate its annotations 
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
  
  class TypeDeclarationLocator(val scu: ScalaCompilationUnit, val matchLocator: MatchLocator, val pattern: TypeDeclarationPattern, val possibleMatch: PossibleMatch) extends MatchLocatorTraverser {
    import MatchLocatorUtils._
    
    def report(tree: Tree) = tree match {
      case c: ClassDef if c.pos.isDefined =>
        reportTypeDefinition(c.symbol.tpe, c.pos)
      case _ =>
    }
    
    def reportTypeDefinition(tpe: Type, declPos: Position) {
      val decl = new TypeDeclaration(null)
      decl.name = tpe.typeSymbol.nameString.toArray
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
  val mlClazz = classOf[MatchLocator]
  val reportMethod = getDeclaredMethod(mlClazz, "report", classOf[SearchMatch])
  
  val orClazz = classOf[OrPattern]
  val orPatternsField = getDeclaredField(orClazz, "patterns")
  def orPatterns(or: OrPattern) = orPatternsField.get(or).asInstanceOf[Array[SearchPattern]]
  
  val fpClazz = classOf[FieldPattern]
  val declaringSimpleNameField = getDeclaredField(fpClazz, "declaringSimpleName")
  def declaringSimpleName(fp : FieldPattern) = declaringSimpleNameField.get(fp).asInstanceOf[Array[Char]]
  
  val ftrClazz = classOf[TypeReferencePattern]
  val simpleNameField = getDeclaredField(ftrClazz, "simpleName")
  def simpleName(trp : TypeReferencePattern) = simpleNameField.get(trp).asInstanceOf[Array[Char]]
}

/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.javaelements

import org.eclipse.jdt.core.search.{ SearchMatch, SearchParticipant, TypeDeclarationMatch, TypeReferenceMatch }

import org.eclipse.jdt.internal.compiler.ast.{ SingleTypeReference, TypeDeclaration }
import org.eclipse.jdt.internal.core.search.matching.{ MatchLocator, PossibleMatch }

import scala.tools.nsc.util.{ RangePosition, Position }

import scala.tools.eclipse.ScalaPresentationCompiler
import scala.tools.eclipse.util.ReflectionUtils

trait ScalaMatchLocator { self: ScalaPresentationCompiler =>
  class MatchLocatorTraverser(scu: ScalaCompilationUnit, matchLocator: MatchLocator, possibleMatch: PossibleMatch) extends Traverser {
    import MatchLocatorUtils._

    override def traverse(tree: Tree): Unit = {
      if (tree.pos.isOpaqueRange) {
        tree match {
          case t: TypeTree if t.pos.isDefined =>
            reportTypeReference(t.tpe, t.pos)
          case v: ValOrDefDef if !v.tpt.pos.isRange =>
            reportTypeReference(v.tpt.asInstanceOf[TypeTree].tpe,
              new RangePosition(v.tpt.pos.source,
                v.tpt.pos.point,
                v.tpt.pos.point,
                v.tpt.pos.point + v.name.length))
          case c: ClassDef if c.pos.isDefined =>
            reportTypeDefinition(c.symbol.tpe, c.pos)
          case _ =>
        }

        if (tree.symbol != null)
          reportAnnotations(tree.symbol)

        super.traverse(tree)
      }
    }

    def reportAnnotations(sym: Symbol) {
      for (annot <- sym.annotations if annot.pos.isDefined) {
        reportTypeReference(annot.atp, annot.pos)
        traverseTrees(annot.args)
      }
    }

    def reportTypeDefinition(tpe: Type, declPos: Position) {
      val decl = new TypeDeclaration(null)
      decl.name = tpe.typeSymbol.nameString.toArray
      if (matchLocator.patternLocator.`match`(decl, possibleMatch.nodeSet) > 0) {
        val element = scu match {
          case ssf: ScalaSourceFile => ssf.getElementAt(declPos.start)
          case _ => null
        }
        val accuracy = SearchMatch.A_ACCURATE
        val offset = declPos.start
        val length = declPos.end - offset
        val participant = possibleMatch.document.getParticipant
        val resource = possibleMatch.resource
        val sm = new TypeDeclarationMatch(element, accuracy, offset, length, participant, resource)

        report(matchLocator, sm)
      }
    }

    def reportTypeReference(tpe: Type, refPos: Position) {
      val ref = new SingleTypeReference(tpe.typeSymbol.nameString.toArray, posToLong(refPos));
      if (matchLocator.patternLocator.`match`(ref, possibleMatch.nodeSet) > 0) {
        val enclosingElement = scu match {
          case ssf: ScalaSourceFile => ssf.getElementAt(refPos.start)
          case _ => null
        }
        val accuracy = SearchMatch.A_ACCURATE
        val offset = refPos.start
        val length = refPos.end - offset
        val insideDocComment = false
        val participant = possibleMatch.document.getParticipant
        val resource = possibleMatch.resource
        val sm = new TypeReferenceMatch(enclosingElement, accuracy, offset, length, insideDocComment, participant, resource)

        report(matchLocator, sm)
      }
    }

    def posToLong(pos: Position): Long = pos.startOrPoint << 32 | pos.endOrPoint
  }
}

object MatchLocatorUtils extends ReflectionUtils {
  val mlClazz = classOf[MatchLocator]
  val reportMethod = getDeclaredMethod(mlClazz, "report", classOf[SearchMatch])

  def report(ml: MatchLocator, sm: SearchMatch) = reportMethod.invoke(ml, sm)
}

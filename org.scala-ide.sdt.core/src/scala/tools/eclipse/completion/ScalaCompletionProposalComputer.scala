/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse
package completion

import org.eclipse.jface.text.contentassist.ICompletionProposal
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jdt.core.compiler.CharOperation

import org.eclipse.jdt.ui.text.java.{IJavaCompletionProposalComputer,
				     ContentAssistInvocationContext,
				     JavaContentAssistInvocationContext,
				     IJavaCompletionProposal}

import org.eclipse.swt.graphics.Image
import org.eclipse.jface.text.IDocument

import scala.tools.nsc.symtab.Flags

import javaelements.ScalaCompilationUnit

class ScalaCompletionProposalComputer extends IJavaCompletionProposalComputer {
  def sessionStarted() {}
  def sessionEnded() {}
  def getErrorMessage() = null

  def computeContextInformation(context : ContentAssistInvocationContext,
				monitor : IProgressMonitor) : java.util.List[_] = null

  def computeCompletionProposals(context : ContentAssistInvocationContext,
				 monitor : IProgressMonitor) : java.util.List[_] = {
    val scu = context match {
      case jc : JavaContentAssistInvocationContext => jc.getCompilationUnit match {
        case scu : ScalaCompilationUnit => scu
        case _ => return null
      }
      case _ => return null
    }

    val position = context.getInvocationOffset()

    scu.withSourceFile({ (sourceFile, compiler) =>
      val pos = compiler.rangePos(sourceFile, position, position, position)
      
      val typed = new compiler.Response[compiler.Tree]
      compiler.askTypeAt(pos, typed)
      val t1 = typed.get.left.toOption

      val chars = scu.getContents
      val (start, end, completed) = compiler.ask { () =>
        val t0 = t1 match {
          case Some(tt : compiler.TypeTree) => Some(tt.original)
          case t => t 
        }
      
        val completed = new compiler.Response[List[compiler.Member]]
        val (start, end) = t0 match {
          case Some(s@compiler.Select(qualifier, name)) if qualifier.pos.isDefined && qualifier.pos.isRange =>
            val cpos0 = qualifier.pos.endOrPoint 
            val cpos = compiler.rangePos(sourceFile, cpos0, cpos0, cpos0)
            compiler.askTypeCompletion(cpos, completed)
            (s.pos.point min s.pos.endOrPoint, s.pos.endOrPoint)
          case Some(i@compiler.Import(expr, selectors)) =>
            def qual(tree : compiler.Tree): compiler.Tree = tree.symbol.info match {
              case compiler.analyzer.ImportType(expr) => expr
              case _ => tree
            }
            val cpos0 = qual(i).pos.endOrPoint
            val cpos = compiler.rangePos(sourceFile, cpos0, cpos0, cpos0)
            compiler.askTypeCompletion(cpos, completed)
            ((cpos0 + 1) min position, position)
          case _ =>
            val region = ScalaWordFinder.findCompletionPoint(chars, position)
            val cpos = if (region == null) pos else {
              val start = region.getOffset
              compiler.rangePos(sourceFile, start, start, start)
            }
            compiler.askScopeCompletion(cpos, completed)
            if (region == null)
              (position, position)
            else {
              val start0 = region.getOffset
              val end0 = start0+region.getLength
              (start0, end0)
            }
        }
        (start, end, completed)
      }

      val prefix = (if (position <= start) "" else scu.getBuffer.getText(start, position-start).trim).toArray

      def nameMatches(sym : compiler.Symbol) = {
        val name = sym.rawname.toString.toArray
        CharOperation.prefixEquals(prefix, name, false) ||
        CharOperation.camelCaseMatch(prefix, name)	
      }

      val buff = new collection.mutable.ListBuffer[ICompletionProposal]

      class ScalaCompletionProposal(completion : String,
                                    display : String,
                                    container : String,
                                    image : Image) extends IJavaCompletionProposal {
        def getRelevance() = 100
        def getImage() = image
        def getContextInformation() = null
        def getDisplayString() = display
        def getAdditionalProposalInfo() = container
        def getSelection(d : IDocument) = null
        def apply(d : IDocument) {
          d.replace(start, position - start, completion)
        }
      }

      def accept(sym : compiler.Symbol, tpe : compiler.Type, inherited : Boolean, viaView : compiler.Symbol) {
        if (sym.isPackage || sym.isConstructor ||
            sym.hasFlag(Flags.ACCESSOR) || sym.hasFlag(Flags.PARAMACCESSOR)) return
        import ScalaImages._
        val image = (if (sym.isMethod) PUBLIC_DEF
                    else if (sym.isClass) SCALA_CLASS
                    else if (sym.isTrait) SCALA_TRAIT
                    else if (sym.isModule) SCALA_OBJECT
                    else if (sym.isType) SCALA_TYPE
                    else PUBLIC_VAL).createImage
        val name = sym.rawname.toString
        val display = if (sym.isMethod) name + tpe.paramss.
                      map(_.map(_.tpe.toString).mkString("(", ",", ")")).mkString
		      else name
        val container = sym.enclClass.fullName
        buff += new ScalaCompletionProposal(name, display, container, image)
      }

      completed.get.left.toOption match {
        case Some(completions) =>
          compiler.ask { () =>
            for(completion <- completions) {
              completion match {
                case compiler.TypeMember(sym, tpe, accessible, inherited, viaView) if nameMatches(sym) =>
                  accept(sym, tpe, inherited, viaView)
                case compiler.ScopeMember(sym, tpe, accessible, _) if nameMatches(sym) =>
                  accept(sym, tpe, false, compiler.NoSymbol)
                case _ =>
              }
            }
          }
        case None =>
          println("No completions")
      }
      collection.JavaConversions.asList(buff.toList)
    })
  }
}

/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse
package completion

import org.eclipse.jface.text.contentassist.{ICompletionProposal, ICompletionProposalExtension,
	IContextInformation, IContextInformationExtension}
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



  import ScalaImages._
  val defImage = PUBLIC_DEF.createImage()
  val classImage = SCALA_CLASS.createImage()
  val traitImage = SCALA_TRAIT.createImage()
  val objectImage = SCALA_OBJECT.createImage()
  val typeImage = SCALA_TYPE.createImage()
  val valImage = PUBLIC_VAL.createImage()
  
  class ScalaContextInformation(display : String,
                                info : String,
                                image : Image) extends IContextInformation with IContextInformationExtension {
	  def getContextDisplayString() = display
	  def getImage() = image
	  def getInformationDisplayString() = info
	  def getContextInformationPosition(): Int = 0
  }
  
  def computeContextInformation(context : ContentAssistInvocationContext,
				monitor : IProgressMonitor) : java.util.List[_] = {
	  // Currently not supported
	  collection.JavaConversions.asList(scala.collection.immutable.List[IContextInformation]())
  }

  def computeCompletionProposals(context : ContentAssistInvocationContext,
				 monitor : IProgressMonitor) : java.util.List[_] = {
    val compUnit:Option[ScalaCompilationUnit] = context match {
      case jc : JavaContentAssistInvocationContext => jc.getCompilationUnit match {
        case scu : ScalaCompilationUnit => Some(scu)
        case a =>
          None
      }
      case b =>
        None
    }

    val position = context.getInvocationOffset()
    compUnit match {
      case Some(scu) => 
        scu.withSourceFile({ (sourceFile, compiler) =>
          val pos = compiler.rangePos(sourceFile, position, position, position)
          
          val typed = new compiler.Response[compiler.Tree]
          compiler.askTypeAt(pos, typed)
          val t1 = typed.get.left.toOption

          val chars = context.getDocument.get.toCharArray
          val (start, end, completed) = compiler.ask { () =>
            val completed = new compiler.Response[List[compiler.Member]]
            val (start, end) = t1 match {
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
    
          def nameMatches(sym : compiler.Symbol) = prefixMatches(sym.decodedName.toString.toArray, prefix)	
          def prefixMatches(name : Array[Char], prefix : Array[Char]) =
        	  CharOperation.prefixEquals(prefix, name, true) || CharOperation.camelCaseMatch(prefix, name) 

          val buff = new collection.mutable.ListBuffer[ICompletionProposal]

          class ScalaCompletionProposal(completion : String,
                                        display : String,
                                        contextName: String,
                                        container : String,
                                        image : Image) extends IJavaCompletionProposal with ICompletionProposalExtension {
            def getRelevance() = 100
            def getImage() = image
            def getContextInformation(): IContextInformation =
        	  new ScalaContextInformation(display,contextName, image)
            def getDisplayString() = display
            def getAdditionalProposalInfo() = container
            def getSelection(d : IDocument) = null
            def apply(d : IDocument) { throw new IllegalStateException("Shoudln't be called") }
        
            def apply(d : IDocument, trigger : Char, offset : Int) {
              d.replace(start, offset - start, completion)
            }
            def getTriggerCharacters= null
            def getContextInformationPosition = 0
            def isValidFor(d : IDocument, pos : Int) =
              prefixMatches(completion.toArray, d.get.substring(start, pos).toArray)  
          } 

          def accept(sym : compiler.Symbol, tpe : compiler.Type, inherited : Boolean, viaView : compiler.Symbol) {
            if (sym.isPackage || sym.isConstructor ||
              sym.hasFlag(Flags.ACCESSOR) || sym.hasFlag(Flags.PARAMACCESSOR)) return
            val image = if (sym.isMethod) defImage
                        else if (sym.isClass) classImage
                        else if (sym.isTrait) traitImage
                        else if (sym.isModule) objectImage
                        else if (sym.isType) typeImage
                        else valImage
            val name = sym.decodedName
            val signature = if (sym.isMethod) { name + tpe.paramss.
                map(_.map(p => p.name.toString + ": " + p.tpe.toString).mkString("(", ",", ")")).mkString +
                ": " + tpe.finalResultType.toString}
		      else name
            val container = sym.owner.enclClass.fullName
            val display = signature + " - " + container
            // todo: display documentation in additional info.
            buff += new ScalaCompletionProposal(name, display, signature, null, image)
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
      case None =>
        collection.JavaConversions.asList(scala.collection.immutable.List[IContextInformation]())    
    }
  }
}

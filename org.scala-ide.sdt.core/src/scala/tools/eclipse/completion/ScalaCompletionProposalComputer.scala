/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse
package completion

import org.eclipse.jface.viewers.ISelectionProvider
import org.eclipse.jface.text.TextSelection
import org.eclipse.jface.text.contentassist.
             {ICompletionProposal, ICompletionProposalExtension, 
              IContextInformation, IContextInformationExtension}
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jdt.core.compiler.CharOperation

import org.eclipse.jdt.ui.text.java.{IJavaCompletionProposalComputer,
                                     ContentAssistInvocationContext,
                                     JavaContentAssistInvocationContext,
                                     IJavaCompletionProposal}

import org.eclipse.swt.graphics.Image
import org.eclipse.jdt.internal.ui.JavaPluginImages 
import org.eclipse.jface.text.IDocument

import scala.tools.nsc.symtab.Flags
import scala.tools.nsc.util.SourceFile

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
    java.util.Collections.emptyList()
  }
  
  def computeCompletionProposals(context : ContentAssistInvocationContext,
         monitor : IProgressMonitor) : java.util.List[_] = {
    import java.util.Collections.{ emptyList => javaEmptyList }
    
    val position = context.getInvocationOffset()
    context match {
      case jc : JavaContentAssistInvocationContext => jc.getCompilationUnit match {
        case scu : ScalaCompilationUnit => 
          scu.withSourceFile { findCompletions(position, context, scu) } (javaEmptyList())
        case _ => javaEmptyList()
      }
      case _ => javaEmptyList()
    }
  }  
  
  private def prefixMatches(name : Array[Char], prefix : Array[Char]) = 
    CharOperation.prefixEquals(prefix, name, false) || CharOperation.camelCaseMatch(prefix, name) 
   
  private def findCompletions(position: Int, context: ContentAssistInvocationContext, scu: ScalaCompilationUnit)
                             (sourceFile: SourceFile, compiler: ScalaPresentationCompiler): java.util.List[_] = {
    val pos = compiler.rangePos(sourceFile, position, position, position)
    
    val typed = new compiler.Response[compiler.Tree]
    compiler.askTypeAt(pos, typed)
    val t1 = typed.get.left.toOption

    val chars = context.getDocument.get.toCharArray
    val (start, completed) = compiler.ask { () =>
      val completed = new compiler.Response[List[compiler.Member]]
      val start = t1 match {
        case Some(s@compiler.Select(qualifier, name)) if qualifier.pos.isDefined && qualifier.pos.isRange =>
          val cpos0 = qualifier.pos.end 
          val cpos = compiler.rangePos(sourceFile, cpos0, cpos0, cpos0)
          compiler.askTypeCompletion(cpos, completed)
          s.pos.point min position
        case Some(i@compiler.Import(expr, selectors)) =>
          def qual(tree : compiler.Tree): compiler.Tree = tree.symbol.info match {
            case compiler.analyzer.ImportType(expr) => expr
            case _ => tree
          }
          val cpos0 = qual(i).pos.endOrPoint
          val cpos = compiler.rangePos(sourceFile, cpos0, cpos0, cpos0)
          compiler.askTypeCompletion(cpos, completed)
          (cpos0 + 1) min position
        case _ =>
          val region = ScalaWordFinder.findCompletionPoint(chars, position)
          val cpos = if (region == null) pos else {
            val start = region.getOffset
            compiler.rangePos(sourceFile, start, start, start)
          }
          compiler.askScopeCompletion(cpos, completed)
          if (region == null) position else region.getOffset
      }
      (start, completed)
    }

    val prefix = (if (position <= start) "" else scu.getBuffer.getText(start, position-start).trim).toArray
    
    def nameMatches(sym : compiler.Symbol) = prefixMatches(sym.decodedName.toString.toArray, prefix)  
    val buff = new collection.mutable.ListBuffer[ICompletionProposal]

    /** Add a new completion proposal to the buffer. Skip constructors and accessors.
     * 
     *  Computes a very basic relevance metric based on where the symbol comes from 
     *  (in decreasing order of relevance):
     *    - members defined by the owner
     *    - inherited members
     *    - members added by views
     *    - packages
     *    - members coming from Any/AnyRef/Object
     *    
     *  TODO We should have a more refined strategy based on the context (inside an import, case
     *       pattern, 'new' call, etc.)
     */
    def addCompletionProposal(sym: compiler.Symbol, tpe: compiler.Type, inherited: Boolean, viaView: compiler.Symbol) {
      if (sym.isConstructor) return

       import JavaPluginImages._
       val image = if (sym.isSourceMethod && !sym.hasFlag(Flags.ACCESSOR | Flags.PARAMACCESSOR)) defImage
                   else if (sym.isClass) classImage
                   else if (sym.isTrait) traitImage
                   else if (sym.isModule) if (sym.isJavaDefined) 
                                          if(sym.companionClass.isJavaInterface) JavaPluginImages.get(IMG_OBJS_INTERFACE) else JavaPluginImages.get(IMG_OBJS_CLASS) 
                                          else objectImage
                   else if (sym.isType) typeImage
                   else valImage
       val name = sym.decodedName
       val signature = 
         if (sym.isMethod) { name +
             (if(!sym.typeParams.isEmpty) sym.typeParams.map{_.name}.mkString("[", ",", "]") else "") +
             tpe.paramss.map(_.map(_.tpe.toString).mkString("(", ", ", ")")).mkString +
             ": " + tpe.finalResultType.toString}
         else name
       val container = sym.owner.enclClass.fullName
       
       // rudimentary relevance, place own members before ineherited ones, and before view-provided ones
       var relevance = 100
       if (inherited) relevance -= 10
       if (viaView != compiler.NoSymbol) relevance -= 20
       if (sym.isPackage) relevance -= 30
       // theoretically we'd need an 'ask' around this code, but given that
       // Any and AnyRef are definitely loaded, we call directly to definitions.
       if (sym.owner == compiler.definitions.AnyClass
           || sym.owner == compiler.definitions.AnyRefClass
           || sym.owner == compiler.definitions.ObjectClass) { 
         println("decreased relevance for Any/AnyRef owner:" + sym )
         relevance -= 40
       }
       println("\t" + relevance)
       
       val contextString = sym.paramss.map(_.map(p => "%s: %s".format(p.decodedName, p.tpe)).mkString("(", ", ", ")")).mkString("")
       buff += new ScalaCompletionProposal(start, name, signature, contextString, container, relevance, image, context.getViewer.getSelectionProvider)
    }     
    
    completed.get.left.toOption match {
      case Some(completions) =>
        compiler.ask { () =>
          for(completion <- completions) {
            completion match {
              case compiler.TypeMember(sym, tpe, accessible, inherited, viaView) if nameMatches(sym) =>
                addCompletionProposal(sym, tpe, inherited, viaView)
              case compiler.ScopeMember(sym, tpe, accessible, _) if nameMatches(sym) =>
                addCompletionProposal(sym, tpe, false, compiler.NoSymbol)
              case _ =>
            }
          }
        }
      case None =>
        println("No completions")
    }
    
    collection.JavaConversions.asList(buff.toList)
  }    
  
  private class ScalaCompletionProposal(startPos: Int, completion: String, display: String, contextName: String, 
                                        container: String, relevance: Int, image: Image, selectionProvider: ISelectionProvider) 
                                        extends IJavaCompletionProposal with ICompletionProposalExtension {
    def getRelevance() = relevance
    def getImage() = image
    def getContextInformation(): IContextInformation = 
      if (contextName.size > 0)
        new ScalaContextInformation(display, contextName, image)
      else null
        
    def getDisplayString() = display
    def getAdditionalProposalInfo() = container
    def getSelection(d : IDocument) = null
    def apply(d : IDocument) { throw new IllegalStateException("Shouldn't be called") }
    
    def apply(d : IDocument, trigger : Char, offset : Int) {
      d.replace(startPos, offset - startPos, completion)
      selectionProvider.setSelection(new TextSelection(startPos + completion.length, 0))
    }
    def getTriggerCharacters= null
    def getContextInformationPosition = 0
    def isValidFor(d : IDocument, pos : Int) = prefixMatches(completion.toArray, d.get.substring(startPos, pos).toArray)  
  } 
}

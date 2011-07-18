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
  val packageObjectImage = SCALA_PACKAGE_OBJECT.createImage()
  val typeImage = SCALA_TYPE.createImage()
  val valImage = PUBLIC_VAL.createImage()
  
  val javaInterfaceImage = JavaPluginImages.get(JavaPluginImages.IMG_OBJS_INTERFACE)
  val javaClassImage = JavaPluginImages.get(JavaPluginImages.IMG_OBJS_CLASS)
  
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
  
  private def findCompletions(position: Int, context: ContentAssistInvocationContext, scu: ScalaCompilationUnit)
                             (sourceFile: SourceFile, compiler: ScalaPresentationCompiler): java.util.List[ICompletionProposal] = {
    val chars = context.getDocument.get.toCharArray
    val region = ScalaWordFinder.findCompletionPoint(chars, position)
    
    val res = findCompletions(region)(position, context.getViewer().getSelectionProvider, scu)(sourceFile, compiler)
    
    // COMPAT: 2.8 compatiblity. backwards compatible: this compiles both with 2.9 and 2.8
    import collection.JavaConversions._
    res: java.util.List[ICompletionProposal]
  }
    
  import org.eclipse.jface.text.IRegion
  
  def findCompletions(region: IRegion)(position: Int, selectionProvider: ISelectionProvider, scu: ScalaCompilationUnit)
                             (sourceFile: SourceFile, compiler: ScalaPresentationCompiler):List[ICompletionProposal] = {
    val pos = compiler.rangePos(sourceFile, position, position, position)
    
    val start = if (region == null) position else region.getOffset
    
    val typed = new compiler.Response[compiler.Tree]
    compiler.askTypeAt(pos, typed)
    val t1 = typed.get.left.toOption

    val completed = new compiler.Response[List[compiler.Member]]
    // completion depends on the typed tree
    t1 match {
      // completion on select
      case Some(s@compiler.Select(qualifier, name)) if qualifier.pos.isDefined && qualifier.pos.isRange =>
        val cpos0 = qualifier.pos.end 
        val cpos = compiler.rangePos(sourceFile, cpos0, cpos0, cpos0)
        compiler.askTypeCompletion(cpos, completed)
      case Some(compiler.Import(expr, _)) =>
        // completion on `imports`
        val cpos0 = expr.pos.endOrPoint
        val cpos = compiler.rangePos(sourceFile, cpos0, cpos0, cpos0)
        compiler.askTypeCompletion(cpos, completed)
      case _ =>
        // this covers completion on `types`
        val cpos = compiler.rangePos(sourceFile, start, start, start)
        compiler.askScopeCompletion(cpos, completed)
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

       val image = if (sym.isSourceMethod && !sym.hasFlag(Flags.ACCESSOR | Flags.PARAMACCESSOR)) defImage
                   else if (sym.isClass) classImage
                   else if (sym.isTrait) traitImage
                   else if (sym.isPackageObject) packageObjectImage
                   else if (sym.isModule) if (sym.isJavaDefined) 
                                          if(sym.companionClass.isJavaInterface) javaInterfaceImage else javaClassImage  
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
         relevance -= 40
       }
       
       val contextString = sym.paramss.map(_.map(p => "%s: %s".format(p.decodedName, p.tpe)).mkString("(", ", ", ")")).mkString("")
       buff += new ScalaCompletionProposal(start, name, signature, contextString, container, relevance, image, selectionProvider)
    }

    for (completions <- completed.get.left.toOption) {
      compiler.askOption { () =>
        for (completion <- completions) {
          completion match {
            case compiler.TypeMember(sym, tpe, accessible, inherited, viaView) if nameMatches(sym) =>
              addCompletionProposal(sym, tpe, inherited, viaView)
            case compiler.ScopeMember(sym, tpe, accessible, _) if nameMatches(sym) =>
              addCompletionProposal(sym, tpe, false, compiler.NoSymbol)
            case _ =>
          }
        }
      }
    }
    
    buff.toList
  }    
}

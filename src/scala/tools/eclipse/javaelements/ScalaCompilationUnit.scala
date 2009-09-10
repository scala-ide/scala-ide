/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.javaelements

import java.util.{ HashMap => JHashMap, Map => JMap }

import scala.concurrent.SyncVar
import scala.util.NameTransformer

import org.eclipse.core.resources.{ IFile, IResource }
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jdt.core.{
  CompletionContext, CompletionProposal, CompletionRequestor, Flags => JDTFlags, ICompilationUnit, IJavaElement, IJavaModelStatusConstants,
  IProblemRequestor, ITypeRoot, JavaCore, JavaModelException, WorkingCopyOwner }
import org.eclipse.jdt.internal.codeassist.InternalCompletionProposal
import org.eclipse.jdt.internal.compiler.env
import org.eclipse.jdt.internal.core.{ CompilationUnitElementInfo, JavaModelStatus, JavaProject, Openable, OpenableElementInfo }
import org.eclipse.swt.graphics.Image

import scala.tools.nsc.io.AbstractFile
import scala.tools.nsc.symtab.Flags
import scala.tools.nsc.util.{ BatchSourceFile, SourceFile }

import scala.tools.eclipse.{ ScalaPlugin, ScalaPresentationCompiler, ScalaSourceIndexer }
import scala.tools.eclipse.util.ReflectionUtils

abstract class TreeHolder {
  val compiler : ScalaPresentationCompiler
  val body : compiler.Tree
}

trait ScalaCompilationUnit extends Openable with env.ICompilationUnit with ScalaElement with ImageSubstituter {
  val project = ScalaPlugin.plugin.getScalaProject(getJavaProject.getProject)
  lazy val aFile = getFile
  var sFile : BatchSourceFile = null
  var treeHolder : TreeHolder = null
  var reload = false
  
  def getFile : AbstractFile
  
  def getTreeHolder : TreeHolder = {
    if (treeHolder == null) {

      treeHolder = new TreeHolder {
        val compiler = project.presentationCompiler
        val body = {
          val typed = new SyncVar[Either[compiler.Tree, Throwable]]
          compiler.askType(getSourceFile, reload, typed)
          typed.get match {
            case Left(tree) =>
              if (reload) {
                val file = getCorrespondingResource.asInstanceOf[IFile]
                val problems = compiler.problemsOf(file)
                val problemRequestor = getProblemRequestor
                if (problemRequestor != null) {
                  try {
                    problemRequestor.beginReporting
                    problems.map(problemRequestor.acceptProblem(_))
                  } finally {
                    problemRequestor.endReporting
                  }
                }
              }
              tree
            case Right(thr) =>
              ScalaPlugin.plugin.logError("Failure in presentation compiler", thr)
              compiler.EmptyTree
          }
        }
      }
      
      reload = false
    }
    
    treeHolder
  }
  
  def discard {
    if (treeHolder != null) {
      val th = treeHolder
      import th._

      compiler.removeUnitOf(sFile)
      treeHolder = null
      sFile = null
    }
  }
  
  override def close {
    discard
    super.close
  }
  
  def getSourceFile : SourceFile = {
    if (sFile == null)
      sFile = new BatchSourceFile(aFile, getBuffer.getCharacters) 
    sFile
  }

  def getProblemRequestor : IProblemRequestor = null

  override def buildStructure(info : OpenableElementInfo, pm : IProgressMonitor, newElements : JMap[_, _], underlyingResource : IResource) : Boolean = {
    val th = getTreeHolder
    import th._

    if (body == null || body.isEmpty) {
      info.setIsStructureKnown(false)
      return info.isStructureKnown
    }
    
    val sourceLength = getBuffer.getLength
    new compiler.StructureBuilderTraverser(this, info, newElements.asInstanceOf[JMap[AnyRef, AnyRef]], sourceLength).traverse(body)
    
    info match {
      case cuei : CompilationUnitElementInfo =>
        cuei.setSourceLength(sourceLength)
      case _ =>
    }

    info.setIsStructureKnown(true)
    info.isStructureKnown
  }
  
  def addToIndexer(indexer : ScalaSourceIndexer) {
    val th = getTreeHolder 
    import th._

    if (body != null)
      new compiler.IndexBuilderTraverser(indexer).traverse(body)
  }
  
  override def codeSelect(cu : env.ICompilationUnit, offset : Int, length : Int, workingCopyOwner : WorkingCopyOwner) : Array[IJavaElement] = {
    val javaProject = getJavaProject.asInstanceOf[JavaProject]
    val environment = javaProject.newSearchableNameEnvironment(workingCopyOwner)
    
    val requestor = new ScalaSelectionRequestor(environment.nameLookup, this)
    val buffer = getBuffer
    if (buffer != null) {
      val end = buffer.getLength
      if (offset < 0 || length < 0 || offset + length > end )
        throw new JavaModelException(new JavaModelStatus(IJavaModelStatusConstants.INDEX_OUT_OF_BOUNDS))
  
      val engine = new ScalaSelectionEngine(environment, requestor, javaProject.getOptions(true))
      engine.select(cu, offset, offset + length - 1)
    }
    
    val elements = requestor.getElements
    if(elements.isEmpty)
      println("No selection")
    else
      for(e <- elements)
        println(e)
    elements
  }

  def codeComplete
    (cu : env.ICompilationUnit, unitToSkip : env.ICompilationUnit,
     position : Int,  requestor : CompletionRequestor, owner : WorkingCopyOwner, typeRoot : ITypeRoot) {
     codeComplete(cu, unitToSkip, position, requestor, owner, typeRoot, null) 
  }
    
  override def codeComplete
    (cu : env.ICompilationUnit, unitToSkip : env.ICompilationUnit,
     position : Int,  requestor : CompletionRequestor, owner : WorkingCopyOwner, typeRoot : ITypeRoot,
     monitor : IProgressMonitor) {

    import InternalCompletionProposalUtils._
    
    val javaProject = getJavaProject.asInstanceOf[JavaProject]
    val environment = javaProject.newSearchableNameEnvironment(owner)
    
    def createProposal(kind : Int, completionOffset : Int) : InternalCompletionProposal = 
      CompletionProposal.create(kind, completionOffset).asInstanceOf[InternalCompletionProposal] 
  
    val th = getTreeHolder
    import th._
    import compiler.{ javaType, mapModifiers, mapTypeName, mapParamTypeName, mapParamTypePackageName, nme }

    val pos = compiler.rangePos(getSourceFile, position, position, position)
    
    val typed = new SyncVar[Either[compiler.Tree, Throwable]]
    compiler.askTypeAt(pos, typed)
    val t0 = typed.get.left.toOption 
    val (cpos0, start, end) = t0 match {
      case Some(s@compiler.Select(qualifier, name)) =>
        (qualifier.pos.endOrPoint, s.pos.point min s.pos.endOrPoint, s.pos.endOrPoint)
      case _ => return
    }
    
    val cpos = compiler.rangePos(getSourceFile, cpos0, cpos0, cpos0)
    
    val completed = new SyncVar[Either[List[compiler.Member], Throwable]]
    compiler.askTypeCompletion(cpos, completed)
    completed.get.left.toOption match {
      case Some(completions) =>
        val context = new CompletionContext
        requestor.acceptContext(context)
      
        for(completion <- completions)
          completion match {
            case compiler.TypeMember(sym, tpe, accessible, inherited, viaView) =>
              if (sym.hasFlag(Flags.ACCESSOR) || sym.hasFlag(Flags.PARAMACCESSOR)) {
                val proposal =  createProposal(CompletionProposal.FIELD_REF, position)
                val fieldTypeSymbol = sym.tpe.resultType.typeSymbol
                val transformedName = NameTransformer.decode(sym.name.toString) 
                val relevance = if (inherited) 20 else if(viaView != compiler.NoSymbol) 10 else 30
                
                proposal.setDeclarationSignature(javaType(sym.owner.tpe).getSignature.replace('/', '.').toArray)
                proposal.setSignature(javaType(sym.tpe).getSignature.replace('/', '.').toArray)
                setDeclarationPackageName(proposal, sym.enclosingPackage.fullNameString.toArray)
                setDeclarationTypeName(proposal, mapTypeName(sym.owner).toArray)
                setPackageName(proposal, fieldTypeSymbol.enclosingPackage.fullNameString.toArray)
                setTypeName(proposal, mapTypeName(fieldTypeSymbol).toArray)
                proposal.setName(transformedName.toArray)
                proposal.setCompletion(transformedName.toArray)
                proposal.setFlags(mapModifiers(sym))
                proposal.setReplaceRange(start, end)
                proposal.setTokenRange(start, end)
                proposal.setRelevance(relevance)
                requestor.accept(proposal)
              } else if (sym.isMethod && !sym.isConstructor && sym.name != nme.asInstanceOf_ && sym.name != nme.isInstanceOf_) {
                val proposal =  createProposal(CompletionProposal.METHOD_REF, position)
                val paramNames = sym.tpe.paramss.flatMap(_.map(_.name))
                val paramTypes = sym.tpe.paramss.flatMap(_.map(_.tpe))
                val resultTypeSymbol = sym.tpe.finalResultType.typeSymbol
                val relevance = if (inherited) 20 else if(viaView != compiler.NoSymbol) 10 else 30
                
                val (transformedName, completion) = NameTransformer.decode(sym.name.toString) match {
                  case n@("$asInstanceOf" | "$isInstanceOf") =>
                    val n0 = n.substring(1) 
                    (n0, n0+"[]")
                  case n =>
                    (n, n+"()")
                }
                
                proposal.setDeclarationSignature(javaType(sym.owner.tpe).getSignature.replace('/', '.').toArray)
                proposal.setSignature(javaType(sym.tpe).getSignature.replace('/', '.').toArray)
                setDeclarationPackageName(proposal, sym.enclosingPackage.fullNameString.toArray)
                setDeclarationTypeName(proposal, mapTypeName(sym.owner).toArray)
                setParameterPackageNames(proposal, paramTypes.map(mapParamTypePackageName(_).toArray).toArray)
                setParameterTypeNames(proposal, paramTypes.map(mapParamTypeName(_).toArray).toArray)
                setPackageName(proposal, resultTypeSymbol.enclosingPackage.fullNameString.toArray)
                setTypeName(proposal, mapTypeName(resultTypeSymbol).toArray)
                proposal.setName(transformedName.toArray)
                proposal.setCompletion(completion.toArray)
                proposal.setFlags(mapModifiers(sym))
                proposal.setReplaceRange(start, end)
                proposal.setTokenRange(start, end)
                proposal.setRelevance(relevance)
                proposal.setParameterNames(paramNames.map(_.toString.toArray).toArray)
                requestor.accept(proposal)
              }
            
            case compiler.ScopeMember(sym, tpe, accessible, _) =>
              println("Not handled")
              
            case _ =>
              println("Not handled")
          }
      case None =>
        println("No completions")
    }
  }
  
  override def mapLabelImage(original : Image) = super.mapLabelImage(original)
  
  override def replacementImage = {
    val file = getCorrespondingResource.asInstanceOf[IFile]
    if(file == null)
      null
    else {
      import ScalaImages.{ SCALA_FILE, EXCLUDED_SCALA_FILE }
      val javaProject = JavaCore.create(project.underlying)
      if(javaProject.isOnClasspath(file)) SCALA_FILE else EXCLUDED_SCALA_FILE
    }
  }
}

object InternalCompletionProposalUtils extends ReflectionUtils {
  val icpClazz = classOf[InternalCompletionProposal]
  val setDeclarationPackageNameMethod = getDeclaredMethod(icpClazz, "setDeclarationPackageName", classOf[Array[Char]])
  val setDeclarationTypeNameMethod = getDeclaredMethod(icpClazz, "setDeclarationTypeName", classOf[Array[Char]])
  val setParameterPackageNamesMethod = getDeclaredMethod(icpClazz, "setParameterPackageNames", classOf[Array[Array[Char]]])
  val setParameterTypeNamesMethod = getDeclaredMethod(icpClazz, "setParameterTypeNames", classOf[Array[Array[Char]]])
  val setPackageNameMethod = getDeclaredMethod(icpClazz, "setPackageName", classOf[Array[Char]])
  val setTypeNameMethod = getDeclaredMethod(icpClazz, "setTypeName", classOf[Array[Char]])
  
  def setDeclarationPackageName(icp : InternalCompletionProposal, name : Array[Char]) { setDeclarationPackageNameMethod.invoke(icp, Array(name) : _*) }
  def setDeclarationTypeName(icp : InternalCompletionProposal, name : Array[Char]) { setDeclarationTypeNameMethod.invoke(icp, Array(name) : _*) }
  def setParameterPackageNames(icp : InternalCompletionProposal, names : Array[Array[Char]]) { setParameterPackageNamesMethod.invoke(icp, Array(names) : _*) }
  def setParameterTypeNames(icp : InternalCompletionProposal, names : Array[Array[Char]]) { setParameterTypeNamesMethod.invoke(icp, Array(names) : _*) }
  def setPackageName(icp : InternalCompletionProposal, name : Array[Char]) { setPackageNameMethod.invoke(icp, Array(name) : _*) }
  def setTypeName(icp : InternalCompletionProposal, name : Array[Char]) { setTypeNameMethod.invoke(icp, Array(name) : _*) }
}

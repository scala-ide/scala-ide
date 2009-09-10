/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.javaelements

import java.{ util => ju }

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.SyncVar
import scala.util.control.Breaks._
import scala.tools.nsc.symtab.Flags
import scala.tools.nsc.util.Position

import org.eclipse.jdt.core.Signature
import org.eclipse.jdt.core.compiler.{ CharOperation, InvalidInputException }
import org.eclipse.jdt.core.search.IJavaSearchConstants
import org.eclipse.jdt.internal.codeassist.{ ISearchRequestor, ISelectionRequestor }
import org.eclipse.jdt.internal.codeassist.impl.{ AssistParser, Engine }
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants
import org.eclipse.jdt.internal.compiler.env
import org.eclipse.jdt.internal.compiler.env.{ AccessRestriction, ICompilationUnit }
import org.eclipse.jdt.internal.compiler.parser.{ Scanner, ScannerHelper, TerminalTokens }
import org.eclipse.jdt.internal.core.{ JavaElement, LocalVariable, SearchableEnvironment }

class ScalaSelectionEngine(nameEnvironment : SearchableEnvironment, requestor : ISelectionRequestor, settings : ju.Map[_, _]) extends Engine(settings) with ISearchRequestor {

  var actualSelectionStart : Int = _
  var actualSelectionEnd : Int = _
  var selectedIdentifier : Array[Char] = _

  val acceptedClasses = new ArrayBuffer[(Array[Char], Array[Char], Int)]
  val acceptedInterfaces = new ArrayBuffer[(Array[Char], Array[Char], Int)]
  val acceptedEnums = new ArrayBuffer[(Array[Char], Array[Char], Int)]
  val acceptedAnnotations = new ArrayBuffer[(Array[Char], Array[Char], Int)]

  def select(cu : env.ICompilationUnit, selectionStart : Int, selectionEnd : Int) {
    val scu = cu.asInstanceOf[ScalaCompilationUnit]
  
    val source = scu.getContents()
    
    actualSelectionStart = selectionStart
    actualSelectionEnd = selectionEnd
    val length = 1+selectionEnd-selectionStart
    selectedIdentifier = new Array(length)
    Array.copy(source, selectionStart, selectedIdentifier, 0, length)
    println("selectedIdentifier: "+selectedIdentifier.mkString("", "", ""))

    val ssr = requestor.asInstanceOf[ScalaSelectionRequestor]

    val th = scu.getTreeHolder
    import th._
    import compiler._
    
    def selectFromTypeTree(t : compiler.TypeTree, pos : Position) : compiler.Symbol =
      (t.tpe, t.original) match {
        case (tr : compiler.TypeRef, att : compiler.AppliedTypeTree) =>
          selectFromTypeTree0(tr, att, pos)
        case (_, compiler.TypeBoundsTree(lo : compiler.TypeTree, _)) if lo.pos overlaps pos =>
          selectFromTypeTree(lo, pos)
        case (_, compiler.TypeBoundsTree(_, hi : compiler.TypeTree)) if hi.pos overlaps pos =>
          selectFromTypeTree(hi, pos)
        case _ => t.tpe.typeSymbolDirect
      }

    def selectFromTypeTree0(tr : compiler.TypeRef, att : compiler.AppliedTypeTree, pos : Position) : compiler.Symbol =
      (tr, att) match {
        case (compiler.TypeRef(pre, sym, args0), compiler.AppliedTypeTree(tpt, args1)) =>
          if (pos overlaps tpt.pos)
            tr.typeSymbolDirect
          else {
            val pargs = args0 zip args1
            val arg = pargs.find(pos overlaps _._2.pos)
            arg match {
              case Some((tr2 : compiler.TypeRef, att2 : compiler.AppliedTypeTree)) =>
                selectFromTypeTree0(tr2, att2, pos)
              case Some((tpe, _)) => tpe.typeSymbolDirect
              case _ => tr.typeSymbolDirect
            }
          }
        case _ => tr.typeSymbolDirect
      }

    def acceptType(t : compiler.Symbol) {
      requestor.acceptType(
        t.enclosingPackage.fullNameString.toArray,
        mapTypeName(t).toArray,
        if (t.isTrait) ClassFileConstants.AccInterface else 0,
        false,
        null,
        actualSelectionStart,
        actualSelectionEnd)
    }
    
    def acceptField(f : compiler.Symbol) {
      requestor.acceptField(
        f.enclosingPackage.fullNameString.toArray,
        mapTypeName(f.owner).toArray,
        (if (f.isSetter) compiler.nme.setterToGetter(f.name) else f.name).toString.toArray,
        false,
        null,
        actualSelectionStart,
        actualSelectionEnd)
    }
    
    def acceptMethod(m : compiler.Symbol) {
      val m0 = if (m.isClass || m.isModule) m.primaryConstructor else m
      val owner = m0.owner
      val name = if (m0.isConstructor) owner.name else m0.name
      val paramTypes = m0.tpe.paramss.flatMap(_.map(_.tpe))
      
      requestor.acceptMethod(
        m0.enclosingPackage.fullNameString.toArray,
        mapTypeName(owner).toArray,
        null,
        name.toString.toArray,
        paramTypes.map(mapParamTypePackageName(_).toArray).toArray,
        paramTypes.map(mapParamTypeName(_).toArray).toArray,
        paramTypes.map(mapParamTypeSignature(_)).toArray,
        new Array[Array[Char]](0),
        new Array[Array[Array[Char]]](0),
        m0.isConstructor,
        false,
        null,
        actualSelectionStart,
        actualSelectionEnd)
    }
    
    def acceptLocalDefinition(defn : compiler.Symbol) {
      val parent = ssr.findLocalElement(defn.pos.startOrPoint)
      if (parent != null) {
        val localVar = new LocalVariable(
          parent.asInstanceOf[JavaElement],
          defn.name.toString,
          defn.pos.startOrPoint,
          defn.pos.endOrPoint-1,
          defn.pos.point,
          defn.pos.point+defn.name.toString.length-1,
          compiler.javaType(defn.tpe).getSignature,
          null)
        ssr.addElement(localVar)
      }
    }
    
    val bsf = scu.getSourceFile
    val pos = compiler.rangePos(bsf, actualSelectionStart, actualSelectionStart, actualSelectionEnd+1)

    val typed = new SyncVar[Either[compiler.Tree, Throwable]]
    compiler.askTypeAt(pos, typed)
    typed.get.left.toOption match {
      case Some(tree) => {
        tree match {
          case i : compiler.Ident =>
            println("Ident("+i.name+")")
            println(i.symbol+": "+i.symbol.getClass.getName+" "+i.symbol.pos)
            i.symbol match {
              case c : compiler.ClassSymbol =>
                acceptType(c)
              case m : compiler.ModuleSymbol =>
                acceptType(m)
              case t : compiler.TermSymbol if t.isValueParameter =>
                val typed = new SyncVar[Either[compiler.Tree, Throwable]]
                compiler.askTypeAt(t.owner.pos, typed)
                val ownerTree = typed.get.left.toOption 
                ownerTree match {
                  case Some(compiler.DefDef(_, _, _, paramss, _, _)) =>
                    for(params <- paramss ; param <- params if param.name.toString == t.nameString)
                      acceptLocalDefinition(param.symbol)
                  case Some(compiler.Function(vparams, _)) =>
                    for(param <- vparams if param.name.toString == t.nameString)
                      acceptLocalDefinition(param.symbol)
                  case Some(compiler.Apply(_, _)) =>
                    acceptLocalDefinition(t)
                  case _ =>
                    println("Unhandled: "+t.getClass.getName)
                }
              case t : compiler.TermSymbol if t.isMethod && t.pos.isDefined =>
                ssr.addElement(ssr.findLocalElement(t.pos.startOrPoint))
              case t : compiler.TermSymbol if t.pos.isDefined =>
                acceptLocalDefinition(t)
              case _ =>
                println("Unhandled: "+i.symbol.getClass.getName)
            }
            
          case s : compiler.Select =>
            println("Selector("+s.qualifier+", "+s.name+")")
            if (s.symbol.owner.isAnonymousClass && s.symbol.pos.isDefined) {
              ssr.addElement(ssr.findLocalElement(s.symbol.pos.startOrPoint))
            } else if (s.symbol.hasFlag(Flags.ACCESSOR) || s.symbol.hasFlag(Flags.PARAMACCESSOR)) {
              acceptField(s.symbol)
            } else if (s.symbol.hasFlag(Flags.JAVA) && s.symbol.isModule) {
              acceptType(s.symbol)
            } else {
              acceptMethod(s.symbol)
            }
            
          case t0 : compiler.TypeTree if t0.symbol != null =>
            val t = selectFromTypeTree(t0, pos)
            println("TypeTree("+t.tpe+")")
            val symbol = t.tpe.typeSymbolDirect
            val symbol0 = t.tpe.typeSymbol
            if (!symbol.pos.isDefined) {
              acceptType(symbol0)
            } else {
              val owner = symbol.owner
              if (owner.isClass) {
                if (symbol.isClass) {
                  acceptType(symbol)
                } else if (symbol.isTypeParameter){
                  if (symbol.pos.isDefined) {
                    acceptLocalDefinition(symbol)
                  }
                } else {
                  acceptField(symbol)
                }
              } else if (symbol.pos.isDefined){
                acceptLocalDefinition(symbol)
              }
            }
            
          case _ =>
            println("Unhandled: "+tree.getClass.getName)
        }
      }
      case None =>
        println("No tree")
    }
    
    // only reaches here if no selection could be derived from the parsed tree
    // thus use the selected source and perform a textual type search
    if (!ssr.hasSelection) {
      nameEnvironment.findTypes(selectedIdentifier, false, false, IJavaSearchConstants.TYPE, this)
      
      // accept qualified types only if no unqualified type was accepted
      if(!ssr.hasSelection) {
        def acceptTypes(accepted : ArrayBuffer[(Array[Char], Array[Char], Int)]) {
          if(!accepted.isEmpty){
            for (t <- accepted)
              requestor.acceptType(t._1, t._2, t._3, false, null, actualSelectionStart, actualSelectionEnd)
            accepted.clear
          }
        }
        
        acceptTypes(acceptedClasses)
        acceptTypes(acceptedInterfaces)
        acceptTypes(acceptedAnnotations)
        acceptTypes(acceptedEnums)
      }
    }
  }

  override def acceptType(packageName : Array[Char], simpleTypeName : Array[Char], enclosingTypeNames : Array[Array[Char]], modifiers : Int, accessRestriction : AccessRestriction) {
    val typeName =
      if (enclosingTypeNames == null)
        simpleTypeName
      else
        CharOperation.concat(
          CharOperation.concatWith(enclosingTypeNames, '.'),
          simpleTypeName,
          '.')
    
    if (CharOperation.equals(simpleTypeName, selectedIdentifier)) {
      val flatEnclosingTypeNames =
        if (enclosingTypeNames == null || enclosingTypeNames.length == 0)
          null
        else
          CharOperation.concatWith(enclosingTypeNames, '.')
      if(mustQualifyType(packageName, simpleTypeName, flatEnclosingTypeNames, modifiers)) {
        val accepted = (packageName, typeName, modifiers)
        val kind = modifiers & (ClassFileConstants.AccInterface | ClassFileConstants.AccEnum | ClassFileConstants.AccAnnotation)
        kind match {
          case x if (x == ClassFileConstants.AccAnnotation) || (x == (ClassFileConstants.AccAnnotation | ClassFileConstants.AccInterface)) =>
            acceptedAnnotations += accepted
          case ClassFileConstants.AccEnum =>
            acceptedEnums += accepted
          case ClassFileConstants.AccInterface =>
            acceptedInterfaces += accepted
          case _ =>
            acceptedClasses += accepted
        }
      } else {
        requestor.acceptType(
          packageName,
          typeName,
          modifiers,
          false,
          null,
          actualSelectionStart,
          actualSelectionEnd)
      }
    }
  }
  
  override def getParser() : AssistParser = {
    throw new UnsupportedOperationException();
  }
    
  override def acceptPackage(packageName : Array[Char]) {
    // NOP
  }
  
  override def acceptConstructor(
    modifiers : Int,
    simpleTypeName : Array[Char],
    parameterCount : Int,
    signature : Array[Char],
    parameterTypes : Array[Array[Char]],
    parameterNames : Array[Array[Char]],
    typeModifiers : Int,
    packageName : Array[Char],
    extraFlags : Int,
    path : String,
    accessRestriction : AccessRestriction) {
    // NOP
  }
}

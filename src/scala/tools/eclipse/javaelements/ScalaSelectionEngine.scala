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
import org.eclipse.jdt.internal.compiler.env.{ AccessRestriction, ICompilationUnit }
import org.eclipse.jdt.internal.compiler.parser.{ Scanner, ScannerHelper, TerminalTokens }
import org.eclipse.jdt.internal.core.{ JavaElement, LocalVariable, SearchableEnvironment }

class ScalaSelectionEngine(nameEnvironment : SearchableEnvironment, requestor : ISelectionRequestor, settings : ju.Map[_, _]) extends Engine(settings) with ISearchRequestor {

  var acceptedAnswer : Boolean = _
  var actualSelectionStart : Int = _
  var actualSelectionEnd : Int = _
  var selectedIdentifier : Array[Char] = _

  val acceptedClasses = new ArrayBuffer[(Array[Char], Array[Char], Int)]
  val acceptedInterfaces = new ArrayBuffer[(Array[Char], Array[Char], Int)]
  val acceptedEnums = new ArrayBuffer[(Array[Char], Array[Char], Int)]
  val acceptedAnnotations = new ArrayBuffer[(Array[Char], Array[Char], Int)]

  var noProposal = true

  def select(scu : ScalaCompilationUnit, selectionStart : Int, selectionEnd : Int) {

    val source = scu.getContents()
    
    actualSelectionStart = selectionStart
    actualSelectionEnd = selectionEnd
    val length = 1+selectionEnd-selectionStart
    selectedIdentifier = new Array(length)
    Array.copy(source, selectionStart, selectedIdentifier, 0, length)

    println("selectedIdentifier: "+selectedIdentifier.mkString("", "", ""))
      
    acceptedAnswer = false

    val th = scu.getTreeHolder
    import th._
    
    def selectFromTypeTree(t : compiler.TypeTree, pos : Position) : compiler.Symbol =
      (t.tpe, t.original) match {
        case (tr : compiler.TypeRef, att : compiler.AppliedTypeTree) =>
          selectFromTypeTree0(tr, att, pos)
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

    def typeName(s : compiler.Symbol) : String =
      if (s == compiler.NoSymbol || s.hasFlag(Flags.PACKAGE)) ""
      else {
        val owner = s.owner
        val prefix = if (owner != compiler.NoSymbol && !owner.hasFlag(Flags.PACKAGE)) typeName(s.owner)+"." else ""
        val suffix = if (s.isModuleClass) "$" else ""
        prefix+s.nameString+suffix
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
                requestor.acceptType(
                  c.enclosingPackage.fullNameString.toArray,
                  typeName(c).toArray,
                  if (c.isTrait) ClassFileConstants.AccInterface else 0,
                  false,
                  null,
                  actualSelectionStart,
                  actualSelectionEnd)
                noProposal = false
                acceptedAnswer = true
              case t : compiler.TermSymbol if t.isValueParameter =>
                val typed = new SyncVar[Either[compiler.Tree, Throwable]]
                val ownerTree = compiler.askTypeAt(t.owner.pos, typed)
                typed.get.left.toOption match {
                  case Some(compiler.DefDef(_, _, _, paramss, _, _)) =>
                    for(params <- paramss ; param <- params if param.name.toString == t.nameString) {
                      val ssr = requestor.asInstanceOf[ScalaSelectionRequestor]
                      val parent = ssr.findLocalElement(param.pos.startOrPoint)
                      if (parent != null) {
                        val localVar = new LocalVariable(
                          parent.asInstanceOf[JavaElement],
                          param.name.toString,
                          param.pos.startOrPoint,
                          param.pos.endOrPoint-1,
                          param.pos.startOrPoint,
                          param.pos.startOrPoint+param.name.toString.length-1,
                          compiler.javaType(t.tpe).getSignature,
                          null)
                        ssr.addElement(localVar)
                      }
                    }
                  case _ =>
                    println("Unhandled: "+t.getClass.getName)
                }
              case t : compiler.TermSymbol if t.isMethod =>
                val ssr = requestor.asInstanceOf[ScalaSelectionRequestor]
                ssr.addElement(ssr.findLocalElement(t.pos.startOrPoint))
                noProposal = false
                acceptedAnswer = true
              case t : compiler.TermSymbol =>
                val ssr = requestor.asInstanceOf[ScalaSelectionRequestor]
                val parent = ssr.findLocalElement(t.pos.startOrPoint)
                if (parent != null) {
                  val localVar = new LocalVariable(
                    parent.asInstanceOf[JavaElement],
                    t.name.toString,
                    t.pos.startOrPoint,
                    t.pos.endOrPoint-1,
                    t.pos.startOrPoint,
                    t.pos.startOrPoint+t.name.toString.length-1,
                    compiler.javaType(t.tpe).getSignature,
                    null)
                  ssr.addElement(localVar)
                }
              case _ =>
                println("Unhandled: "+i.symbol.getClass.getName)
            }
          case s : compiler.Select =>
            println("Selector("+s.qualifier+", "+s.name+")")

            if (s.symbol.hasFlag(Flags.ACCESSOR)) {
              requestor.acceptField(
                s.symbol.enclosingPackage.fullNameString.toArray,
                typeName(s.symbol.owner).toArray,
                (if (s.symbol.isGetter) s.symbol.name else compiler.nme.setterToGetter(s.symbol.name)).toString.toArray,
                false,
                null,
                s.symbol.pos.startOrPoint,
                s.symbol.pos.endOrPoint
              )
            } else {
              requestor.acceptMethod(
                s.symbol.enclosingPackage.fullNameString.toArray,
                typeName(s.symbol.toplevelClass).toArray,
                null,
                s.name.toString.toArray,
                new Array[Array[Char]](0),
                s.symbol.tpe.paramTypes.map(compiler.javaType(_).toString.toArray).toArray,
                s.symbol.tpe.paramTypes.map(compiler.javaType(_).getSignature).toArray,
                new Array[Array[Char]](0),
                new Array[Array[Array[Char]]](0),
                false /* method.isConstructor() */,
                false,
                null,
                actualSelectionStart,
                actualSelectionEnd
              )
            }
            noProposal = false
            acceptedAnswer = true
          case t0 : compiler.TypeTree if t0.symbol != null =>
            val t = selectFromTypeTree(t0, pos)
            println("TypeTree("+t.tpe+")")
            val symbol = t.tpe.typeSymbolDirect
            if (!symbol.pos.isDefined) {
              requestor.acceptType(
                symbol.enclosingPackage.fullNameString.toArray,
                typeName(symbol).toArray,
                if (symbol.isTrait) ClassFileConstants.AccInterface else 0,
                false,
                null,
                actualSelectionStart,
                actualSelectionEnd
              )
            } else {
              val owner = symbol.owner
              if (owner.isClass) {
                if (symbol.isClass) {
                  requestor.acceptType(
                    symbol.enclosingPackage.fullNameString.toArray,
                    typeName(symbol).toArray,
                    if (symbol.isTrait) ClassFileConstants.AccInterface else 0,
                    false,
                    null,
                    actualSelectionStart,
                    actualSelectionEnd
                  )
                } else {
                  requestor.acceptField(
                    symbol.enclosingPackage.fullNameString.toArray,
                    typeName(symbol.toplevelClass).toArray,
                    symbol.name.toString.toArray,
                    false,
                    null,
                    symbol.pos.startOrPoint,
                    symbol.pos.endOrPoint
                  )
                }
              } else {
                val ssr = requestor.asInstanceOf[ScalaSelectionRequestor]
                val parent = ssr.findLocalElement(symbol.pos.startOrPoint)
                if (parent != null) {
                  val localVar = new LocalVariable(
                    parent.asInstanceOf[JavaElement],
                    symbol.name.toString,
                    symbol.pos.startOrPoint,
                    symbol.pos.endOrPoint-1,
                    symbol.pos.point,
                    symbol.pos.point+symbol.name.toString.length-1,
                    compiler.javaType(t.tpe).getSignature,
                    null)
                  ssr.addElement(localVar)
                }
              }
            }
            noProposal = false
            acceptedAnswer = true
          case _ =>
            println("Unhandled: "+tree.getClass.getName)
        }
      }
      case None =>
        println("No tree")
    }
    
    // only reaches here if no selection could be derived from the parsed tree
    // thus use the selected source and perform a textual type search
    if (!acceptedAnswer) {
      nameEnvironment.findTypes(selectedIdentifier, false, false, IJavaSearchConstants.TYPE, this)
      
      // accept qualified types only if no unqualified type was accepted
      if(!acceptedAnswer) {
        acceptQualifiedTypes()
      }
    }
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

    // TODO Implement
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
        noProposal = false
        requestor.acceptType(
          packageName,
          typeName,
          modifiers,
          false,
          null,
          actualSelectionStart,
          actualSelectionEnd)
        acceptedAnswer = true
      }
    }
  }

  override def acceptPackage(packageName : Array[Char]) {}
  
  override def getParser() : AssistParser = null
  
  def acceptQualifiedTypes() {

    def acceptTypes(accepted : ArrayBuffer[(Array[Char], Array[Char], Int)]) {
      if(!accepted.isEmpty){
        acceptedAnswer = true
        noProposal = false
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

/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.javaelements

import java.{ util => ju }

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.SyncVar
import scala.util.control.Breaks._

import org.eclipse.jdt.core.Signature
import org.eclipse.jdt.core.compiler.{ CharOperation, InvalidInputException }
import org.eclipse.jdt.core.search.IJavaSearchConstants
import org.eclipse.jdt.internal.codeassist.{ ISearchRequestor, ISelectionRequestor }
import org.eclipse.jdt.internal.codeassist.impl.{ AssistParser, Engine }
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants
import org.eclipse.jdt.internal.compiler.env.{ AccessRestriction, ICompilationUnit }
import org.eclipse.jdt.internal.compiler.parser.{ Scanner, ScannerHelper, TerminalTokens }
import org.eclipse.jdt.internal.core.SearchableEnvironment

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
    
    val bsf = scu.getSourceFile
    val pos = compiler.rangePos(bsf, actualSelectionStart, actualSelectionStart, actualSelectionEnd)
    
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
                this.requestor.acceptType(
                  c.enclosingPackage.fullNameString.toArray,
                  c.toplevelClass.nameString.toArray,
                  0 /* modifiers */,
                  false,
                  null,
                  actualSelectionStart,
                  actualSelectionEnd)
                noProposal = false
                acceptedAnswer = true
              case _ =>
                println("Unhandled: "+i.symbol.getClass.getName)
            }
          case s : compiler.Select =>
            println("Selector("+s.qualifier+", "+s.selector+")")
            println(s.symbol+": "+s.symbol.getClass.getName+" "+s.symbol.pos)
            println(s.symbol.enclosingPackage.fullNameString)
            println(s.symbol.toplevelClass.nameString)

            requestor.acceptMethod(
              s.symbol.enclosingPackage.fullNameString.toArray,
              s.symbol.toplevelClass.nameString.toArray,
              null,
              s.selector.toString.toArray,
              new Array[Array[Char]](0),
              new Array[Array[Char]](0),
              s.symbol.tpe.paramTypes.map(compiler.javaType(_).getSignature).toArray,
              new Array[Array[Char]](0),
              new Array[Array[Array[Char]]](0),
              false /* method.isConstructor() */,
              false,
              null,
              this.actualSelectionStart,
              this.actualSelectionEnd)
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

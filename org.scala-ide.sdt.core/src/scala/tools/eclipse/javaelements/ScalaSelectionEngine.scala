/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.javaelements

import java.{ util => ju }

import ch.epfl.lamp.fjbg.JObjectType
import ch.epfl.lamp.fjbg.JType
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.SyncVar
import scala.util.control.Breaks._
import scala.tools.nsc.symtab.Flags

import org.eclipse.jdt.core.Signature
import org.eclipse.jdt.core.compiler.{ CharOperation, InvalidInputException }
import org.eclipse.jdt.core.search.IJavaSearchConstants
import org.eclipse.jdt.internal.codeassist.{ ISearchRequestor, ISelectionRequestor }
import org.eclipse.jdt.internal.codeassist.impl.{ AssistParser, Engine }
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants
import org.eclipse.jdt.internal.compiler.env
import org.eclipse.jdt.internal.compiler.env.{ AccessRestriction, ICompilationUnit }
import org.eclipse.jdt.internal.compiler.parser.{ Scanner, ScannerHelper, TerminalTokens }
import org.eclipse.jdt.internal.core.{ JavaElement, SearchableEnvironment }

import scala.tools.eclipse.ScalaWordFinder

class ScalaSelectionEngine(nameEnvironment : SearchableEnvironment, requestor : ISelectionRequestor, settings : ju.Map[_, _]) extends Engine(settings) with ISearchRequestor {

  var actualSelectionStart : Int = _
  var actualSelectionEnd : Int = _
  var selectedIdentifier : Array[Char] = _

  val acceptedClasses = new ArrayBuffer[(Array[Char], Array[Char], Int)]
  val acceptedInterfaces = new ArrayBuffer[(Array[Char], Array[Char], Int)]
  val acceptedEnums = new ArrayBuffer[(Array[Char], Array[Char], Int)]
  val acceptedAnnotations = new ArrayBuffer[(Array[Char], Array[Char], Int)]

  def select(cu : env.ICompilationUnit, selectionStart0 : Int, selectionEnd0 : Int) {
    val scu = cu.asInstanceOf[ScalaCompilationUnit]
  
    scu.withCompilerResult({ crh =>
    
      import crh._
      import compiler._
      
      val source = scu.getContents()
      
      val (selectionStart, selectionEnd) =
        if (selectionStart0 <= selectionEnd0)
          (selectionStart0, selectionEnd0)
        else {
          val region = ScalaWordFinder.findWord(source, selectionEnd0)
          (region.getOffset, if (region.getLength > 0) region.getOffset+region.getLength-1 else region.getOffset)
        }
      
      actualSelectionStart = selectionStart
      actualSelectionEnd = selectionEnd
      val length = 1+selectionEnd-selectionStart
      selectedIdentifier = new Array(length)
      Array.copy(source, selectionStart, selectedIdentifier, 0, length)
      println("selectedIdentifier: "+selectedIdentifier.mkString("", "", ""))
  
      val ssr = requestor.asInstanceOf[ScalaSelectionRequestor]
      var fallbackToDefaultLookup = true
  
      def selectFromTypeTree(t : compiler.TypeTree, pos : Position) : compiler.Symbol = {
        val tpe = t.tpe
        val orig = t.original
        (t.tpe, t.original) match {
          case (tr : compiler.TypeRef, att : compiler.AppliedTypeTree) =>
            selectFromTypeTree0(tr, att, pos)
          case (_, compiler.SelectFromTypeTree(qual : compiler.TypeTree, name)) if qual.pos overlaps pos =>
            selectFromTypeTree(qual, pos)
          case (_, compiler.TypeBoundsTree(lo : compiler.TypeTree, _)) if lo.pos overlaps pos =>
            selectFromTypeTree(lo, pos)
          case (_, compiler.TypeBoundsTree(_, hi : compiler.TypeTree)) if hi.pos overlaps pos =>
            selectFromTypeTree(hi, pos)
          case (_, sel : compiler.Select) => sel.symbol
          case (_, ident : compiler.Ident) => ident.symbol
          case (_, compound : compiler.CompoundTypeTree) => {
            if (compound.tpe != null && compound.tpe.typeSymbol != null && compound.tpe.typeSymbol.isRefinementClass)
              compound.tpe.typeSymbol.info match {
                case tpe : RefinedType =>
                  tpe.parents.zip(compound.templ.parents).find {
                    case (tpe, tree) => tree.pos overlaps pos
                  } match {
                    case Some((tpe, tree)) => tpe.typeSymbolDirect
                    case _ => t.tpe.typeSymbolDirect
                  }
                
                case _ => t.tpe.typeSymbolDirect
              }
            else
              t.tpe.typeSymbolDirect
          }
          case _ => t.tpe.typeSymbolDirect
        }
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
      
      def qual(tree : compiler.Tree): Tree = tree.symbol.info match {
        case compiler.analyzer.ImportType(expr) => expr
        case _ => tree
      }
      
      def acceptType(t : compiler.Symbol) {
        acceptTypeWithFlags(t, if (t.isTrait) ClassFileConstants.AccInterface else 0)
      }
      
      def acceptTypeWithFlags(t : compiler.Symbol, jdtFlags : Int) {
        requestor.acceptType(
          t.enclosingPackage.fullName.toArray,
          mapTypeName(t).toArray,
          jdtFlags,
          false,
          null,
          actualSelectionStart,
          actualSelectionEnd)
      }
  
      def acceptField(f : compiler.Symbol) {
        requestor.acceptField(
          f.enclosingPackage.fullName.toArray,
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
          m0.enclosingPackage.fullName.toArray,
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
          val name = if (defn.hasFlag(Flags.PARAM) && defn.hasFlag(Flags.SYNTHETIC)) "_" else defn.name.toString.trim
          val jtype = compiler.javaType(defn.tpe) match {
            case jt if jt == JType.UNKNOWN | jt == JType.ADDRESS | jt == JType.REFERENCE => JObjectType.JAVA_LANG_OBJECT
            case jt => jt
          }
          val localVar = new ScalaLocalVariableElement(
            parent.asInstanceOf[JavaElement],
            name,
            defn.pos.startOrPoint,
            defn.pos.endOrPoint-1,
            defn.pos.point,
            defn.pos.point+name.length-1,
            jtype.getSignature,
            name+" : "+defn.tpe.toString)
          ssr.addElement(localVar)
        }
      }
      
      def isPrimitiveType(sym : compiler.Symbol) = {
        import compiler.definitions._
        sym match {
          case ByteClass | CharClass | ShortClass | IntClass | LongClass | FloatClass | DoubleClass | UnitClass => true
          case _ => false
        }
      }
      
      def isSpecialType(sym : compiler.Symbol) = {
        import compiler.definitions._
        sym match {
          case AnyClass | AnyRefClass | AnyValClass | NothingClass | NullClass => true
          case _ => false
        }
      }
      
      val pos = compiler.rangePos(sourceFile, actualSelectionStart, actualSelectionStart, actualSelectionEnd+1)
  
      val typed = new SyncVar[Either[compiler.Tree, Throwable]]
      compiler.askTypeAt(pos, typed)
      typed.get.left.toOption match {
        case Some(tree) => {
          tree match {
            case i : compiler.Ident =>
              val sym = i.symbol
              sym match {
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
                  println("Unhandled: "+sym.getClass.getName)
              }
              
            case s : compiler.Select if s.symbol != null && s.symbol != NoSymbol =>
              val sym = s.symbol
              if (sym.hasFlag(Flags.JAVA)) {
                if (sym.isModule || sym.isClass)
                  acceptType(sym)
                else if (sym.isMethod)
                  acceptMethod(sym)
                else
                  acceptField(sym)
              } else if (sym.owner.isAnonymousClass && sym.pos.isDefined) {
                ssr.addElement(ssr.findLocalElement(sym.pos.startOrPoint))
              } else if (sym.hasFlag(Flags.ACCESSOR|Flags.PARAMACCESSOR)) {
                acceptField(sym)
              } else
                acceptMethod(sym)
              
            case t0 : compiler.TypeTree if t0.symbol != null =>
              val t = selectFromTypeTree(t0, pos)
              val symbol = t.tpe.typeSymbolDirect
              val symbol0 = t.tpe.typeSymbol
              if (!symbol.pos.isDefined) {
                if (isPrimitiveType(symbol0) || isSpecialType(symbol0) || isSpecialType(symbol))
                  fallbackToDefaultLookup = false
                else
                  acceptType(symbol0)
              } else {
                val owner = symbol.owner
                if (owner.isClass) {
                  if (symbol.isClass) {
                    symbol match {
                      case c : compiler.ClassSymbol =>
                        acceptType(c)
                      case m : compiler.ModuleSymbol =>
                        acceptType(m)
                      case _ =>
                        println("Unhandled: "+tree.getClass.getName)
                    }
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
              
            case a@compiler.Annotated(atp, _) =>
              acceptTypeWithFlags(atp.symbol, ClassFileConstants.AccAnnotation)
              
            case i@compiler.Import(expr, selectors) =>
              def acceptSymbol(sym : compiler.Symbol) {
                sym match {
                  case c : compiler.ClassSymbol =>
                    acceptType(c)
                  case m : compiler.ModuleSymbol =>
                    acceptType(m)
                  case f : compiler.TermSymbol if f.hasFlag(Flags.ACCESSOR|Flags.PARAMACCESSOR) =>
                    acceptField(f)
                  case m : compiler.TermSymbol if m.isMethod =>
                    acceptMethod(m)
                  case f : compiler.TermSymbol =>
                    acceptField(f)
                  case t : compiler.TypeSymbol =>
                    acceptField(t)
                  case _ =>
                    println("Unhandled: "+tree.getClass.getName)
                }
              }
            
              val q = qual(i)
              if (q.pos overlaps pos) {
                def findInSelect(t : compiler.Tree) : Tree = t match {
                  case Select(qual, _) if qual.pos.overlaps(pos) => findInSelect(qual)
                  case _ => t
                }
                val tree = findInSelect(q)
                val sym = tree.symbol
                acceptSymbol(sym) 
              } else
                selectors.find({ case compiler.ImportSelector(name, pos, _, _) => pos >= selectionStart && pos+name.length-1 <= selectionEnd }) match {
                  case Some(compiler.ImportSelector(name, _, _, _)) =>
                    val base = compiler.typer.typedQualifier(q).tpe
                    val sym0 = base.member(name) match {
                      case NoSymbol => base.member(name.toTypeName)
                      case s => s
                    }
                    val syms = if (sym0.hasFlag(Flags.OVERLOADED)) sym0.alternatives  else List(sym0)
                    syms.foreach(acceptSymbol)
                  case _ =>
                }
              
            case l@(_ : ValDef | _ : Bind | _ : ClassDef | _ : ModuleDef | _ : TypeDef | _ : DefDef) =>
              val sym = l.symbol 
              if (sym.isLocal)  
                acceptLocalDefinition(l.symbol)
              else
                ssr.addElement(ssr.findLocalElement(pos.startOrPoint))
              
            case _ =>
              println("Unhandled: "+tree.getClass.getName)
          }
        }
        case None =>
          println("No tree")
      }
      
      if (!ssr.hasSelection && fallbackToDefaultLookup) {
        // only reaches here if no selection could be derived from the parsed tree
        // thus use the selected source and perform a textual type search
        
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
    })
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

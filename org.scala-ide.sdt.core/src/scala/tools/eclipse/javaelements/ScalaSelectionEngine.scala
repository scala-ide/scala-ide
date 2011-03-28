/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse
package javaelements

import java.{ util => ju }

import ch.epfl.lamp.fjbg.JObjectType
import ch.epfl.lamp.fjbg.JType
import scala.collection.mutable.ArrayBuffer
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

import util.Logger

class ScalaSelectionEngine(nameEnvironment: SearchableEnvironment, requestor: ISelectionRequestor, settings: ju.Map[_, _]) extends Engine(settings) with ISearchRequestor with Logger {

  var actualSelectionStart: Int = _
  var actualSelectionEnd: Int = _
  var selectedIdentifier: Array[Char] = _

  val acceptedClasses = new ArrayBuffer[(Array[Char], Array[Char], Int)]
  val acceptedInterfaces = new ArrayBuffer[(Array[Char], Array[Char], Int)]
  val acceptedEnums = new ArrayBuffer[(Array[Char], Array[Char], Int)]
  val acceptedAnnotations = new ArrayBuffer[(Array[Char], Array[Char], Int)]

  def select(cu: env.ICompilationUnit, selectionStart0: Int, selectionEnd0: Int) {
    val scu = cu.asInstanceOf[ScalaCompilationUnit]

    scu.doWithSourceFile { (src, compiler) =>

      import compiler.{ log => _, _ }

      val source = scu.getContents()

      val (selectionStart, selectionEnd) =
        if (selectionStart0 <= selectionEnd0)
          (selectionStart0, selectionEnd0)
        else {
          val region = ScalaWordFinder.findWord(source, selectionEnd0)
          (region.getOffset, if (region.getLength > 0) region.getOffset + region.getLength - 1 else region.getOffset)
        }

      actualSelectionStart = selectionStart
      actualSelectionEnd = selectionEnd
      val length = 1 + selectionEnd - selectionStart
      selectedIdentifier = new Array(length)
      Array.copy(source, selectionStart, selectedIdentifier, 0, length)
      log("selectedIdentifier: " + selectedIdentifier.mkString("", "", ""))

      val ssr = requestor.asInstanceOf[ScalaSelectionRequestor]
      var fallbackToDefaultLookup = true

      def qual(tree: compiler.Tree): Tree = tree.symbol.info match {
        case compiler.analyzer.ImportType(expr) => expr
        case _ => tree
      }

      /** Delay the action. Necessary so that the payload is run outside of 'ask'. */
      class Cont(f: () => Unit) {
        def apply() = f()
      }
      
      object Cont {
        def apply(next: => Unit) = new Cont({ () => next })
        val Noop = new Cont(() => ())
        implicit def noop(v: Any) = Noop
      }

      def acceptType(t: compiler.Symbol) = {
        acceptTypeWithFlags(t, if (t.isTrait) ClassFileConstants.AccInterface else 0)
      }

      def acceptTypeWithFlags(t: compiler.Symbol, jdtFlags: Int) = {
        val packageName = t.enclosingPackage.fullName.toArray
        val typeName = mapTypeName(t).toArray
        Cont(requestor.acceptType(
          packageName,
          typeName,
          jdtFlags,
          false,
          null,
          actualSelectionStart,
          actualSelectionEnd))
      }

      def acceptField(f: compiler.Symbol) = {
        val packageName = f.enclosingPackage.fullName.toArray
        val typeName = mapTypeName(f.owner).toArray
        val name = (if (f.isSetter) compiler.nme.setterToGetter(f.name) else f.name).toString.toArray
        Cont(requestor.acceptField(
          packageName,
          typeName,
          name,
          false,
          null,
          actualSelectionStart,
          actualSelectionEnd))
      }

      def acceptMethod(m: compiler.Symbol) = {
        val m0 = if (m.isClass || m.isModule) m.primaryConstructor else m
        val owner = m0.owner
        val isConstructor = m0.isConstructor
        val name = if (isConstructor) owner.name else m0.name
        val paramTypes = m0.tpe.paramss.flatMap(_.map(_.tpe))

        val packageName = m0.enclosingPackage.fullName.toArray
        val typeName = mapTypeName(owner).toArray
        val parameterPackageNames = paramTypes.map(mapParamTypePackageName(_).toArray).toArray
        val parameterTypeNames = paramTypes.map(mapParamTypeName(_).toArray).toArray
        val parameterSignatures = paramTypes.map(mapParamTypeSignature(_)).toArray
        Cont(requestor.acceptMethod(
          packageName,
          typeName,
          null,
          name.toChars,
          parameterPackageNames,
          parameterTypeNames,
          parameterSignatures,
          Array.empty,
          Array.empty,
          isConstructor,
          false,
          null,
          actualSelectionStart,
          actualSelectionEnd))
      }

      def acceptLocalDefinition(defn: compiler.Symbol) {
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
            defn.pos.endOrPoint - 1,
            defn.pos.point,
            defn.pos.point + name.length - 1,
            jtype.getSignature,
            name + " : " + defn.tpe.toString)
          ssr.addElement(localVar)
        }
      }

      def isPrimitiveType(sym: compiler.Symbol) = {
        import compiler.definitions._
        sym match {
          case ByteClass | CharClass | ShortClass | IntClass | LongClass | FloatClass | DoubleClass | UnitClass => true
          case _ => false
        }
      }

      def isSpecialType(sym: compiler.Symbol) = {
        import compiler.definitions._
        sym match {
          case AnyClass | AnyRefClass | AnyValClass | NothingClass | NullClass => true
          case _ => false
        }
      }

      val pos = compiler.rangePos(src, actualSelectionStart, actualSelectionStart, actualSelectionEnd + 1)

      val typed = new compiler.Response[compiler.Tree]
      compiler.askTypeAt(pos, typed)
      val typedRes = typed.get
      import Cont.noop
      val cont: Cont = compiler.ask { () =>
        typedRes.left.toOption match {
          case Some(tree) => {
            tree match {
              case i: compiler.Ident => i.symbol match {
                case c: compiler.ClassSymbol => acceptType(c)
                case m: compiler.ModuleSymbol => acceptType(m)
                case t: compiler.TermSymbol if t.pos.isDefined =>
                  if (t.isMethod) acceptMethod(t) else if (t.isLocal) acceptLocalDefinition(t) else acceptField(t)
                case sym => log("Unhandled: " + sym.getClass.getName)
              }

              case r: compiler.Literal => r.symbol match {
                case m: compiler.ModuleSymbol => acceptType(m)
                case t: compiler.TermSymbol if !t.isMethod && t.pos.isDefined =>
                  if (t.isLocal) acceptLocalDefinition(t) else acceptField(t)
                case _ =>
              }

              case s: compiler.Select if s.symbol != null && s.symbol != NoSymbol =>
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
                } else if (sym.hasFlag(Flags.ACCESSOR | Flags.PARAMACCESSOR)) {
                  acceptField(sym)
                } else if (sym.isModule || sym.isClass) {
                  acceptType(sym)
                } else
                  acceptMethod(sym)

              case a@compiler.Annotated(atp, _) =>
                acceptTypeWithFlags(atp.symbol, ClassFileConstants.AccAnnotation)

              case i@compiler.Import(expr, selectors) =>
                def acceptSymbol(sym: compiler.Symbol) {
                  sym match {
                    case c: compiler.ClassSymbol =>
                      acceptType(c)
                    case m: compiler.ModuleSymbol =>
                      acceptType(m)
                    case f: compiler.TermSymbol if f.hasFlag(Flags.ACCESSOR | Flags.PARAMACCESSOR) =>
                      acceptField(f)
                    case m: compiler.TermSymbol if m.isMethod =>
                      acceptMethod(m)
                    case f: compiler.TermSymbol =>
                      acceptField(f)
                    case t: compiler.TypeSymbol =>
                      acceptField(t)
                    case _ =>
                      log("Unhandled: " + tree.getClass.getName)
                  }
                }

                val q = qual(i)
                if (q.pos overlaps pos) {
                  def findInSelect(t: compiler.Tree): Tree = t match {
                    case Select(qual, _) if qual.pos.overlaps(pos) => findInSelect(qual)
                    case _ => t
                  }
                  val tree = findInSelect(q)
                  val sym = tree.symbol
                  acceptSymbol(sym)
                } else
                  selectors.find({ case compiler.ImportSelector(name, pos, _, _) => pos >= selectionStart && pos + name.length - 1 <= selectionEnd }) match {
                    case Some(compiler.ImportSelector(name, _, _, _)) =>
                      val base = compiler.typer.typedQualifier(q).tpe
                      val sym0 = base.member(name) match {
                        case NoSymbol => base.member(name.toTypeName)
                        case s => s
                      }
                      val syms = if (sym0.hasFlag(Flags.OVERLOADED)) sym0.alternatives else List(sym0)
                      syms.map(acceptSymbol)
                    case _ =>
                  }

              case l@(_: ValDef | _: Bind | _: ClassDef | _: ModuleDef | _: TypeDef | _: DefDef) =>
                val sym = l.symbol
                if (sym.isLocal)
                  acceptLocalDefinition(l.symbol)
                else
                  ssr.addElement(ssr.findLocalElement(pos.startOrPoint))

              case _ =>
                log("Unhandled: " + tree.getClass.getName)
            }
          }
          case None =>
            log("No tree")
        }
      }
      cont()

      if (!ssr.hasSelection && fallbackToDefaultLookup) {
        // only reaches here if no selection could be derived from the parsed tree
        // thus use the selected source and perform a textual type search

        nameEnvironment.findTypes(selectedIdentifier, false, false, IJavaSearchConstants.TYPE, this)

        // accept qualified types only if no unqualified type was accepted
        if (!ssr.hasSelection) {
          def acceptTypes(accepted: ArrayBuffer[(Array[Char], Array[Char], Int)]) {
            if (!accepted.isEmpty) {
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
  }

  override def acceptType(packageName: Array[Char], simpleTypeName: Array[Char], enclosingTypeNames: Array[Array[Char]], modifiers: Int, accessRestriction: AccessRestriction) {
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
      if (mustQualifyType(packageName, simpleTypeName, flatEnclosingTypeNames, modifiers)) {
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

  override def getParser(): AssistParser = {
    throw new UnsupportedOperationException();
  }

  override def acceptPackage(packageName: Array[Char]) {
    // NOP
  }

  override def acceptConstructor(
    modifiers: Int,
    simpleTypeName: Array[Char],
    parameterCount: Int,
    signature: Array[Char],
    parameterTypes: Array[Array[Char]],
    parameterNames: Array[Array[Char]],
    typeModifiers: Int,
    packageName: Array[Char],
    extraFlags: Int,
    path: String,
    accessRestriction: AccessRestriction) {
    // NOP
  }
}

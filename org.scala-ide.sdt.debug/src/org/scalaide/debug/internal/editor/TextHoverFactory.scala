package org.scalaide.debug.internal.editor

import scala.util.Try
import scala.reflect.internal.util.{Position, RangePosition, OffsetPosition, SourceFile}
import org.scalaide.util.internal.eclipse.EclipseUtils.PimpedRegion
import org.scalaide.ui.internal.editor.ScalaHover
import org.scalaide.ui.editor.extensionpoints.{ TextHoverFactory => TextHoverFactoryInterface }
import org.scalaide.debug.internal.ScalaDebugger
import org.scalaide.debug.internal.model.{ScalaThisVariable, ScalaStackFrame}
import org.scalaide.core.internal.jdt.model.ScalaCompilationUnit
import org.scalaide.core.compiler.ScalaPresentationCompiler
import org.eclipse.swt.widgets.Shell
import org.eclipse.jface.text.ITextViewer
import org.eclipse.jface.text.ITextHoverExtension2
import org.eclipse.jface.text.ITextHoverExtension
import org.eclipse.jface.text.ITextHover
import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.IInformationControlExtension2
import org.eclipse.jface.text.IInformationControlCreator
import org.eclipse.jface.text.DefaultInformationControl
import org.eclipse.jdt.internal.debug.ui.ExpressionInformationControlCreator
import org.eclipse.debug.core.model.IVariable

class TextHoverFactory extends TextHoverFactoryInterface {
  def createFor(scu: ScalaCompilationUnit): ITextHover = new ScalaHover(scu) with ITextHoverExtension with ITextHoverExtension2 {
    var stringWasReturnedAtGetHoverInfo2 = false

    override def getHoverInfo2(viewer: ITextViewer, region: IRegion): AnyRef = {
      icu.withSourceFile{(src, compiler) =>
        import compiler._

        val resp = new Response[Tree]
        askTypeAt(region.toRangePos(src), resp)

        stringWasReturnedAtGetHoverInfo2 = false

        for {
          t <- resp.get.left.toOption
          stackFrame <- Option(ScalaDebugger.currentStackFrame)
          variable <- StackFrameVariableOfTreeFinder.find(src, compiler, stackFrame)(t)
        } yield variable
      }.flatten getOrElse {
        stringWasReturnedAtGetHoverInfo2 = true
        super.getHoverInfo(viewer, region)
      }
    }

    override def getHoverControlCreator: IInformationControlCreator =
      if(stringWasReturnedAtGetHoverInfo2)
        new IInformationControlCreator {
          def createInformationControl(parent: Shell) =
            new StringHandlingInformationControlExtension2(parent)
        }
      else  /* An IVariable was returned. */
        new ExpressionInformationControlCreator
  }

  class StringHandlingInformationControlExtension2(parent: Shell)
  extends DefaultInformationControl(parent)
  with IInformationControlExtension2 {
    override def setInput(input: AnyRef) {
      setInformation(input.asInstanceOf[String])
    }
  }
}


object StackFrameVariableOfTreeFinder {
  def find(src: SourceFile, compiler: ScalaPresentationCompiler, stackFrame: ScalaStackFrame)(t: compiler.Tree): Option[IVariable] = {
    import compiler.{Try => _, _}

    // ---------------- HELPERS ---------------------------------
    /////////////////////////////////////////////////////////////

    def optSymbol(symbolProducer: => Symbol) =
      Option(symbolProducer) filterNot(_ == NoSymbol)

    def treeAt(pos: Position) = {
      val resp = new Response[Tree]
      askTypeAt(pos, resp)
      resp.get.left.toOption
    }

    // StackFrame line numbering is 1-based, while SourceFile line numbering is 0-based. Hence the "- 1".
    lazy val sfLineNumber = Try{stackFrame.getLineNumber}.filter(_ > 0).map(_ - 1).toOption

    lazy val stackFramePos = sfLineNumber.map {ln =>
      new OffsetPosition(src, src.skipWhitespace(src.lineToOffset(ln)))
    }

    def isStackFrameWithin(range: Position) = stackFramePos map {range includes _} getOrElse false


    /** Here we use 'template' as an umbrella term to refer to any entity that exposes a 'this'
     *  variable in the stack frame. This 'this' variable will contain the fields of this template.
     *  Also if an instance X of a template is enclosed by an instance Y of a template, then Y is
     *  accessible from X, via the 'outer' field of X.
     *
     *  Currently this file considers classes, objects, traits & closures to be templates.
     *
     *  Since the stack frame only reveals its line number (and not its position within the line),
     *  sometimes it is difficult to pinpoint, with certainty, the template that encloses its
     *  position.
     *
     *  This function returns the certainly inner-most template that encloses the line of the stack
     *  frame. None is returned if we cannot be certain.
     */
    def innerMostTemplateCertainlyEnclosingSfPos: Option[Symbol] = sfLineNumber flatMap {sfLine =>
      val sfPos = src.position(src lineToOffset sfLine)
      val enclTemplTree = locateIn(parseTree(src), sfPos,
          t => t.isInstanceOf[ClassDef] || t.isInstanceOf[ModuleDef] || t.isInstanceOf[Function])

      if(enclTemplTree == EmptyTree)
        None
      else {
        val templStartLine = src.offsetToLine(enclTemplTree.pos.start)
        val templEndLine = src.offsetToLine(enclTemplTree.pos.end)
        if(sfLine == templStartLine || sfLine == templEndLine)
          None  // Because in these lines, statements within other templates may exist as well.
        else
          treeAt(enclTemplTree.pos) flatMap {t => optSymbol(t.symbol)}
      }
    }

    def enclosingTemplOf(s: Symbol) = optSymbol {s enclosingSuchThat {e =>
      e != s && (e.isClass || e.isTrait || e.isModuleOrModuleClass || e.isAnonymousFunction)}
    }

    def isFieldAccessibleAtStackFrame(field: Symbol) = optSymbol(field.owner) map {owner =>
      isStackFrameWithin(owner.pos)
    } getOrElse false

    def findVariableFromLocalVars(sym: Symbol, disableVarAccessibilityCheck: Boolean = false) = {
      val isLocalVarAccessibleAtStackFrame = (for {
        encloser <- optSymbol(sym.enclosingSuchThat{x => x.isMethod || x.isAnonymousFunction})
      } yield isStackFrameWithin(encloser.pos)) getOrElse false

      if(isLocalVarAccessibleAtStackFrame || disableVarAccessibilityCheck)
        for {
          localVars <- Try{stackFrame.getVariables}.toOption
          foundVar <- localVars.find{_.getName == sym.name.decoded} orElse
            localVars.find{_.getName.split('$')(0) == sym.name.decoded}  // for variables of enclosing methods
        } yield foundVar
      else None
    }

    def findVariableFromFieldsOf(variable: IVariable, sym: Symbol, varMatcher: IVariable => Boolean = null) = {
      def stackFrameCompatibleNameOf(sym: Symbol): Name = {  // Based on trial & error. May need more work.
        var name = sym.name.toTermName
        if(sym.hasLocalFlag) {
          // name = name.dropLocal
          // TODO: The commented line above can be used instead of the one below, when scala 2.10 support is dropped.
          name = nme dropLocalSuffix name
        }
        name.decodedName
      }

      def cleanBinaryName(name: String) = nme originalName newTermName(name)

      val variableMatcher = if(varMatcher != null)
        varMatcher
      else
        (v: IVariable) => cleanBinaryName(v.getName) == stackFrameCompatibleNameOf(sym)

      for {
        value <- Try{variable.getValue}.toOption
        foundFieldVariable <- value.getVariables.find(variableMatcher)
      } yield foundFieldVariable
    }

    def thisOfStackFrame = Try{stackFrame.getVariables}.toOption flatMap {sfVars =>
      sfVars.find(_.isInstanceOf[ScalaThisVariable]) /* doesn't match in traits */ orElse
        sfVars.find(_.getName == "$this")  // a hack used to access the this when in traits
    }

    def findVariableFromFieldsOfThis(sym: Symbol, varMatcher: IVariable => Boolean = null) =
       thisOfStackFrame flatMap {ths => findVariableFromFieldsOf(ths, sym, varMatcher)}

    // ---------------- END OF HELPERS --------------------------
    /////////////////////////////////////////////////////////////

    Option(t.symbol) flatMap {sym =>
      val isAVariableOrField = sym.isVal || sym.isVar || sym.isAccessor
      if(! isAVariableOrField) None
      else t match {
        case Select(ths: This, _) =>
          def tryGetOuter(inner: IVariable) = for {
            innerVal <- Try{inner.getValue}.toOption
            outer <- innerVal.getVariables.find(_.getName == "$outer")
          } yield outer

          (for {
            sfEnclTempl <- innerMostTemplateCertainlyEnclosingSfPos
            requestedThisTempl <- Option(ths.symbol)
            thisOfSf <- thisOfStackFrame
          } yield {

            // Symbol equality, except if a param is a module & the other its module class, still returns true.
            def templSymbolsMatch(s1: Symbol, s2: Symbol) =
              s1 == s2 ||
              (s1.isModule && (s1.moduleClass == s2)) ||
              (s2.isModule && (s1 == s2.moduleClass)) ||
              //
              // Only in the test case, sometimes two different Symbol objects are created for the same thing,
              // causing s1 == s2 to be false. The below clause is thus added to make the tests pass.
              s1.fullLocationString == s2.fullLocationString

            if(templSymbolsMatch(requestedThisTempl, sfEnclTempl))
              findVariableFromFieldsOfThis(sym) orElse {
                // Non-field class parameters are sometimes only accessible as local vars.
                findVariableFromLocalVars(sym, disableVarAccessibilityCheck = true)}
            else {
              var currOuter = tryGetOuter(thisOfSf)
              var currEncl = enclosingTemplOf(sfEnclTempl)
              while(currOuter.isDefined && currEncl.isDefined && (! templSymbolsMatch(currEncl.get, requestedThisTempl))) {
                currOuter = tryGetOuter(currOuter.get)
                currEncl = enclosingTemplOf(currEncl.get)
              }
              if(currOuter.isDefined && currEncl.isDefined)
                findVariableFromFieldsOf(currOuter.get, sym)
              else None
            }
          }).flatten
        case Select(_, _) => None
        case _ =>
          if(sym.isLocal) {
            findVariableFromLocalVars(sym) orElse
            //
            // In closures, local vars of enclosing methods are stored as fields with mangled names.
            findVariableFromFieldsOfThis(sym, varMatcher = {_.getName.startsWith(sym.decodedName + "$")})
          } else {
            if(isFieldAccessibleAtStackFrame(sym))
              findVariableFromFieldsOfThis(sym) orElse {
                // Non-field class parameters are sometimes only accessible as local vars.
                findVariableFromLocalVars(sym, disableVarAccessibilityCheck = true)}
            else None
          }
      }
    }
  }
}

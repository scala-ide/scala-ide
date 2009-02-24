/*
 * Copyright 2005-2009 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$
 
package scala.tools.editor

import scala.annotation.unchecked.uncheckedStable
import scala.collection.jcl.{LinkedHashMap,LinkedHashSet}
import scala.collection.mutable.ListBuffer

import scala.tools.nsc.util
import scala.tools.nsc.ast.parser.Tokens
import scala.tools.nsc.io.{AbstractFile,PlainFile,ZipArchive}
import scala.tools.nsc.symtab.Flags
import scala.tools.nsc.util._
 
trait TypersPresentations extends scala.tools.editor.Presentations {
  private val closeComment = "*/"
  val OverrideIndicator : AnnotationKind
  val classStyle = Style("class").foreground(colors.mocha).style
  val traitStyle = Style("trait").parent(classStyle).italics.style
  val typeStyle = Style("type").parent(classStyle).bold.style
  val objectStyle = Style("object").parent(classStyle).underline.style
  val packageStyle = Style("package").parent(objectStyle).bold.style
  
  val valStyle = Style("val").foreground(colors.blueberry).style
  val varStyle = Style("var").parent(valStyle).underline.style
  val defStyle = Style("def").foreground(colors.ocean).style
  val argStyle = Style("arg").parent(valStyle).italics.style
  import nsc.Global

  trait IdeRef {
    def hyperlink : Unit
    def hover : Option[RandomAccessSeq[Char]]
    def symbol : Option[Global#Symbol]
  }

  case object NoRef extends IdeRef {
    def hyperlink : Unit = {}
    def hover : Option[RandomAccessSeq[Char]] = None
    override def symbol = None
  }
  
  type Project <: ProjectImpl 
  trait ProjectImpl extends super.ProjectImpl with Typers {
    override val compiler : Compiler 

    trait FileIdeRef extends IdeRef {
      def file : File  
      def refOffset : Option[Int]
      override def hyperlink = refOffset match {
        case None =>
        case Some(offset) => 
          openAndSelect(file, offset)
      }
      override def hover = {
        val sym = adapt(symbol)
        sym.map(documentation).getOrElse(None)
      }
    }
    
    import compiler.definitions._
    import compiler.nme

    def adapt(sym : Option[Global#Symbol]) : Option[compiler.Symbol] = sym match {
    case None => None
    case Some(sym : compiler.Symbol) => Some(sym)
    case Some(sym) if sym.name.toString == nme.ROOTPKG.toString => Some(RootPackage)
    case Some(sym) if sym.name.toString == nme.ROOT.toString => Some(RootClass)
    case Some(sym) if sym.name.toString == nme.NOSYMBOL.toString => None
    case Some(sym) =>
      var name = compiler.newTermName(sym.name.toString)
      if (sym.name.isTypeName) name = name.toTypeName
      adapt(Some(sym.owner)).map(_.info.decls.lookup(name)) match {
      case Some(compiler.NoSymbol) => None
      case s => s
      }
    }
    trait Compiler extends super.Compiler {self : compiler.type =>
      override def loadSource(file : AbstractFile) = {
        super.loadSource(file) match {
        case None => None 
        case ret @ Some(unit) =>
          var keys = sourceMap.keySet
          val map = sourceMap(file)._2
          sourceMap(file) = (unit.body,map)
          map.clear // populate the map.
          class visitor extends walker.Visitor {
            def contains(pos : Position) = map.contains(pos.offset.get)
            def apply(pos : Position) = map(pos.offset.get)
            def update(pos : Position, sym : Symbol) : Unit = {
              assert(pos.source.get.file == file)
              map(pos.offset.get) = sym
            }
          }  
          walker.walk(unit.body, new visitor)(offset => unit.source.identifier(offset, compiler))
          ret
        }
      }
    }
    import compiler._
    private val sourceMap = new LinkedHashMap[AbstractFile,(Tree,LinkedHashMap[Int,Symbol])] {
      override def default(file : AbstractFile) = {
        val map = new LinkedHashMap[Int,Symbol]
        this(file) = (EmptyTree,map)
        (null,map)
      }
    }
    private def strip(string : RandomAccessSeq[Char]) : RandomAccessSeq[Char] = {
      val buf = new StringBuilder
      var idx = 0
      while (idx < string.length) string(idx) match {
      case '*' => idx = idx + 1
      case '@' =>
        buf append "<br><b>@"
        idx = idx + 1  
        while (idx < string.length && compiler.isIdentifierPart(string(idx))) {
          buf append(string(idx)) 
          idx = idx + 1
        }
        buf append "</b>"
      case c => buf append c; idx = idx + 1
      }
      buf
    }

    private var magicName0 : compiler.Name = _
    private def magicName = {
      if (magicName0 == null) magicName0 = compiler.newTermName("__magic__sauce__")
      magicName0
    }   
    import scala.tools.nsc.symtab.SymbolWalker
    private object walker extends SymbolWalker {
      lazy val global : compiler.type = compiler
    }
    trait IdentifierPositionImpl extends super.IdentifierPositionImpl with FileIdeRef {
      override def file = owner.file
      override def refOffset = if (isValid) Some(absolute) else None
      override def symbol = {
        if (owner != null && owner.isValid) owner.decode(this) else None
      }
    }
    private def loadSource(source : AbstractFile) = tryLockTyper{
      compiler.loadSource(source)
      analyzer0.finishTyping
      compiler.finishTyping
      flushTyper // just in case we dirtied something.
    }    
    protected def javaRef(symbol : Symbol) : IdeRef
    protected def fileFor(sym : Symbol) : Option[Project#File]
    private def decode(symbol : Symbol) : IdeRef = {
      symbol.info // force completion
      val source = symbol.sourceFile
      if ((symbol hasFlag Flags.JAVA) || ((source ne null) && source.name.endsWith(".java")))
        javaRef(symbol)
      else 
        symbol.pos match {
          case pos : IdentifierPositionImpl => pos
          case NoPosition => 
            if (source eq null) return NoRef
            if (!source.name.endsWith(".scala")) return NoRef
            // force load the file!
            loadSource(source)
            if (symbol.pos == NoPosition) return NoRef
            else return decode(symbol)
          case OffsetPosition(source,offset) => fileFor(symbol) match {
            case None => NoRef
            case Some(f) => 
              val symbol0 = symbol
              new FileIdeRef {
                override def file = f.asInstanceOf[ProjectImpl.this.File]
                override def refOffset = Some(offset)
                override def symbol = Some(adapt(Some(symbol0)).getOrElse(compiler.NoSymbol))
              }
          }
        }
    }
    private def isMagicPhase = currentTyped.map[Boolean](_.isInstanceOf[MagicProcessor]).getOrElse(false)
    
    type ParseNode <: ParseNodeImpl
    trait ParseNodeImpl extends super.ParseNodeImpl {selfX:ParseNode=>
      def self : ParseNode
      // some positions are id positions.
      private var data : List[(IdentifierPosition,Symbol)] = null
      private var ovr : TypersPresentations.this.Annotation = _

      protected def overriding : Option[Symbol] = {
        import compiler.MemberDef
        import compiler.NoSymbol
        if (resultTypeInfo.isEmpty) None else resultTypeInfo.get.last match {
        case tree : MemberDef if (tree.symbol != null && tree.symbol != NoSymbol) =>
          try {
            val list = tree.symbol.allOverriddenSymbols.filter(_ != tree.symbol)
            if (!list.isEmpty) {
              Some(list.head)
            } else None
          } catch {
            case ex => logError(ex)
            None
          }
        case _ => None
        }
      }
      override protected def destroy0 : Unit = {
        super.destroy0
        if (ovr != null) file.delete(ovr)
        data = null
      }
      protected def resetData = data = null
      protected def refreshData = if (data == null) { // we are in the UI thread.
        if (ovr != null) file.delete(ovr)
        val overriding = this.overriding
        ovr = if (overriding.isDefined) {
          file.Annotation(OverrideIndicator, (overriding.get).fullNameString, Some(absolute), length)
        } else null.asInstanceOf[TypersPresentations.this.Annotation]
        data = Nil
        class visitor extends walker.Visitor {
          def contains(pos : Position) = data.find(p => p._1 == pos).isDefined
          def apply(pos : Position) = data.find(p => p._1 == pos).get._2
          def update(pos : Position, sym : Symbol) : Unit =
            data = (pos.asInstanceOf[IdentifierPositionImpl].self, sym) :: data
        }
        //highlightChanged
        //file.invalidate0(offset.get,offset.get+length)
        resultTypeInfo.foreach(_.foreach(t => walker.walk(t, new visitor){
        case pos : IdentifierPositionImpl if pos.isValid => 
          val file = pos.file
          Some(file.tokenFor(pos.absolute).text.toString)
        case _ => None
        }))
      }
      def decode(pos : util.Position) : Option[Symbol] = {
        if (data == null) {
          if (jobIsAsync) return None
          refreshData
        }
        data.find(p => p._1 == pos).map(_._2)
      }
    }
    type File <: FileImpl
    trait FileA extends super[ProjectImpl].FileImpl {selfX : File => }
    trait FileB extends super[Typers]    .FileImpl {selfX : File => }
    trait FileImpl extends FileA with FileB  {selfX:File=>
      override def loaded = {
        sourceMap.removeKey(nscFile) // force refresh.
        super.loaded
      }
      def unloadedBody : Tree = {
        if (!editing && !sourceMap.contains(nscFile)) loadSource(nscFile)
        sourceMap.get(nscFile).map(_._1).getOrElse(EmptyTree)
      }
      
      def outlineTrees : List[Tree]
      def parseChanged(parseNode : ParseNode) = {}
      def topSymbols : List[compiler.Symbol] = {
        var ret : List[compiler.Symbol] = Nil
        var c : ScopeClient = null;
        def f(tree : compiler.Tree) : Unit = tree match {
        case compiler.PackageDef(_,body) => body.foreach(f)
        case compiler.ClassDef(_,_,_,_) | compiler.ModuleDef(_,_,_) => 
          if (tree.symbol != null && tree.symbol != compiler.NoSymbol) 
            ret = tree.symbol :: ret
        case _ =>
        }
        if (!editing) f(unloadedBody)
        else rootParse.resultTypeInfo.foreach{_.foreach{f}}
        ret
      }
    
    
      type ParseNode <: ProjectImpl.this.ParseNode with ParseNodeImpl
      trait ParseNodeA extends super[FileA].ParseNodeImpl {selfX : ParseNode => } 
      trait ParseNodeB extends super[FileB].ParseNodeImpl {selfX : ParseNode => }
      trait ParseNodeImpl extends ParseNodeA with ParseNodeB with ProjectImpl.this.ParseNodeImpl {selfX : ParseNode =>
        def self : ParseNode
        override protected def errorsDisabled = super.errorsDisabled || isMagicPhase
        override def shouldBeTyped : Boolean = isMagicPhase || super.shouldBeTyped
        override protected def highlightChanged = {
          super.highlightChanged
          if (!hasParseErrors && !hasTypeErrors && !isMagicPhase) {
            // redo the data ASAP
            resetData
            dirtyPresentation
          }
        }
        override def parseChanged = {
          super.parseChanged
          FileImpl.this.parseChanged(self)
        } 
        override def doPresentation(implicit txt : PresentationContext) : Unit = {
          refreshData
          super.doPresentation(txt)
        }        
        override protected def computeFold = {
          val doFold = parseContext.pinfo match {
          case NonLocalDefOrDcl|LocalDef|TopLevelTmplDef|CaseBlock => true
          case _ => false
          }
          if (doFold) {
            val start = absolute
            val length = this.length
            Some(start, start + length)
          } else None
        }
        override protected def identifier(in : compiler.ScannerInput, name : Name) = if (isMagicPhase) name else {
          val first = in.offset - name.length
          val first0 = separated(first, name.length)
          if (first == first0) name
          else {
            import scala.tools.nsc.ast.parser.Tokens
            assert(first0 > first)
            val kw = name.toString.substring(first0 - first) 
            val code = compiler.ScannerConfiguration.name2token(compiler.newTermName(kw))
            if (code == Tokens.IDENTIFIER) name
            else { 
              in.seek(first0)
              compiler.newTermName(name.toString.substring(0, first0 - first))
            }
          }
        }
        override protected def contentForParse = 
          if (isMagicPhase) file.content.drop(absolute)
          else super.contentForParse
        
        protected override def doConstrict(content : RandomAccessSeq[Char], offset : Int) : RandomAccessSeq[Char] = 
          if (isMagicPhase) content 
          else {
            val ret = content.patch(offset, ";", 1)
            ret
          }
      }
      protected override def noParse(range : Match, offset : Int, added : Int, removed : Int) = range.kind match {
      case kind if kind==StringMatch|kind==MultiMatch|(kind.isInstanceOf[Comment]) => 
        object noParseStuff extends HasPresentation {
          def isValid : Boolean = true
          def doPresentation(implicit txt : PresentationContext) : Unit =
            invalidate(range.from, range.until)
        }
        noParseStuff.dirtyPresentation
        true
      case _ => super.noParse(range, offset, added, removed)
      }
      type IdentifierPosition <: ProjectImpl.this.IdentifierPosition with IdentifierPositionImpl 
      trait IdentifierPositionImpl extends super[FileA].IdentifierPositionImpl with super[FileB].IdentifierPositionImpl {
        def self : IdentifierPosition
        override def isValid = {
          super.isValid
        }
      }
      type Token <: TokenImpl
      trait TokenImpl extends super.TokenImpl {
        def self : Token
        /*
        override def computeFold(owner : parses.Range) = super.computeFold(owner) match {
        case ret @ Some(_) => ret
        case None if code == scala.tools.nsc.ast.parser.Tokens.LBRACE => 
          if (owner == parses.NoRange || ((owner.get:ParseNode).parseContext.pinfo match {
          case NonLocalDefOrDcl|LocalDef|TopLevelTmplDef => true
          case _ => false
          })) {
            border(NEXT).map(x => (offset,x))
          } else None
        case _ => None
        }*/
          
        // so we keep a map of all symbols.
        def asSymbol(owner : parses.Range) : Option[Symbol] = if (owner.isEmpty) {
          //var keySet = sourceMap.keySet.toList
          val nscFile0 = nscFile
          if (!sourceMap.contains(nscFile0))
            loadSource(nscFile0)
          sourceMap(nscFile)._2.get(offset)
        } else {
          import scala.tools.nsc.ast.parser.Tokens._
          if (code != IDENTIFIER && code != BACKQUOTED_IDENT) return None
          val pos = identifiers.find(offset) match {
          case None => return None
          case Some(pos) => pos
          }
          owner.get.decode(pos)
        }
        import scala.tools.nsc.ast.parser.Tokens._
        override def style : Style = if (code == IDENTIFIER || code == BACKQUOTED_IDENT) 
          asSymbol(enclosingParse) match {
        case None => super.style
        case Some(sym) =>
          coreStyle(sym) overlay super.style
        } else super.style
        
        override def hover : Option[RandomAccessSeq[Char]] = super.hover.orElse(syncUI{asSymbol(enclosingParse) match {
        case None => None
        case Some(sym) => 
          val buf = new StringBuilder
          if (sym.tpe != null) {
            buf append ("<code><b>" + (if (sym.isValueParameter) sym.simpleName else sym.fullNameString) + "</b></code>")
            buf append ("<code>" + header(sym).getOrElse("") + "</code>")
            buf append "<br>"
          }
          try {
            val result = decode(sym).hover.map(strip)
            if (!result.isEmpty) buf ++= (result.get)
          } catch {
            case t => logError(t)
          }
          Some(buf)
        }})
        def enclosingDefinition : Option[Symbol] = {
          if (editing) {
            import compiler._
            var r = enclosingParse
            while (!r.isEmpty) r.get.getParseTrees.find(_.isInstanceOf[MemberDef]) match {
            case None => r = r.enclosing
            case Some(tree : MemberDef) => tree.pos match {
              case pos : IdentifierPositionImpl => return pos.owner.decode(pos)
              }
            }
            return None
          }
          var tok : Option[Token] = Some(self)
          while (!tok.isEmpty) tok.get.asSymbol(parses.NoRange) match {
          case Some(symbol) if symbol.pos.offset.isDefined && symbol.pos.offset.get == tok.get.offset => return Some(symbol)
          case _ => tok = tok.get.prev
          }
          return None
        }
        override def hyperlink : Option[Hyperlink] =
          super.hyperlink orElse {
            val node = {
              val symbol = asSymbol(enclosingParse)
              if (symbol.isDefined && symbol.get.owner.isPackageClass) {
                val tree = sourceMap(nscFile)._1
                object CtorFinderTraverser extends Traverser {
                  var lastSelect : Option[Symbol] = None
                  var inLastSelect = false
                  var rightmost = 0
                  override def traverse(tree: Tree): Unit = {
                    if (tree.pos.offset.isDefined && rightmost < tree.pos.offset.get)
                      rightmost = tree.pos.offset.get
                    if (rightmost < offset && !inLastSelect)
                      lastSelect = None
                    if (rightmost > offset)
                      return
                    tree match {
                      case sel : Select =>
                        if (rightmost <= offset && sel.symbol.isConstructor) {
                          sel.symbol.info.complete(sel.symbol)
                          lastSelect = Some(sel.symbol)
                          inLastSelect = true
                          super.traverse(sel)
                          inLastSelect = false
                          if ((lastSelect.getOrElse(null) eq sel) && rightmost < offset)
                            lastSelect = None
                        }
                        else
                          super.traverse(sel)
                          
                      case _ => super.traverse(tree)
                    }
                  }
                }
                CtorFinderTraverser.traverse(tree)
                CtorFinderTraverser.lastSelect orElse symbol
              } else symbol
            }
            
            val ref = node.map(decode)
            ref match {
              case None|Some(NoRef) => None 
              case Some(r) => Some(Hyperlink(r.hyperlink)("Go to"))
            }
          }
        override def completions(offset : Int) : List[Completion] = {
          if (!editing || jobIsAsync) return Nil // busy
          if (!isMatched) return Nil
          
          val (replaceAt, replaceLength, leading, range) = code match {
          case STRINGLIT|CHARLIT|COMMENT => return Nil
          case IDENTIFIER => (this.offset,text.length, text.slice(0, offset), enclosingParse)
          case BACKQUOTED_IDENT =>
            (this.offset, this.text.length,
                (if (offset == 0) "" else if (offset == text.length - 1) text.slice(1, text.length - 1)
                 else text.slice(1, offset))  : RandomAccessSeq[Char], enclosingParse)
          case _ => prev.map(t => (t,t.code)) match {
            case Some((tok,IDENTIFIER)) if offset == 0 => (tok.offset, tok.text.length, tok.text, tok.enclosingParse) 
            case Some((tok,DOT|WHITESPACE)) => (this.offset + offset, 0, "" : RandomAccessSeq[Char], tok.enclosingParse)
            case Some((tok,COMMENT)) => return Nil
            case _ => 
              var tok = this
              while (tok.next.isDefined && (tok.code match {
                case WHITESPACE|NEWLINE|NEWLINES => true
                case _ => false
              })) tok = tok.next.get
              var span = tok.enclosingParse
              while (span.from > this.offset + offset) span = span.enclosing
              (this.offset + offset, 0, "" : RandomAccessSeq[Char], span)
            }
          }
          
          val replaceAt0 = replaceAt - range.from
          assert(replaceAt0 >= 0)
          if (range.isEmpty || !range.get.hasLength) return Nil
          val content = FileImpl.this.content.slice(range.from, range.until + 1).
            patch(replaceAt0, magicName.toString, replaceLength)
          val replaced = magicName.toString.length
          @uncheckedStable val node0 = range.get
          val parser = new node0.Parser(content, node0.parseContext) {
            override protected def doDestroy = {
              //assert(false)
              false
            }
            override protected def indirect(relative0 : Int, indirect0 : Option[ParseNode], txt : ParseContext) : ParseTree = {
              indirect0.map(_.parseContext) match {
              case Some(txt0) if txt == txt0 => return super.indirect(relative0, indirect0, txt)
              case _ => 
                in.nextToken
                val results = txt.pinfo(this)
                if (results.isEmpty) compiler.EmptyTree else results.last
              }
            }
            override def unadjust(offset : Int) : Int = {
              if (offset <= replaceAt0) return super.unadjust(offset)
              else if (replaced >= replaceLength) return offset + (replaced - replaceLength)
              else return offset - (replaceLength - replaced)
            }
            override def adjust(offset : Int) : Int = {
              if (offset <= replaceAt0) return super.adjust(offset)
              if (offset <= replaceAt0 + replaced) return replaceAt0
              if (replaced >= replaceLength) return {
                val ret = offset - (replaced - replaceLength)
                assert(ret >= replaceAt0)
                ret
              }
              return {
                val ret = offset + (replaceLength - replaced)
                assert(ret >= replaceAt0)
                ret
              }
            }
          }
          val magicProcessor = new MagicProcessor(leading.toString)
          val parse = node0.parseContext.pinfo(parser)
          node0.doMagic0(magicProcessor, parse){
            node0.doNamer
            node0.doTyper
          }
          val buf = new ListBuffer[Completion]
          def convert(wantHigh : Boolean) = magicProcessor.elements.filter{
            case (_,(sym,high)) => high == wantHigh && !sym.isSetter 
          }.foreach{
          case (_,(sym,_)) => 
            val text : runtime.RichString = sym.name.decode 
            val code = compiler.ScannerConfiguration.name2token(sym.name.toTermName)
            val backquote = if (Tokens.isKeyword(code)) true
            else if (text.length == 0) true else { // we could do this offline.
              var isId = compiler.isIdentifierStart(text(0))
              var isOp = compiler.isOperatorPart(text(0))
              var idx = 1
              while ((isId||isOp) && idx < text.length) text(idx) match {
                case '_' if isId && idx + 1 < text.length && compiler.isOperatorPart(text(idx + 1)) =>
                  isId = false; isOp = true; idx = idx + 1
                case c if isId && compiler.isIdentifierPart(c) => idx = idx + 1
                case c if isOp && compiler.isOperatorPart(c) => idx = idx + 1
                case _ => isId = false; isOp = false
              }
              !(isId || isOp)
            } 
            buf += FileImpl.this.Completion(replaceAt, replaceLength, if (backquote) '`' + text + '`' else text, header(sym), imageFor(sym), 
                decode(sym).hover.map(_.mkString))
          }
          convert(true)
          convert(false)
          buf.toList
        }
      }
    }
    private class MagicProcessor(leading : String) extends LinkedHashMap[String,(Symbol,Boolean)] with TypedElement {
      private var verify : Symbol => Symbol = null
      private var pt : Type = null
      def withFile = None
      def dirtyTyped = {}
      def self = this
      override def addTo(set : => LinkedHashSet[ScopeClient]) = {}
      override def makeNoChanges : Boolean = true
      override def notify(name : Name, scope : HookedScope) : Boolean = if (name.toTermName == magicName) {
        val isType = name.isTypeName
        scope.elements.foreach{sym => 
          val name = sym.name
          val str = name.decode
          val key = if (sym.isMethod) str.trim+header(sym) else str.trim 
          val last = str.last
          
          import org.eclipse.jdt.core.search.SearchPattern

          // TODO: check accessibility. 
          if (name.isTypeName == isType && (str.indexOf('$') == -1) && last != ' ' &&
                !contains(key) && (str.toLowerCase.startsWith(leading.toLowerCase) || 
                SearchPattern.camelCaseMatch(leading, str))) {
            val sym0 = if (verify==null) sym else verify(sym)
            if (sym0 != compiler.NoSymbol) {
              val high = if (pt != null) sym0.tpe <:< pt
                         else false
              this(key) = (sym0,high)
            }
          }
        }
        true
      } else super.notify(name, scope)
      override def notify(name : Name, sym : Symbol) : Unit = if (name.toTermName == magicName) {
        val name = sym.name
        val str = name.decode
        val key = if (sym.isMethod) str.trim+header(sym) else str.trim 

        if (str.toLowerCase.startsWith(leading.toLowerCase) && !str.endsWith("$") && !contains(key)) 
          this(key) = (sym,true)
        super.notify(name, sym)
      }
      override def verifyAndPrioritize[T](verify : Symbol => Symbol)(pt : Type)(f : => T) : T = {
        this.verify = verify; this.pt = pt
        try {
          super.verifyAndPrioritize(verify)(pt)(f)
        } finally {
          this.verify = null
          this.pt = null
        }
      }
    }
    def header(sym : compiler.Symbol) = try {
      if (sym.isType || sym.isModule) None
      else if (sym.isMethod) {
        var str = sym.tpe.deconst.widen.toString.trim
        if (str.startsWith("=>")) str = str.substring(2).trim
        val idx = str.lastIndexOf(')')
        if (idx != -1) {
          str = str.substring(0, idx + 1) + " : " + str.substring(idx + 1)
        } else str = " : " + str
        Some(str)
      }
      else if (sym.isValue) {
        var str = sym.tpe.widen.toString.trim
        if (str.startsWith("=>")) str = str.substring(2)
        Some(" : " + str)
      }
      else None
    } catch {
      case ex => 
        logError("DOC_ERROR", ex)
        None
    }
    private[TypersPresentations] def documentation(sym : compiler.Symbol) : Option[RandomAccessSeq[Char]] =  {
      if (compiler.comments != null) compiler.comments.get(sym) match {
      case Some(string) => return Some(strip(string))
      case _ =>
      }      

      if (sym.pos == NoPosition && (sym.sourceFile ne null)) {
        val name = sym.sourceFile.name
        if (name.endsWith(".scala")) {
          val e = fileFor(sym)
          assert(!e.isEmpty)  
        } else if (name.endsWith(".java")) error("Not yet implemented") // javaDocumentation(sym)
      }
      if (sym.pos == NoPosition) return None
      val (offset,content) = sym.pos match {
        case (tok : IdentifierPositionImpl) => 
          //(tok.offset.get,tok.file.content)
          // should already be in comment
          val offset = syncUI(tok.absolute)
          (offset, tok.file.content.projection.take(offset))
        case OffsetPosition(sourceF,offset) if sym.sourceFile ne null =>
           (offset, sym.sourceFile.toCharArray.projection.take(offset))
        case _ => return None
      }
      var idx = offset - 1
      while (idx >= 0 && (isSpace(content(idx)) || isNewline(content(idx)))) idx = idx - 1
      if (idx < 0) return None
      def bw(text : String) = {
        if (idx + 1 < text.length) false
        if (content.drop(idx + 1 - text.length).startsWith(text)) {
          idx = idx + 1 - text.length
 	  true
        } else false
      }
      def isKeyword = content(idx) match {
      case 's' if bw("class") => true
      case 't' if bw("object") => true
      case 't' if bw("trait") => true
      case 'l' if bw("val") => true 
      case 'r' if bw("var") => true
      case 'f' if bw("def") => true
      case 'e' if bw("type") => true
      case _ => false
      }
      
      def sw(text : String) = content.drop(idx).startsWith(text)
      if (!isKeyword) return None
      def giveUp : Option[RandomAccessSeq[Char]] = try {
        val i = sym.allOverriddenSymbols.elements
        while (i.hasNext) {
          val ret = decode(i.next).hover
          if (!ret.isEmpty) return ret
        }
        (sym,sym.info) match {
        case (_ : TypeSymbol, TypeRef(_,sym,_)) =>
          val ret = decode(sym).hover
          if (!ret.isEmpty) return ret
        case _ =>
        }
        None
      } catch {
        case ex => logError(ex); None
      }
      while (idx >= 0 && (content(idx) match {
      case '*' if sw(closeComment) => 
        val close = idx
        idx = idx - 1
        while (idx >= 0 && !(content(idx) == '/' && sw("/*"))) idx = idx - 1
        if (idx < 0 || !sw("/**")) return giveUp
        return Some(strip(content.slice(idx + 3, close)))
      case _ if isKeyword => return giveUp
      case '['|']'|'/'|'@'|'-'|'('|')' => true
      case c if compiler.isIdentifierPart(c) => true
      case c if compiler.isDigit(c) => true
      case c if isSpace(c) || isNewline(c) => true
      case ';' | '}' => return giveUp
      case _ => false
      })) idx = idx - 1
      giveUp
    }
    def imageFor(sym : Symbol) : Option[Image] = imageFor(coreStyle(sym))
    def imageFor(style : Style) : Option[Image] = None
    def coreStyle(sym : Symbol) = {
      if (sym.isTrait) (traitStyle)
      else if (sym.isClass) (classStyle)
      else if (sym.isPackage) (packageStyle)
      else if (sym.isType || sym.isTypeParameter) (typeStyle)
      else if (sym.isModule) (objectStyle)
      else if (sym.isVariable) {
        if (sym.hasFlag(Flags.LAZY)) valStyle else varStyle
      }
      else if (sym.isGetter) {
        if (sym.accessed.isVariable) (varStyle)
        else (valStyle)
      }
      else if (sym.isSetter) (varStyle)
      else if (sym.isMethod) (defStyle)
      else if (sym.isValue)  {
        if (sym.isValueParameter) argStyle
        else (valStyle)
      } else noStyle
    }
  } 
}

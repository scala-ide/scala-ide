/*
 * Copyright 2005-2008 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.editor
import scala.tools.nsc.ast.parser
import parser.Tokens
import parser.Tokens._

trait Presentations extends lampion.presentation.Matchers {
  val commentStyle = Style("comment").foreground(colors.salmon).style
  val keywordStyle = Style("keyword").foreground(colors.iron).bold.style
  val litStyle = Style("literal").foreground(colors.salmon).italics.style
  val stringStyle  = Style("string").parent(litStyle).style
  val charStyle = Style("char").parent(litStyle).style
  val symbolStyle = Style("symbol").parent(litStyle).style
  override protected def isNewline(c : Char) = Tokens.isNewLine(c)
  override protected def isSpace(c : Char) = Tokens.isSpace(c)

  type Project <: ProjectImpl
  trait ProjectImpl extends super.ProjectImpl with Matchers {
    def self : Project
    type File <: FileImpl
    trait FileA extends super[ProjectImpl].FileImpl{selfX : File => }
    trait FileB extends super[Matchers].FileImpl{selfX : File => }
    trait FileImpl extends FileA with FileB {selfX : File => 
      def self : File
      type Token <: TokenImpl
      def tokIsSignificant : Token => Boolean = (tok => tok.isSignificant)
      def tokIsStatementEnd : Token => Boolean = (tok => tok.isStatementEnd)
      def tokIsDefLike : Token => Boolean = (tok => tok.isDefLike)
      trait TokenA extends super[FileA].TokenImpl
      trait TokenB extends super[FileB].TokenImpl
      trait TokenImpl extends TokenA with TokenB {
        def self : Token
        override def isWhitespace = super[TokenB].isWhitespace
        override def isNewline = super[TokenB].isNewline
        override def isSignificant = super.isSignificant && (code != COMMENT)
        
        private def doCaseSpace(from : Token, edits : Edits) = {
          assert(code == CASE)          
          val idx = firstOfLine
          if (idx.isDefined) edits * FileImpl.this.reIndent(idx.get, from.extent + 1)
          else super.doSpace(edits)
        } 
        override def doSpace(edits : Edits) : Edits = code match {
        case CASE => doCaseSpace(self, edits)
        case CLASS|OBJECT => // could be in a mis-indented case
          var prev = this.prev
          while (prev.isDefined && prev.get.isWhitespace) prev = prev.get.prev
          if (prev.isDefined && prev.get.code == CASE) prev.get.doCaseSpace(self, edits)
          else super.doSpace(edits)
        case RBRACE|ELSE => 
          val idx = firstOfLine
          if (idx.isDefined) edits * reIndent(idx.get, self.extent + 1)
          else super.doSpace(edits)
        case _ => super.doSpace(edits)         
        }
        override def doNewline(edits : Edits, added : Int) : Edits = code match {
        case LBRACE if false => 
          var tok = tokenFor(extent + added)
          while (tok.isWhitespace && tok.next.isDefined) tok = tok.next.get
          if (tok.code == RBRACE) {
            val buf = new StringBuilder
            val indent = statementIndent + indentBy
            buf.append(indent + "\n")
            //if (added - 1 - indentBy.length > 0)
            //  1.until(added - indentBy.length).foreach(x => buf append ' ')
            edits * new Edit(tok.offset, 0, buf) {
              override def moveCursorTo = tok.offset + indent.length
            }
          } else  super.doNewline(edits, added)
        case _ => super.doNewline(edits, added)
        }
        override def border(dir : Dir) = (code,dir) match {
        case (RPAREN|RBRACE|RBRACKET,PREV) => super.border(dir)
        case (LPAREN|LBRACE|LBRACKET,NEXT) => super.border(dir)
        case _ => None
        }
        protected def conditionBrace = (code match {
        case RPAREN|RBRACE|RBRACKET => (border(PREV).map(tokenFor) match {
          case Some(tok) => tok.find(PREV)(tokIsSignificant).map(t => (t,t.code)) match {
            case Some((tok,DO|WHILE|FOR|IF)) => Some(tok)
            case _ => None
          }
          case _ => None
          })  
        case _ => None
        }) 
        override protected def isConditionBrace = super.isConditionBrace || conditionBrace.isDefined
        override def style = {
          var ret = super.style
          ret = ret overlay (if (isKeyword) keywordStyle
          else code match {
          case STRINGLIT => stringStyle
          case CHARLIT => charStyle
          case SYMBOLLIT => symbolStyle
          case COMMENT => commentStyle
          case _ => noStyle
          })
          ret
        }
        /*
        override def computeFold(owner : FileImpl.this.parses.Range) = (code match {
        case STRINGLIT if text.startsWith(MultiMatch.open)       => Some(offset, extent)
        case COMMENT   if text.startsWith(MultiLineComment.open) => Some(offset, extent)
        case CASE if inCase => 
          findWithMatch(NEXT)(tok => tok.code match {
          case RBRACE => true
          case CASE if (tok.find(NEXT)(tokIsSignificant) match {
            case Some(tok) if isDefinition(tok.code) => false            
            case _ => true
          }) => true
          case _ => false
          }) match {
            case Some(tok) => Some(offset, tok.offset)
            case None => super.computeFold(owner)
          }
        case _ => super.computeFold(owner)
        })
        */
        def isFollowedBy(f : Token => Boolean) = followedBy.map(f) getOrElse false
        def followedBy = {
          find(NEXT)(!_.isWhitespace) match {
          case Some(tok) if tok.isNewline => None
          case Some(tok) if tok.code == SEMI => None
          case ret => ret
          }
        }
        def statementSeparator : Option[Token] = findWithMatch(PREV)(tok => tok.code match {
        case SEMI|LBRACE|LPAREN|LBRACKET => true
        case NEWLINE|NEWLINES => 
          (tok.find(NEXT)(tok => !tok.isWhitespace && !tok.isNewline).map(t => (t,t.code)) match {
            case Some((tok, IF|ELSE|DO|WHILE|FOR)) if TokenImpl.this.code == LBRACE => 
              assert(true)
              true 
            case Some((tok, ELSE)) if tok.find(NEXT)(_.isSignificant).map(_.code) != Some(IF) => false
            case Some((_, DOT)) => false
            case _ => true
          }) && (tok.find(PREV)(t => !t.isWhitespace && t.code != COMMENT) match {
          case None => false
          case Some(tok) if tok.isNewline => false
          case Some(tok) if tok.code == RPAREN && tok.isConditionBrace && !TokenImpl.this.isStatementEnd => true
          case Some(tok) => TokenImpl.this.code == LBRACE || 
            TokenImpl.this.code == LPAREN || 
              TokenImpl.this.code == LBRACKET || 
                TokenImpl.this.isConditionBrace || tok.isStatementEnd
          })
        case _ => false 
        })
        def statementBegin = {val ret=statementSeparator match {
        case None => 
          var tok = (tokenFor(0))
          while (!tok.isSignificant && !tok.next.isEmpty) tok = tok.next.get
          tok
        case Some(tok) => tok.find(NEXT)(_.isSignificant) getOrElse tok
        }; assert(ret != null); ret}
        def statementIndent = statementBegin.spaceBefore

        override def isStatementEnd = code match {
        case ARROW if inCase || !getSelf.isEmpty => true
        case _ => super.isStatementEnd
        }
        def getSelf : Option[Token] = code match {
        case ARROW =>          
          findWithMatch(PREV)(_.code match {
          case LBRACE|LPAREN|LBRACKET|EQUALS|SEMI|CASE => true
          case _ => false
          }).map(tok => (tok,tok.code)) match {
            case Some((tok,LBRACE|LPAREN)) => Some(tok)
            case _ => None
          }
        case _ => None
        }
        
        def inCase : Boolean = code match {
        case CASE => find(NEXT)(tokIsSignificant).map(_.code) match {
          case Some(OBJECT|CLASS) => false
          case _ => true
          }
        case ARROW | IF => findWithMatch(PREV)(tok => tok.code match {
          case CASE|EQUALS|ARROW|SEMI => true
          case NEWLINE|NEWLINES => 
            tok.find(PREV)(tokIsSignificant).map(tokIsStatementEnd) getOrElse false
          case code if isDefinition(code) => true
          case _ => false
          }) match {
          case Some(tok) if tok.code == CASE => true
          case _ => false
          }
        case _ => false
        }
        def isDefLike = code match {
        case OBJECT|CLASS|TRAIT => true
        case TYPE|VAL|VAR|DEF => true
        case PACKAGE => true
        case IMPORT => true // why not
        case _ => false
        }
        override def indentFrom(offset : Int, sentry : Token) : RandomAccessSeq[Char]= {
          code match {
          case ELSE => 
            var depth = 0
            findWithMatch(PREV)(_.code match {
            case ELSE => depth = depth + 1; false
            case IF if depth == 0 => true
            case IF => depth = depth - 1; false
            case _ => false
            }) match {
            case Some(ifn) => 
              return ifn.find(PREV)(!_.isWhitespace).map(t => (t,t.code)) match {
              case Some((elsen,ELSE)) => elsen.spaceBefore
              case _ => ifn.spaceBefore
              }
            case None => 
            }
          case CASE if !isFollowedBy(tokIsDefLike) => // real case statement
            findWithMatch(PREV)(tok => tok.code match {
                                case CASE if !isFollowedBy(tokIsDefLike) => true
            case LBRACE => true
            case _ => false
            }).map(tok => (tok,tok.code)) match {
            case Some((tok,CASE)) => return tok.spaceBefore
            case Some((tok,LBRACE)) => return tok.indentOfThisLine 
            case None => 
            }
          case RBRACE|RPAREN|RBRACKET => 
            val matching = border(PREV)
            if (!matching.isEmpty)
              return tokenFor(matching.get).indentOfThisLine
          case _ => 
          }
          sentry.code match {
          case LBRACE|LPAREN|LBRACKET => return sentry.statementBegin.indentOfThisLine ++ indentBy
          case ARROW if sentry.inCase =>
            return sentry.statementBegin.indentOfThisLine ++ indentBy
          case ARROW =>
            sentry.getSelf match {
            case Some(lbrace) => return lbrace.statementBegin.indentOfThisLine ++ indentBy
            case _ =>
            }
          case _ => 
          }
          if (offset == sentry.offset) return ""
          else return sentry.statementIndent ++ ((if (!sentry.isStatementEnd || code == DOT) indentBy else "") : Iterable[Char])
        }
      }
      override def completeOpen(offset : Int, kind : OpenMatch) : Int = {
        if (kind == Curlies) {
          val tok = tokenFor(offset)
          assert(tok.code == LBRACE)
          var last : Token = null.asInstanceOf[Token]
          val next = tok.next
          val newLine = if (!next.isDefined || next.get.isNewline) None else
            next.get.findWithMatch(NEXT){tok =>
            if (tok.isSignificant) last  = tok
            if (tok.isNewline) {
              last == null || last.isStatementEnd
            } else false
          }
          if (last != null && newLine.isDefined) return newLine.get.offset  
        }
        super.completeOpen(offset, kind)
      }
      
      class IsNewLine extends (Char => Boolean) {
        def apply(c : Char) = isNewLine(c)
      }
      override protected def doSurround(c : Char, offset : Int, length : Int) : Option[(String,String)] = 
        super.doSurround(c, offset, length) orElse (c match {
      case '/' => Some("/*","*/")
      case '(' => Some("(", ")")
      case '[' => Some("[", "]")     
      case '{' => Some("{", "}")     
        // XXX: get around anonfunc in gaurd not found bug
      case '\"' if content.slice(offset, offset + length).exists(new IsNewLine) => 
        Some(MultiMatch.open, MultiMatch.close)  
      case '\"'  => Some("\"", "\"")     
      case '`'  => Some("`", "`")     
      case _ => None
      })
      override def beforeEdit(edit : Edit) : Edit = {
        if (edit.length == 0 && edit.text.length == 1 && isNewline(edit.text(0))) {
          // TODO: ensure in a code region.
          var lbraceOffset = edit.offset - 1
          if (edit.offset > 0 && edit.offset < content.length &&  {
                var pass = false            
                while (!pass && lbraceOffset >= 0 && !isNewLine(content(lbraceOffset))) {
                  if (content(lbraceOffset) == '{') pass = true
                  else lbraceOffset -= 1
                }
                pass
              } && content(edit.offset) == '}') {
            val lbrace = tokenFor(lbraceOffset)
            val buf = new StringBuilder
            val lbraceIndent = lbrace.statementIndent
            val indent = "\n" + lbraceIndent + indentBy
            buf.append(indent + "\n" + lbraceIndent)
            return new Edit(edit.offset, 0, buf) {
              override def moveCursorTo = edit.offset + indent.length
            }
          }
          // convert single-line string to multi-line string if necesary
          enclosing(edit.offset) match {
          case Some(Match(kind,begin,end)) if kind == StringMatch =>
            var what = file.content.slice(begin,end)
            what = what.patch(edit.offset - begin, edit.text, 0)
            what = what.patch(0, "\"\"", 0)
            what = what.patch(what.length, "\"\"", 0)
            return new Edit(begin, end - begin, what) {
              override def moveCursorTo = edit.offset + 3
            }
          case _ => 
          }
        } else if (edit.length == 0 && edit.text.length == 1 && isSpace(edit.text(0))) enclosing(edit.offset) match {
        case Some(Match(kind,begin,end)) if kind == MultiLineComment && edit.offset == end - 1 => 
          return new Edit(edit.offset, 0, edit.text ++ Seq.singleton('*')) {
            override def moveCursorTo = edit.offset + 1
          }
        case _ =>          
        // make sure >) aren't separated
        } else if (edit.length == 0 && edit.text.length == 1 && edit.text(0) != '>') {
          val m = border(edit.offset + 1, PREV)
          if (m.isDefined && m.get.kind == XMLParenMatch) 
            return if (edit.text(0) == '{') 
	      new Edit(edit.offset, 0, "{}>") {
	        override def moveCursorTo = edit.offset + 1
	      } else new Edit(edit.offset, 0, edit.text ++ Seq.singleton('>')) {
                override def moveCursorTo = edit.offset + 1
	      }
        }
        return super.beforeEdit(edit)
      }
      override def overwriteOk(offset : Int) = super.overwriteOk(offset) && 
        (offset == 0 || content(offset - 1) != '\\')
        
        
      override protected def indentFor(m : Match, sentry : Int, leading : Int) : Option[RandomAccessSeq[Char]] = {
        super.indentFor(m,sentry,leading) orElse (m.kind match { 
        case kind if  kind.isInstanceOf[MultiLineComment] => 
          // find the last star of the sentry line
          var lastStar = -1
          var offset0 = sentry
          while (offset0 > 0 && (content(offset0) match {
            case '*' => lastStar = offset0; true
            case c if isNewline(c) && lastStar != -1 => false
            case _ => true
          })) offset0 = offset0 - 1
          assert(lastStar != -1) // must be at least one star!
          val buf = spaceTo(lastStar)
          content(leading) match {
          case '*' => 
          case c if isNewline(c) => buf ++= "* "
          case _ =>  buf ++= "* "
          }
          Some(buf)
        case kind if kind == MultiMatch => Some("") // no indent!
        case kind =>
          assert(kind != MultiMatch)
          None
        })
      }
      override def canConstrict(offset : Int) = super.canConstrict(offset) && {
	assert(true)
        val what = enclosing(offset).map(_.kind) 
        if (what.isDefined) {
          (what.get != LineComment && what.get != MultiMatch && what.get != Parens)
        } else true
      }
    }
  }
  protected val keywords = 
    "var" :: "val" :: "def" :: "type" :: "package" :: 
    "implicit" :: 
    "import" :: 
    "class" :: "trait" :: "object" :: "case" ::
    "return" ::
    "protected" :: "private" :: "override" :: "abstract" :: "final" :: Nil
}

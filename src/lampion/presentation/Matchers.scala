/*
 * Copyright 2005-2009 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package lampion.presentation

trait Matchers extends AutoEdits {
  type Project <: ProjectImpl
  trait ProjectImpl extends super.ProjectImpl {
    type File <: FileImpl
    trait FileImpl extends super[ProjectImpl].FileImpl {selfX : File => 
      def self : File
      override def doParsing = {
        if (isMatched)
          super.doParsing
      }
      protected def overwriteOk(offset : Int) = !isNewline(content(offset)) && isCompleted(offset + 1)
      override def beforeEdit(edit : Edit) : Edit = {
        if (isMatched) {
          if (edit.text.length == 1 && edit.length == 0 && edit.offset < content.length && 
              edit.text.charAt(0) == content(edit.offset) && overwriteOk(edit.offset)) {
            val enclosing = FileImpl.this.enclosing(edit.offset)
            if (enclosing.isDefined && 
              edit.offset >= enclosing.get.until - enclosing.get.close.length) {
              unmakeCompleted(edit.offset + 1)
              return NoEdit
            }
            
          } else if (edit.text.length == 0 && edit.length == 1) {
            val enclosing = FileImpl.this.enclosing(edit.offset + 1)
            if (enclosing.isDefined &&
                edit.offset + 1 == enclosing.get.from + enclosing.get.kind.open.length &&
                edit.offset + 1 == enclosing.get.until - enclosing.get.close.length &&
                isCompleted(enclosing.get.until)) { 
              return new Edit(edit.offset, 1 + enclosing.get.close.length, "") {
                override def moveCursorTo = edit.offset
              }
            }
          }
        } else if (edit.text.length == 1 && edit.length == 0 && edit.offset > 0) {
          rebalance(edit.offset) match {
            case Some(CompleteBrace(kind, close)) => 
              val at = completeOpen(edit.offset - kind.open.length, kind) + 1
              if (close == "\"") {} 
              else if (close == "}" && ({ // XXX: also doesn't belong here.
                var i = edit.offset
                while (i < content.length && isSpace(content(i))) i += 1
                i != content.length && !isNewline(content(i))
              })) {} else if (isNewline(edit.text(0)) && close == "}") { // XXX: doesn't belong here.
                val id0 = indentOfThisLine(edit.offset)
                val id1 = id0.mkString + "  ";
                return new Edit(edit.offset, 0, "\n" + id1.mkString + "\n" + id0.mkString + close) {
                  override def afterEdit = makeCompleted(offset + 2 + id1.length + id0.length + close.length)
                  override def moveCursorTo = edit.offset + id1.length + 1
                }
                
              } else return new Edit(edit.offset, 0, edit.text + close) {
                override def afterEdit = makeCompleted(offset + 1 + close.length)
              }
            case _ =>
          }
        }
        super.beforeEdit(edit)
      }
      protected def indentFor(m : Match, sentry : Int, leading : Int) : Option[RandomAccessSeq[Char]] = None
      protected override def autoIndent(sentry : Int, leading : Int) : RandomAccessSeq[Char] = {
        enclosing(sentry,leading).map(m => indentFor(m,sentry,leading)) getOrElse None getOrElse
          super.autoIndent(sentry, leading)
      }  
      protected def completeOpen(offset : Int, kind : OpenMatch) = offset + kind.open.length
      override def afterEdit(offset : Int, added : Int, removed : Int) : Edits = {
        var edits = super.afterEdit(offset, added, removed)
        if (edits ne NoEdit) return edits
        if (!(added == 1 && removed == 0)) return edits
        rebalance(offset + added) match {
        case Some(DeleteClose(from,length)) =>
          if (isCompleted(from)) 
            return edits * new Edit(from - length, length, "")
        case _ => 
        }
        edits
      }
      type Token <: TokenImpl
      trait TokenImpl extends super.TokenImpl {
        def self : Token
        override def hover : Option[RandomAccessSeq[Char]] = super.hover orElse {
          if (syncUI{isUnmatchedOpen(offset)}) Some("unmatched open brace")
          else if (syncUI{isUnmatchedClose(extent)}) Some("unmatched close brace")
          else None 
        }

        // matches are made at the edge
        def borderNext = FileImpl.this.borderNext(offset).map(_.until)
        def borderPrev = FileImpl.this.borderPrev(extent).map(_.from)
        
        def findWithMatchNext(f : Token => Boolean) : Option[Token] = {
          var tok : Option[Token] = Some(self)
          while (true) {
            if (tok.isEmpty) return None
            else if (tok.get != self && 
                     f(tok.get)) return tok
            val nextBorder = tok.get.borderNext 
            def prevBorder = tok.get.borderPrev
            if (!nextBorder.isEmpty) {
              tok = tokenFor(nextBorder.get).next
            } else if (tok.get != self && !prevBorder.isEmpty) return None 
            else tok = tok.get.next
          }
          None
        }
        def findWithMatchPrev(f : Token => Boolean) : Option[Token] = {
          var tok : Option[Token] = Some(self)
          while (true) {
            if (tok.isEmpty) return None
            else if (tok.get != self && 
                     f(tok.get)) return tok
            val nextBorder = tok.get.borderPrev 
            def prevBorder = tok.get.borderNext
            if (!nextBorder.isEmpty) {
              tok = tokenFor(nextBorder.get).prev
            } else if (tok.get != self && !prevBorder.isEmpty) return None 
            else tok = tok.get.prev
          }
          None
        }
      }
    }
  }
}

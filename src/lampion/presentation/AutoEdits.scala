/*
 * Copyright 2005-2009 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package lampion.presentation
import scala.collection.jcl._

trait AutoEdits extends Presentations {
  def indentBy : RandomAccessSeq[Char] = "  "
  protected def combine(t0 : RandomAccessSeq[Char], t1 : RandomAccessSeq[Char]) = (t0,t1) match {
    case (buf : runtime.RichStringBuilder,t1) => 
      buf ++= t1
      buf.self
    case (t1,buf : runtime.RichStringBuilder) => 
      buf.insertAll(0, t1)
      buf.self
    case (t0,t1) =>
      val result = new StringBuilder
      result ++= t0
      result ++= t1
      result
  }
  protected def isNewline(c : Char) = c match {
  case '\n' => true
  case '\r' => true
  case _ => false
  }
  protected def isSpace(c : Char) = c match {
  case ' ' => true
  case _ => false
  }
  
  trait Edits extends Iterable[Edit] {
    def *(edit : Edit) : Edits
  }
  class CompositeEdits(edit0 : Edit, edit1 : Edit) extends TreeSet[Edit] with Edits {
    this += edit0; this += edit1
    assert(edit0 ne NoEdit)
    assert(edit1 ne NoEdit)
    override def *(edit : Edit) : Edits = {
      assert(edit ne NoEdit)
      this += edit; this
    }
  }
  class Edit(val offset : Int, val length : Int, val text : RandomAccessSeq[Char]) extends Edits with Ordered[Edit]  {
    def moveCursorTo = -1
    def afterEdit : Unit = {}
    def compare(edit : Edit) = 
      if (offset != edit.offset) edit.offset - offset
      else if (this == edit) 0
      else if (isDelete && edit.isInsert) -1
      else if (isInsert && edit.isDelete) +1
      else abort
  
    private def isDelete = length >  0 && text.length == 0
    private def isInsert = length == 0 && text.length >  0
    def elements = (this :: Nil).elements 
    override def *(edit : Edit) : Edits = new CompositeEdits(this, edit)
  }
  object NoEdit extends Edit(0, 0, "") {
    override def elements = (Nil).elements
    override def *(edit : Edit) : Edits = edit
  }
    
  type Project <: ProjectImpl
  trait ProjectImpl extends super.ProjectImpl {
    def self : Project
    type File <: FileImpl
    trait FileImpl extends super.FileImpl {selfX : File =>
      def self : File

      def reIndent(idx : Int, from : Int) = {
        val edit = autoIndent(idx)
        new Edit(edit.offset, edit.length, edit.text) {
          override def moveCursorTo = from + (edit.text.length - edit.length)
        }
      }

      private var constrict : Option[Int] = None
      private var separate0 : Option[Int] = None
      private var modified : List[ParseNode] = Nil
      def resetEdit = {
        constrict = None
        separate0 = None
        modified.foreach(_.dirtyParse)
        modified = Nil
      }
      def resetConstrict = {
        separate0 = None
        constrict = None // without flushing.
      }
      override def tokenFor(offset : Int) : Token

      def indentOfThisLine(offset : Int) : RandomAccessSeq[Char] = {
        var length = 0
        var offset0 = offset
        if (offset0 >= content.length) return ""
        while (offset0 > 0 && isNewline(content(offset0))) offset0 = offset0 - 1
        while (offset0 > 0) content(offset0) match {
          case c if isNewline(c) => return content.slice(offset0 + 1, offset0 + 1 + length)
          case c if isSpace(c) => length = length + 1; offset0 = offset0 - 1
          case _ => length = 0; offset0 = offset0 - 1
        }
        return ""
      }
      def spaceTo(offset : Int) : StringBuilder = {
        val result = new StringBuilder
        var offset0 = offset
        while (offset0 > 0 && !isNewline(content(offset0 - 1))) {
          result += ' '
          offset0 = offset0 - 1
        }
        result
      }
      def autoIndent(offset : Int) : Edit = {
        assert(isNewline(content(offset)))
        var leading = offset + 1
        while (leading < content.length &&
          isSpace(content(leading))) leading = leading + 1
        var sentry = offset 
        while (sentry > 0 && (content(sentry) match {
          case c if isSpace(c) || isNewline(c) => true
          case _ => false
        })) sentry = sentry - 1
        assert(sentry >= 0)
        assert(leading <= content.length)
        assert(sentry <= offset)
        assert(offset < leading)
        new Edit(offset + 1, leading - (offset + 1), autoIndent(sentry, leading))
      }
      protected def autoIndent(sentry : Int, leading : Int) : RandomAccessSeq[Char] = {
        var tok0 = tokenForFuzzy(sentry)
        while (!tok0.prev.isEmpty && !tok0.isSignificant) 
          tok0 = tok0.prev.get
        var tok1 = tokenForFuzzy(leading)

        tok1.indentFrom(leading - tok1.offset, tok0)
      }
      
      def canConstrict(offset : Int) : Boolean = true
      
      def afterEdit(offset : Int, added : Int, removed : Int) : Edits = {
        resetEdit;
        {
          var offset0 = offset + added
          val content = FileImpl.this.content
          val length = content.length
          while (offset0 < length && (isSpace(content(offset0)))) offset0 += 1
          if (offset0 < length && isNewline(content(offset0)) && canConstrict(offset0)) {
            constrict = Some(offset0)
          } else separate0 = Some(offset + added)
        }
        if (removed > 0 || added != 1) return NoEdit
        var edits : Edits = NoEdit
        if (removed == 0 && added == 1 && offset > 0 && offset < content.length && isSpace(content(offset))) {
          if (isSpace(content(offset))) {                  
            val tok = tokenForFuzzy(offset - 1)
            edits = tok.doSpace(edits)
          }
        } else if (offset > 0 && removed == 0 && added >= 1 && isNewline(content(offset)) &&
                   1.until(added).forall(i => isSpace(content(offset + i)))) {
          val tok = tokenForFuzzy(offset - 1)
          edits = tok.doNewline(edits, added)
        }
        edits
      }
      def beforeEdit(edit : Edit) : Edit = {
        if (edit.length > 0 && edit.text.length == 1) {
          doSurround(edit.text(0), edit.offset, edit.length) match {
          case Some((begin,end)) => 
            val bld = new StringBuilder
            bld append begin
            content.slice(edit.offset, edit.offset + edit.length).foreach(c => bld append c)
            bld append end
            return new Edit(edit.offset, edit.length, bld.toString)
          case None =>
          }
        } else if (edit.length == 0 && (edit.text.length == 1 || edit.text.mkString == "\r\n") && edit.offset < content.length) {
          val tok = tokenForFuzzy(edit.offset)
          assert(edit.offset >= tok.offset)
          if (edit.text(0) == '\t') return doTab(edit)
          // do auto indent
          if (isNewline(edit.text(0))) {
            var leading = edit.offset
            while (leading < content.length && isSpace(content(leading))) 
              leading = leading + 1
            var sentry = edit.offset
            if (sentry > 0) sentry = sentry - 1
            while (sentry > 0 && (isSpace(content(sentry)) || isNewline(content(sentry))))
              sentry = sentry - 1
            val indent = autoIndent(sentry, leading)
            val buf = new StringBuilder
            buf ++= edit.text
            buf ++= indent
            assert(isNewline(buf(0)))
            var idx = edit.text.length
            while (idx < buf.length && !isNewline(buf(idx))) idx += 1
            return new Edit(edit.offset, leading - edit.offset, buf) {
              override def moveCursorTo = edit.offset + idx
            }
          }
        }
        return edit
      }
      protected def doSurround(c : Char, offset : Int, length : Int) : Option[(String,String)] = None

      protected def doTab(edit : Edit) : Edit = {
        // do re-indent first
        var offset0 = if (edit.offset == 0) 0 else edit.offset - 1
        while (offset0 > 0 && (isSpace(content(offset0)))) 
          offset0 = offset0 - 1
        if (isNewline(content(offset0))) {
          val edit = autoIndent(offset0)
          new Edit(edit.offset, edit.length, edit.text) {
            override def moveCursorTo = edit.offset + edit.text.length
          }
        } else edit
      }
      type ParseNode <: ProjectImpl.this.ParseNode with ParseNodeImpl
      trait ParseNodeImpl extends super.ParseNodeImpl { self0 : ParseNode => 
        def self : ParseNode
        override protected def contentForParse = {
          val from = absolute 
          var ret = FileImpl.this.content.drop(from)
          val constrict = FileImpl.this.constrict
          if (!constrict.isEmpty && from < constrict.get && (!hasLength || constrict.get <= from + length + 1)) {
            ret = doConstrict(ret, constrict.get - from)  
            if (hasLength) modified = self :: modified
          }
          ret
        }
        protected def separated(relative : Int, length : Int) = separate0 match {
        case Some(separate0) =>
          val separate = separate0 - this.absolute
          if (separate > relative && separate < relative + length) {
            modified = self :: modified
            separate
          }
          else relative
        case _ => relative
        }
        protected def doConstrict(content : RandomAccessSeq[Char], offset : Int) : RandomAccessSeq[Char] = 
          content // nothing.
      }
      type Token <: TokenImpl
      trait TokenImpl extends super.TokenImpl {
        def self : Token
        def doSpace(edits : Edits) = edits
        def doNewline(edits : Edits, added : Int) = edits
        def indentOfThisLine = FileImpl.this.indentOfThisLine(offset)
        def spaceBefore = spaceTo(offset)
        def leadingSpace = spaceTo(offset + text.length)
        def isWhitespace : Boolean = AutoEdits.this.isSpace(text(0))
        def isNewline : Boolean = AutoEdits.this.isNewline(text(0))
        def isSignificant = !isWhitespace && !isNewline
        def indentFrom(offset : Int, sentry : Token) : RandomAccessSeq[Char] = sentry.indentOfThisLine
        def firstOfLine : Option[Int] = {
          var idx = offset - 1
          while (idx >= 0 && isSpace(content(idx))) idx = idx - 1
          if (idx >= 0 && AutoEdits.this.isNewline(content(idx))) return Some(idx)
          else return None
        }
      }
    }
  }
}

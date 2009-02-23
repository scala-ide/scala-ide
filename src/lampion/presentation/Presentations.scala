/*
 * Copyright 2005-2009 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package lampion.presentation
import scala.collection.jcl._

trait Presentations extends lampion.core.Plugin {
  type Color
  val  noColor : Color
  type Hyperlink
  type PresentationContext
  type HighlightContext
  
  type Annotation // extra stuff
  type AnnotationKind
  
  type ErrorAnnotation
  type Image
  
  type Style <: StyleImpl
  trait StyleImpl {
    def self : Style
    def foreground : Color
    def background : Color
    def bold    : Boolean 
    def italics : Boolean 
    def strikeout : Boolean 
    def underline : Boolean 
    def overlay(style : Style) : Style = {
      if (style == noStyle) self else if (this == noStyle) style
      else overlay0(style)
    }
    protected def overlay0(style : Style) : Style
  }
  val noStyle : Style
  def Style(key : String) : StyleFactory
  abstract class StyleFactory {
    
    
    var foreground : Color = noColor
    def foreground(color : Color) : this.type = {
      foreground = color; this
    }
    var background : Color = noColor
    def background(color : Color) : this.type = {
      background = color; this
    }
    var bold0 : Boolean = false
    def bold : this.type = {
      bold0 = true; this
    }
    var italics0 : Boolean = false
    def italics : this.type = {
      italics0 = true; this
    }
    var underline0 : Boolean = false
    def underline : this.type = {
      underline0 = true; this
    }
    var strikeout0 : Boolean = false
    def strikeout : this.type = {
      strikeout0 = true; this
    }
    var parent : Option[Style] = None
    def parent(style : Style) : this.type = {
      this.parent = Some(style); this
    }
    def style : Style
  }
  def rgb(r : Int, g : Int, b : Int) : Color
  object colors {
    val white = rgb(255,255,255)
    val black = rgb(0, 0, 0)
    val ocean = rgb(0,62,133)
    val salmon = rgb(255,94,94)
    val cayenne = rgb(148,0,0)
    val maroon = rgb(149, 0, 64)
    val eggplant = rgb(77, 0, 134)
    val blueberry = rgb(35, 0, 251)
    val iron = rgb(76, 76, 76)
    val mocha = rgb(142, 62, 0)
  }
  type Fold
  type Project <: ProjectImpl
  trait ProjectImpl extends super.ProjectImpl with lampion.compiler.Tokenizers {
    def self : Project
    def Hyperlink(file : File, offset : Int, length : Int)(action : => Unit)(info : String) : Hyperlink
    def openAndSelect(file : File, offset : Int) : Unit = {
      openAndSelect(file, {
        val tok = file.tokenFor(offset)//.presentationBody
        (tok.offset, tok.text.length)
      })
    }
    def openAndSelect(file : File, select : => (Int,Int)) : Unit
    type File <: FileImpl
    trait FileImpl extends super[ProjectImpl].FileImpl with super[Tokenizers].FileImpl {selfX : File =>
      def self : File
      def highlight(offset : Int, length : Int, style : Style)(implicit txt : HighlightContext) : Unit
      def invalidate(start : Int, end : Int)(implicit txt : PresentationContext) : Unit
      def invalidate0(start : Int, end : Int) : Unit
      def Annotation(kind : AnnotationKind, text : String, offset : => Option[Int], length : Int) : Annotation
      def delete(a : Annotation) : Unit

      type Completion
      def Completion(offset : Int, length : Int, to : String, info : Option[String], image : Option[Image], additional : => Option[String]) : Completion
      def doComplete(offset : Int) : List[Completion] = {
        val tok = tokenForFuzzy(offset)
        assert(offset >= tok.offset)
        try {
          tok.completions(offset - tok.offset)
        } catch {
          case ex => logError(ex); Nil
        }
      } 
      def refreshHighlightFor(offset : Int, length : Int)(implicit txt : HighlightContext) = if (offset < content.length) {
        var tok : Option[Token] = Some(tokenForFuzzy(offset))
        while (!tok.isEmpty && tok.get.offset < offset + length) {
          highlight(tok.get.offset, tok.get.text.length, tok.get.style)
          tok = tok.get.next
        }
      }
      def readOnly = true
      def processEdit : Unit = try {
        if (editing) {
          doParsing
          presentFiles = self :: presentFiles
          doAfterParsing // asynchronous
        } else {
        }
      } catch {
      case e => Presentations.this.logError(e)
      }
      override def doUnload = {
        super.doUnload
        presentation.dirty.clear
      }
      trait HasPresentation {
        def isValid : Boolean
        def doPresentation(implicit txt : PresentationContext) : Unit
        def dirtyPresentation = presentation.synchronized{presentation.dirty += this}
      }
      
      private object presentation {
        val dirty = new LinkedHashSet[HasPresentation]
        val rehighlight = new LinkedHashSet[ParseNode]
      }
      def createPresentationContext : PresentationContext
      def finishPresentationContext(implicit txt : PresentationContext)
      def doPresentation : Unit = try{ job.synchronized{if (!presentation.synchronized{presentation.dirty.isEmpty && presentation.rehighlight.isEmpty}) {
        if (job.state >= Presentations.ASYNC) return // not now.
        // lock out type checking during presentation. 
        implicit val txt = createPresentationContext
        def next = presentation.synchronized{
          val i = presentation.dirty.elements
          if (!i.hasNext) None
          else {
            val next = i.next
            i.remove
            Some(next)
          }
        }
        while (next match {
          case None => false
          case Some(next) =>
            if (next.isValid) {
              next.doPresentation
              //presentation.rehighlight += next
            }
            true
        }) {}
        presentation.synchronized{presentation.rehighlight.foreach{node=>
            if (node.isValid) invalidate(node.absolute, node.absolute + node.length)
          }
          presentation.rehighlight.clear
        }
        finishPresentationContext
      }}} finally {
	;
      }
      type Token <: TokenImpl 
      trait TokenImpl extends super.TokenImpl {
        def self : Token
        def style : Style = noStyle
        def hover : Option[RandomAccessSeq[Char]] = if (!editing) None else syncUI{
          val enclosingParse = this.enclosingParse
          if (enclosingParse.isEmpty) return None
          val errors = enclosingParse.get.installedErrors(offset)
          if (errors.isEmpty) None
          else {
            val ret = errors.map(e => <p>{e.msg}</p>)
            Some(ret.mkString)
          }
        }
        def hyperlink : Option[Hyperlink] = None
        //def border(dir : Dir) : Option[Int] = None
        //override def isValid = true
        
        //def matching : Option[Token] = None
        protected final def Completion(                      text0 : String, info : Option[String], image : Option[Image], additional : => Option[String]) = 
          FileImpl.this.    Completion(offset, text.length, text0 : String, info : Option[String], image : Option[Image], additional)
        def completions(offset : Int) : List[Completion] = Nil
        
        def Hyperlink(action : => Unit)(info : String) : Hyperlink = {
          ProjectImpl.this.Hyperlink(FileImpl.this.self, offset, text.length)(action)(info)
        }
      }
      override def repair(offset : Int, added : Int, removed : Int) : Unit = {
        super.repair(offset, added, removed)
      }
      override type ParseNode <: ProjectImpl.this.ParseNode with ParseNodeImpl
      trait ParseNodeImpl extends super.ParseNodeImpl with HasPresentation {selfX : ParseNode =>
        def self : ParseNode
        protected def computeFold : Option[(Int,Int)] = None
        private var fold : Option[Fold] = None
        private[Presentations] var annotations : List[Annotation] = Nil
        private[Presentations] var errors : List[(ErrorKind,ErrorAnnotation)] = Nil
        protected def errorsDisabled = false
        override protected def error(pos : ErrorPosition, error : ErrorKind) : Unit = if (!errorsDisabled) {
          val e = newError(error.msg)
          self.synchronized{errors = (error,e) :: errors}
          val pos0 : ErrorPosition = if (pos.owner == self) pos else self
          // if the error is still installed. 
          asyncUI{self.synchronized{if (isValid && pos0.isValid && self.errors.exists(_._2 == e)) {    
            val tok = tokenFor(pos0.absolute)//.presentationBody
            FileImpl.this.install(tok.offset, tok.text.length, e)
          }}}
        }
        def installedErrors(offset : Int) = errors.filter(x => isAt(x._2, offset)).map(_._1)
        override def flushErrors(p : ErrorKind => Boolean) : Unit = {
          val deleteErrors = self.synchronized{
            val ret = errors.filter(x => p(x._1)).map(_._2)
            errors = errors.filter(x => !p(x._1))
            ret
          }
          if (!deleteErrors.isEmpty) asyncUI{
            deleteErrors.foreach(uninstall)
          }
        }
        override def hasErrors(p : ErrorKind => Boolean) : Boolean = self.synchronized{errors.exists{
        case (k,e) if p(k) => true
        case _ => false
        }}
        protected override def destroy0 = {
          super.destroy0
          errors.foreach{x => 
            if (x._2 != null) 
              uninstall(x._2)
          }
          errors = Nil
          if (fold.isDefined) {
            destroyCollapseRegion(fold.get)(null.asInstanceOf[PresentationContext])
            fold = None
          }
        }
        protected override def parseChanged = {
          // should be repainted.
          super.parseChanged
          dirtyPresentation
        }
        private def currentFold = fold match {
          case None => None
          case Some(fold) => Some(collapseRegion(fold))
        }
        protected def highlightChanged : Unit = presentation.synchronized{
          presentation.rehighlight += self
        }
        def doPresentation(implicit txt : PresentationContext) : Unit = {
          ((computeFold,currentFold) match {
          case (x0,y0) if x0 == y0 =>
          case (None,Some(_)) => destroyCollapseRegion(fold.get)
          case (Some((from,to)),_) => 
            fold = Some(createCollapseRegion(from,to,fold))
            //invalidate(from,to)
          case (None, None) =>
          })
        }
      }

      def isCollapsed(fold : Fold) : Boolean
      def createCollapseRegion(from : Int, to : Int, old : Option[Fold])(implicit txt : PresentationContext) : Fold
      def destroyCollapseRegion(fold : Fold)(implicit txt : PresentationContext) : Unit
      def collapseRegion(fold : Fold) : (Int,Int)
      //def createCollapseRegions(i : Iterator[(Int,Int)], old : List[Fold])(implicit txt : PresentationContext) : List[Fold]
      def newError(msg : String) : ErrorAnnotation
      def uninstall(a : ErrorAnnotation) : Unit
      def isAt(a : ErrorAnnotation, idx : Int) : Boolean
      def install(offset : Int, length : Int, a : ErrorAnnotation) : Unit
      //def doMatch(offset : Int) : Option[(Int,Int)] = None
    }
    import Presentations._
    override def destroy = {
      super.destroy
      job.synchronized{job.state = STOP; job.notifyAll}
    }
    def lockTyper[T](f : => T) : T = {
      if (Thread.currentThread == job) return f
      job.synchronized{while (job.state >= BUSY) job.wait; f}
    }
    def tryLockTyper[T](f : => T) : Option[T] = {
      if (Thread.currentThread == job) return Some(f)
      job.synchronized{if (job.state >= BUSY) None; else Some(f)}
    }
    private val job = new Job
    private class Job extends Thread("worker-" + self) {
      var state : Int = 0
      //val yield0 = new Object
      //var yieldRequested = 0
      
      override def run : Unit = while (isOpen) {
        try { 
          var presentFiles0 = List[File]()
          def check(b : Boolean, to : Int) = if (!b) {
            logError("wrong state: " + state, null)
            state = to
          }
          val doit = job.synchronized{
            if (state == STOP) return // stop
            if (state == READY) job.wait
            state >= BUSY
          }
          if (doit) do {
            afterParsing
          } while (job.synchronized{
            assert(state >= BUSY)
            if (state == BACKLOG) {
              state = ASYNC; true // keep going
            } else {
              check(state == ASYNC || state == BUSY, ASYNC)
              if (state == ASYNC) {
                presentFiles0 = ProjectImpl.this.presentFiles
                ProjectImpl.this.presentFiles = Nil
                // notify later.
              } else {
                job.notifyAll
              }
              state = READY
              false
            }
          })
          if (!presentFiles0.isEmpty) {
            val previous = syncUI{ // wait until we are in the UI thread to lock out the UI thread?
              val previous = job.synchronized{
                val ret = state
                state = LOCKED
                ret
              }
              destroyOrphans
              presentFiles0.foreach(_.doPresentation)
              previous
            }
            job.synchronized{
              if (state != LOCKED) {
                logError("ERROR in background thread",null)
              }
              state = previous
              job.notifyAll
            }
          }
        } catch {
          case ex =>
            logError(ex) // go on.
            job.synchronized{state = READY; job.notifyAll}
        } 
      }
    }
    // one per...
    job.start
    def jobIsAsync = job.synchronized{job.state >= ASYNC}
    def jobIsBusy  = job.synchronized{job.state == BUSY}
    
    
    def flushPresentation = // XXX: won't work yet. 
      if (job.synchronized{job.state >= ASYNC}) // do between every iteration in after parsing
        syncUI{presentFiles.foreach(_.doPresentation)}

    def syncUI[T](f : => T) : T = f
    def asyncUI(f : => Unit) : Unit = f
    def inUIThread : Boolean
    private var presentFiles : List[File] = Nil
    override protected def doAfterParsing : Unit = try {job.synchronized{
      assert(inUIThread)
      if (job.state == LOCKED) {
        Console.println("ERROR in background thread")
        job.state = READY
      }
      assert(job.state != LOCKED) // how could it be?
      while (job.state == LOCKED) job.wait
      if (job.state == STOP) return
      assert(job.state >= READY)
      assert(job.state != BUSY) // otherwise we wouldn't be here
      if (job.state >= ASYNC) {
        job.state = BACKLOG; return
      }
      // if busy, we are in trouble!
      assert(job.state == READY) 
      job.state = BUSY
      job.setPriority(Thread.MAX_PRIORITY)
      job.notifyAll
      job.wait(30L)
      if (job.state == READY) { // they are done
        val presentFiles = ProjectImpl.this.presentFiles
        ProjectImpl.this.presentFiles = Nil
        //Console.println("NO_ASYNC " + timer.elapsedString)
        destroyOrphans
        presentFiles.foreach(_.doPresentation)
      } else {
        if (job.state != BUSY) {
          Console.println("ERROR in background thread")
        }
        job.state = ASYNC
        //Console.println("GOING ASYNC " + timer.elapsedString)
      }
      assert(job.state != BUSY)
    }} finally {
      job.synchronized{}
    }
    // XXX: not used.
    protected def timeout = 20L
  }
}

object Presentations {
  val LOCKED = -2
  val STOP = -1
  val READY = 0
  val BUSY = 1
  val ASYNC = 2
  val BACKLOG = 3
}

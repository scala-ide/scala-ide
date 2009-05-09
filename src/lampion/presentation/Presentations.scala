/*
 * Copyright 2005-2009 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package lampion.presentation

import scala.collection.mutable.LinkedHashSet

import org.eclipse.jface.text.{ Position, TextPresentation }
import org.eclipse.jface.text.hyperlink.IHyperlink
import org.eclipse.jface.text.source.Annotation
import org.eclipse.jface.text.source.projection.ProjectionAnnotation
import org.eclipse.swt.graphics.{ Color, Image }
import scala.tools.eclipse.ScalaPlugin
import scala.tools.eclipse.util.Colors
import scala.tools.eclipse.util.Style

trait Presentations {
  val plugin = ScalaPlugin.plugin
  
  type PresentationContext
  
  type Project <: ProjectImpl
  trait ProjectImpl extends lampion.compiler.Tokenizers {
    def self : Project
    def Hyperlink(file : File, offset : Int, length : Int)(action : => Unit)(info : String) : IHyperlink
    def openAndSelect(file : File, offset : Int) : Unit = {
      openAndSelect(file, {
        val tok = file.tokenFor(offset)//.presentationBody
        (tok.offset, tok.text.length)
      })
    }
    def openAndSelect(file : File, select : => (Int,Int)) : Unit
    type File <: FileImpl
    trait FileImpl extends super.FileImpl {selfX : File =>
      def self : File
      def highlight(offset : Int, length : Int, style : Style, txt : TextPresentation) : Unit
      def invalidate(start : Int, end : Int, txt : PresentationContext) : Unit
      def Annotation(kind : String, text : String, offset : => Option[Int], length : Int) : Annotation
      def delete(a : Annotation) : Unit

      type Completion
      def Completion(offset : Int, length : Int, to : String, info : Option[String], image : Option[Image], additional : => Option[String]) : Completion
      def doComplete(offset : Int) : List[Completion] = {
        val tok = tokenForFuzzy(offset)
        assert(offset >= tok.offset)
        try {
          tok.completions(offset - tok.offset)
        } catch {
          case ex => plugin.logError(ex); Nil
        }
      } 
      def refreshHighlightFor(offset : Int, length : Int, txt : TextPresentation) = if (offset < content.length) {
        var tok : Option[Token] = Some(tokenForFuzzy(offset))
        while (!tok.isEmpty && tok.get.offset < offset + length) {
          highlight(tok.get.offset, tok.get.text.length, tok.get.style, txt)
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
      case e => plugin.logError(e)
      }
      
      override def doUnload = {
        super.doUnload
        presentation.clear
      }
      
      trait HasPresentation {
        def isValid : Boolean
        def doPresentation(txt : PresentationContext) : Unit
        def dirtyPresentation = presentation.dirty(this)
      }
      
      private object presentation {
        private val dirtied = new LinkedHashSet[HasPresentation]
        private val rehighlighted = new LinkedHashSet[ParseNode]

        def dirty(node : HasPresentation) = synchronized { dirtied += node }
        
        def rehighlight(node : ParseNode) = synchronized { rehighlighted += node }
        
        def clear = {
          dirtied.clear
          rehighlighted.clear
        }
        
        def drain : (List[HasPresentation], List[ParseNode]) = synchronized {
          val res = (dirtied.toList, rehighlighted.toList)
          clear
          res
        }
      }
      
      def createPresentationContext : PresentationContext
      
      def finishPresentationContext(txt : PresentationContext)
      
      def doPresentation : Unit = {
        job.synchronized {
          if (job.state >= Presentations.ASYNC) return // not now.
      
          // lock out type checking during presentation. 
          val txt = createPresentationContext
          
          val (dirtied, rehighlighted) = presentation.drain
          dirtied.foreach{ node => if (node.isValid) node.doPresentation(txt) }
          rehighlighted.foreach { node => if (node.isValid) if (node.isValid) invalidate(node.absolute, node.absolute + node.length, txt) }
          finishPresentationContext(txt)
        }
      }
      
      type Token <: TokenImpl 
      trait TokenImpl extends super.TokenImpl {
        def self : Token
        def style : Style
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
        def hyperlink : Option[IHyperlink] = None
        //def border(dir : Dir) : Option[Int] = None
        //override def isValid = true
        
        //def matching : Option[Token] = None
        protected final def Completion(                      text0 : String, info : Option[String], image : Option[Image], additional : => Option[String]) = 
          FileImpl.this.    Completion(offset, text.length, text0 : String, info : Option[String], image : Option[Image], additional)
        def completions(offset : Int) : List[Completion] = Nil
        
        def Hyperlink(action : => Unit)(info : String) : IHyperlink = {
          ProjectImpl.this.Hyperlink(FileImpl.this.self, offset, text.length)(action)(info)
        }
      }
      override def repair(offset : Int, added : Int, removed : Int) : Unit = {
        super.repair(offset, added, removed)
      }
      
      override type ParseNode <: ProjectImpl.this.ParseNode with ParseNodeImpl
      trait ParseNodeImpl extends super.ParseNodeImpl with HasPresentation {selfX : ParseNode =>
        def self : ParseNode
        private[Presentations] var annotations : List[Annotation] = Nil
        private[Presentations] var errors : List[(ErrorKind,Annotation)] = Nil
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
        }
        protected override def parseChanged = {
          // should be repainted.
          super.parseChanged
          dirtyPresentation
        }

        protected def highlightChanged : Unit = presentation.rehighlight(self)
      }

      def newError(msg : String) : Annotation
      def uninstall(a : Annotation) : Unit
      def isAt(a : Annotation, idx : Int) : Boolean
      def install(offset : Int, length : Int, a : Annotation) : Unit
    }
    import Presentations._
    def isOpen = true
    def destroy = {
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
            plugin.logError("wrong state: " + state, null)
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
                plugin.logError("ERROR in background thread",null)
              }
              state = previous
              job.notifyAll
            }
          }
        } catch {
          case ex =>
            plugin.logError(ex) // go on.
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

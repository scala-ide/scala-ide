package scala.tools.eclipse.util

object SeqUtils {
  def seqToString(seq : Seq[Char]) : String = seq match { 
    case string : scala.runtime.RichString => string.self 
    case seq =>  
      val i = seq.elements 
      val buf = new StringBuilder 
      while (i.hasNext) buf.append(i.next) 
        buf.toString 
  } 
}

package test

import scala.xml.NodeSeq

class Foo { 
  def competitors() { 
    List("foo", "bar") match { 
      case Nil => Nil 
      case competitors =>
        def bindCompetitors(): scala.xml.NodeSeq = 
          competitors.flatMap { competitor => 
            val l: Int = competitor.length 
            l.foo.toString
          } 
        Nil
    }
  } 
}
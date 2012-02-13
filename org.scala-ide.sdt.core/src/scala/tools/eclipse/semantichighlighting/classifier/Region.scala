package scala.tools.eclipse.semantichighlighting.classifier

case class Region(offset: Int, length: Int) {

  def intersects(other: Region): Boolean =
    !(other.offset >= offset + length || other.offset + other.length - 1 < offset)

  def of(s: Array[Char]): String = s.slice(offset, offset + length).mkString  
    
  def of(s: String): String = s.slice(offset, offset + length)

}

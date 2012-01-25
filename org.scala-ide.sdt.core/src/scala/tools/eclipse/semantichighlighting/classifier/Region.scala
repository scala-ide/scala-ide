package scala.tools.eclipse.semantichighlighting.classifier

case class Region(offset: Int, length: Int) {

  def intersects(other: Region) =
    !(other.offset >= offset + length || other.offset + other.length - 1 < offset)

  def of(s: String) = s.slice(offset, offset + length)

}

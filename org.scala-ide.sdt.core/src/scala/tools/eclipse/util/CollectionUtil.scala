package scala.tools.eclipse.util

object CollectionUtil {

  import scala.collection.GenTraversable

  /** Split the passed traversable collection at the head, returning a pair {{{(xs.headOption, xs.tail)}}}. If the
   * collection is empty then {{{(None, xs)}}} is returned.*/
  def splitAtHead[A](xs: GenTraversable[A]): (Option[A], GenTraversable[A]) =
    if(xs.isEmpty) (None, xs)
    else (Some(xs.head), xs.tail)


  import scala.collection.mutable.ListBuffer

  /** Split the passed traversable collection at the last element, returning a pair {{{(xs.init, xs.lastOption)}}}. If the
   * collection is empty then {{{(Nil, None)}}} is returned.*/
  def splitAtLast[A](xs: GenTraversable[A]): (List[A], Option[A]) =
    if(xs.isEmpty) (xs.toList, None)
    else {
      val init = new ListBuffer[A]
      val it = xs.toIterator
      val last = xs.size - 1
      var i = 0
      while(i < last) {
        init += it.next()
        i += 1
      }
      (init.toList, Some(it.next()))
    }
}
package occ

class DummyOccurrences(param: Int, func/*<2*/: (Int/*<5*/, Int) => Int) {
  type T/*<2*/ = Int

  def sum(xs: List[T]) = {
    xs.foldLeft(param/*<3*/)(_ + _)
    for (j <- xs) {
      (param /: xs)(func)
    }
  }
}
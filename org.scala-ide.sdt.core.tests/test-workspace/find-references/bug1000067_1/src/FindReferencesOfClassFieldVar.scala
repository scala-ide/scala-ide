class Referred {
  var aVar = 0
  val aVal = 0
  def aMethod = 2
}

class Referring {
  val obj = new Referred

  def anotherMethod {
    obj.aVar/*ref*/
    obj.aVal
    obj.aMethod
  }

  def yetAnotherMethod {
    obj.aVar
    obj.aVal
    obj.aMethod
  }
}

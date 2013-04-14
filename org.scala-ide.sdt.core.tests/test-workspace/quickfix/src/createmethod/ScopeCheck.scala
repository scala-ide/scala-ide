package createmethod

class Class1
class Class2
class Class3
class Class4
class Class5
class Class6

class Parent {
  val class1 = new Class1
  private val class6 = new Class6
}

class ScopeCheck extends Parent {
  val class2 = new Class2
  def class3 = new Class3
  def class4() = new Class4
  def class5(a: Int) = new Class5
  
  def method(param1: Int) {
    val findMe = 0
  }
}
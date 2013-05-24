package createmethod

class OtherClass {
  def existingMethod = 3.3
}

class CreateMethod {
  val other = new OtherClass
  //easy
  other.easy1("a", 3) //Create method 'easy1(String, Int)' in type 'OtherClass'
  easy2(other) //Create method 'easy2(OtherClass)'
  
  //multiple arguments
  other.multiple1(3)(other, 1.2) //Create method 'multiple1(Int)(OtherClass, Double)' in type 'OtherClass'
  multiple2{"abc"}(null) //Create method 'multiple2(Any)(AnyRef)'
  
  //named parameters
  other.named1(abc = "a b c", num = 1) //Create method 'named1(String, Int)' in type 'OtherClass'
  
  //expected return type
  val list = List(1, 2, 3)
  list.drop(other.expected1) //Create method 'expected1(): Int' in type 'OtherClass'
  def namedMethod(arg0: Int, arg1: String) = ???
  namedMethod(arg1=other.expected2, arg0 = 3) //Create method 'expected2(): String' in type 'OtherClass'
  
  //higher order functions & named parameters
  list.map(other.higher1) //Create method 'higher1(Int)' in type 'OtherClass'
  list.sortWith(other.higher2) //Create method 'higher2(Int, Int): Boolean' in type 'OtherClass'
  list.sortWith(higher3) //Create method 'higher3(Int, Int): Boolean'
  def higherOrderSquared(f: (String => Double) => (Int => Char)) = ???
  higherOrderSquared(other.higher4) //Create method 'higher4(String => Double): Int => Char' in type 'OtherClass'
  def higherOrderNamed(arg0: Int, arg1: Int)(arg2: String, f: Double => String) = ???
  higherOrderNamed(0, 0)(f = other.higher5, arg2 = "") //Create method 'higher5(Double): String' in type 'OtherClass'
  higherOrderNamed(0, 0)(f = higher6, arg2 = "") //Create method 'higher6(Double): String'
  
  //special functions
  !other //Create method 'unary_!(): OtherClass' in type 'OtherClass'
  -other //Create method 'unary_-(): OtherClass' in type 'OtherClass'
  
  //infix
  other infix1 "a" //Create method 'infix1(String)' in type 'OtherClass'
  other infix2 list //Create method 'infix2(List[Int])' in type 'OtherClass'
  other infix3("a") //Create method 'infix3(String)' in type 'OtherClass'
  other infix4 ("a") //Create method 'infix4(String)' in type 'OtherClass'
  other infix5 ("a", 3, other) //Create method 'infix5(String, Int, OtherClass)' in type 'OtherClass'
  other infix6 ("a", 3, other, other.existingMethod) //Create method 'infix6(String, Int, OtherClass, Double)' in type 'OtherClass'
  
  //infix with named parameters & multiple argument lists
  other namedinfix1 (aaa=3, bbb = other, "a") //Create method 'namedinfix1(Int, OtherClass, String)' in type 'OtherClass'
  other namedinfix2 ("a")(3, bbb = other)("b") //Create method 'namedinfix2(String)(Int, OtherClass)(String)' in type 'OtherClass'
  
  //inix on this
  selfinfix1 (1, "a") //CreateMethodProposal - Create method 'selfinfix1(Int, String)'
  selfinfix2 (1, a="a")(other, aaa="b", 5) //Create method 'selfinfix2(Int, String)(OtherClass, String, Int)'
 
  //compound statements on lhs
  List(other)(0).compound1 //Create method 'compound1()' in type 'OtherClass'
  
  //get the proper type for complex expressions
  other.complex1(list.map(_.toDouble)) //Create method 'complex1(List[Double])' in type 'OtherClass'
  other.complex2(named = list ++ list) //Create method 'complex2(List[Int])' in type 'OtherClass'
  other.complex3(list, list.map(num => List(num)))(abc = list.isEmpty) //Create method 'complex3(List[Int], List[List[Int]])(Boolean)' in type 'OtherClass'
  
  //these don't work yet, due to a problem with the compiler, we get the type <error> and fall back to displaying "Any"
  other.complex4("a" * 3) //should be: Create method 'complex4(String)' in type 'OtherClass'
  other.complex5(list + "a") //should be: Create method 'complex5(String)' in type 'OtherClass'
  import scala.collection.JavaConversions._
  import scala.collection.JavaConverters._
  val javaList = list.asJava
  other.complex6(javaList.map(_ * 2)) //should be: Create method 'complex6(List[Int])' in type 'OtherClass'
  
  //shouldn't show up for assignment, we're not automatically testing this though
  //other.assign1 = "asdf"
  //assign2 = 3
}

object RedHerring {
  /*
   * We try to grab the class from the types that are in scope, but when that
   * fails we fall back on doing a type search by name from the error message.
   * If we find a *unique* result in the project, we use it.
   * This is here to disable that, meaning all of these results should be from
   * the primary way of finding the target class, the compiler scope information.
   */
  //class OtherClass
}
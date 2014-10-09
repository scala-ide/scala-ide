package basic

object Basic {

  // look at TypeMismatchQuickFixProcessor.cases for quick fixes to expect

  // lists
  val listOfInt_expression: List[Int] = List[List[Int]]()
  val listOfInt_val = List[List[Int]]()
  val listOfInt_identifier: List[Int] = listOfInt_val

  // options
  val intVal = 5
  val optionOfInt_identifier: Option[Int] = intVal

  // arrays
  val arrayOfInt_expression: Array[Int] = List[Int]()
  val arrayOfInt_val = List[Int]()
  val arrayOfInt_identifier: Array[Int] = arrayOfInt_val

  // options literals
//  val optionOfInt_expression: Option[Int] = 5
//  val optionOfInt_expression2: Option[Int] = 5 * 3 + 2
//  val optionOfInt_expression3: Option[Int] = 5 * 3 + intVal
//  val optionOfBoolean_expression: Option[Boolean] = true
//  val optionOfBoolean_expression2: Option[Boolean] = (intVal % 4 == 2)
//  val optionOfChar_expression: Option[Char] = '5'
//  val optionOfString_expression: Option[String] = "asd"
//  val optionOfFloat_expression: Option[Float] = 5.3f

}
package basic

object Basic {
    
  // look at TypeMismatchQuickFixProcessor.cases for quick fixes to expect
  
  // lists
  val listOfInt_expression: List[Int] = List[List[Int]]()
  val listOfInt_val = List[List[Int]]()
  val listOfInt_identifier: List[Int] = listOfInt_val
  
  // options
  val optionOfInt_expression: Option[Int] = 5  
  val intVal = 5  
  val optionOfInt_identifier: Option[Int] = intVal
  
  // arrays
  val arrayOfInt_expression: Array[Int] = List[Int]()
  val arrayOfInt_val = List[Int]()
  val arrayOfInt_identifier: Array[Int] = arrayOfInt_val

}
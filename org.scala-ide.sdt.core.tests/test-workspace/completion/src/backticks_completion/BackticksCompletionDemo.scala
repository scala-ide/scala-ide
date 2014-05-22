package backticksCompletion

class NormalClassName

class `weird class name`

case class abcαβγ_!^©®(i: Int)

trait `misnamed/trait`

object `YOLO Obj` {
  /** Members below that are defined with backticks here would be invalid without them, so at completion time,
   *  they should also be auto-completed with backticks. Conversely, The fields that are not defined with
   *  backticks should be auto-completed without backticks.
   */

  def normalDef() {}

  def `weird/def`(name: String) = println(s"Yo, $name!")

  val `text/plain` = "text/plain"

  var lolcαβγ_!^©® = 3

  val `badchar£2` = 1

  val `while` = "reserved word"
}

object CompletionsDemo {
  new Nor /*!*/  // Should complete WITHOUT backticks (NormalClassName)

  new we /*!*/   // Should complete WITH backticks (`weird class name`)

  abc /*!*/    // WITHOUT (abcαβγ_!^©®)

  new `weird class name` with mis /*!*/  // WITH (`misnamed/trait`)

  YO /*!*/  // WITH (`YOLO Obj`)

  `YOLO Obj`.nor /*!*/  // WITHOUT

  `YOLO Obj`.wei /*!*/  // WITH

  `YOLO Obj`.tex /*!*/  // WITH

  `YOLO Obj`.lol /*!*/  // WITHOUT

  `YOLO Obj`.badc /*!*/  // WITH

  `YOLO Obj`.whi /*!*/  // WITH

}

package ticket_1001766

class Ticket1001766 {
  /* Test 1 */
  def buz() = ()

  // this should be completed with empty-parens
  buz /*!*/

  /* Test 2 */
  def bar = ()

  // this should be completed without adding empty-parens
  bar /*!*/
}
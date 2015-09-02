package acme

class AcmeTest {
  def baz = "3" + (new AcmeMacrosAsMain).foobar
}
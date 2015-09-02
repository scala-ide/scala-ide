package acme

class AcmeMacrosAsMain {
  def foobar = (new AcmeMacro).foo + (new AcmeMain).bar
}
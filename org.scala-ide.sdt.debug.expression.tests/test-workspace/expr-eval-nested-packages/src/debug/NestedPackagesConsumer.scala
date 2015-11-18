package debug
import debug.nested.packages
object NestedPackagesConsumer extends App {
  val breakpointHere = packages.NestedPackagesSample.foo
  println(5)
  println(breakpointHere)
}
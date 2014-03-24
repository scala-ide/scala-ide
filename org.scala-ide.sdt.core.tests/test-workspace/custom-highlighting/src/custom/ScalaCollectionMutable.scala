/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package scalaCollectionMutable

import scala.collection.mutable

object Main {

  val mySeq = mutable.Seq(1, 2, 3)

  mySeq.head

  mySeq.foreach { a => println(a) }

  def print(map: mutable.Map) {
    map.foreach { case (a, b) => println(s"$a: $b") }
  }
}
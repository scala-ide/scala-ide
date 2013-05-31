package scala.tools.eclipse.util

import org.junit.Test
import junit.framework.Assert.assertEquals


class CollectionUtilTest {
  @Test
  def splitAtHeadAnEmptyList {
    // when
    val xs = Nil

    // then
    val (head, tail) = CollectionUtil.splitAtHead(xs)

    // verify
    assertEquals(head, xs.headOption)
    assertEquals(tail, xs)
  }

  @Test
  def splitAtHeadAListWithOneElement {
    // when
    val xs = List(1)

    // then
    val (head, tail) = CollectionUtil.splitAtHead(xs)

    // verify
    assertEquals(head, xs.headOption)
    assertEquals(tail, xs.tail)
  }

  @Test
  def splitAtHeadAListWithSeveralElements {
    // when
    val xs = List(1, 2, 3, 4)

    // then
    val (head, tail) = CollectionUtil.splitAtHead(xs)

    // verify
    assertEquals(head, xs.headOption)
    assertEquals(tail, xs.tail)
  }

  @Test
  def splitAtLastAnEmptyList {
    // when
    val xs = Nil

    // then
    val (init, last) = CollectionUtil.splitAtLast(xs)

    // verify
    assertEquals(init, xs)
    assertEquals(last, xs.lastOption)
  }

  @Test
  def splitAtLastAListWithOneElement {
    // when
    val xs = List(1)

    // then
    val (init, last) = CollectionUtil.splitAtLast(xs)

    // verify
    assertEquals(init, xs.init)
    assertEquals(last, xs.lastOption)
  }

  @Test
  def splitAtLastAListWithSeveralElements {
    // when
    val xs = List(1, 2, 3, 4)

    // then
    val (init, last) = CollectionUtil.splitAtLast(xs)

    // verify
    assertEquals(init, xs.init)
    assertEquals(last, xs.lastOption)
  }
}
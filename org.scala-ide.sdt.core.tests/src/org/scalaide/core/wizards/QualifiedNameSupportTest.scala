/*
 * Copyright 2010 LAMP/EPFL
 *
 */
package scala.tools.eclipse.wizards

import org.junit.Test
import org.junit.Assert

class QualifiedNameSupportTest extends QualifiedNameSupport {

  import Assert._

  private val Test_Data = "scala.collection.immutable.Set[T]"
  private val Test_Data2 = "scala.collection.immutable.Map[Key,Value]"
  private val Test_Data3 = "java.util.Map<Key,Value>"
  private val Test_Data4 = "scala.math.Equiv[Eee]"

  @Test
  def createSuperTypeInstance() {
    val st = createSuperType(Test_Data)
    assertTrue(st.packageName == List("scala", "collection", "immutable"))
    assertTrue(st.typeName == "Set")
    assertTrue(st.params == List("T"))
  }

  @Test
  def createSuperTypeInstance2() {
    val st = createSuperType(Test_Data4)
    assertTrue(st.packageName == List("scala", "math"))
    assertTrue(st.typeName == "Equiv")
    assertTrue(st.params == List("Eee"))
  }

  @Test
  def packageAndTypeNameTuple() = {
    val (packageName, typeName) = packageAndTypeNameOf(Test_Data)
    assertTrue(packageName == "scala.collection.immutable")
    assertTrue(typeName == "Set")
  }

  @Test
  def typeWithoutParameters() {
    val t = removeParameters(Test_Data)
    assertTrue(t == "scala.collection.immutable.Set")
  }

  @Test
  def typeRemovePackage() {
    val t = removePackage(Test_Data)
    assertTrue(t == "Set[T]")
  }

  @Test
  def typeRemovePackage2() {
    val t = removePackage(Test_Data2)
    assertTrue(t == "Map[Key,Value]")
  }

  @Test
  def typeRemovePackage3() {
    val t = removePackage(Test_Data3)
    assertTrue(t == "Map[Key,Value]")
  }

  @Test
  def packageOfType() {
    val t = packageOf(Test_Data)
    assertTrue(t == "scala.collection.immutable")
  }

  @Test
  def nameOfType() {
    val t = typeNameOf(Test_Data)
    assertTrue(t == "Set")
  }

  @Test
  def parametersOfType() {
    val ps = parametersOf(Test_Data2)
    assertTrue(ps == List("Key", "Value"))
  }

  @Test
  def parametersOfType2() {
    val ps = parametersOf(Test_Data3)
    assertTrue(ps == List("Key", "Value"))
  }
}

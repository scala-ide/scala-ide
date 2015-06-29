package org.scalaide.ui.internal.editor

import scala.collection.mutable.Map

trait PreferenceProvider {
  private val preferences = Map.empty[String, String]

  def updateCache(): Unit

  def put(key: String, value: String): Unit = {
    preferences(key) = value
  }

  def get(key: String): String = {
    preferences(key)
  }

  def getBoolean(key: String): Boolean = {
    get(key).toBoolean
  }

  def getInt(key: String): Int = {
    get(key).toInt
  }
}

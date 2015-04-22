package org.scalaide.sbt.core

import scala.concurrent.ExecutionContext
import scala.pickling.Unpickler

import sbt.client.SbtClient
import sbt.client.SettingKey
import sbt.client.Subscription
import sbt.client.TaskKey
import sbt.client.ValueListener
import sbt.protocol.ScopedKey

object KeyProvider {

  import scala.language.higherKinds

  /**
   * Allows to abstract at least over [[sbt.client.TaskKey]] and [[sbt.cleint.SettingKey]].
   */
  trait KeyProvider[M[_]] {
    def key[A](key: ScopedKey): M[A]
    def watch[A : Unpickler](a: M[A])(listener: ValueListener[A])(implicit ex: ExecutionContext): Subscription
  }

  implicit def TaskKeyKP(implicit client: SbtClient) = new KeyProvider[TaskKey] {
    def key[A](key: ScopedKey) = TaskKey(key)
    def watch[A : Unpickler](key: TaskKey[A])(listener: ValueListener[A])(implicit ex: ExecutionContext): Subscription =
      client.watch(key)(listener)
  }

  implicit def SettingKeyKP(implicit client: SbtClient) = new KeyProvider[SettingKey] {
    def key[A](key: ScopedKey) = SettingKey(key)
    def watch[A : Unpickler](key: SettingKey[A])(listener: ValueListener[A])(implicit ex: ExecutionContext): Subscription =
      client.watch(key)(listener)
  }

}

/*
 * Copyright (c) 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal
package expression

import org.junit.AfterClass
import org.junit.BeforeClass

/**
 * Caches eclipse project for whole test case.
 * Project is started before target class and cleaned at the end.
 * In case on nested usage of `CachedEclipseProjectContext`
 * provider is created once but it is cleaned every time target ends.
 */
class CachedEclipseProjectContext {

  private class Provider extends EclipseProjectContextProvider {
    private var state: Map[String, Context] = Map.empty

    def clean(): Unit = {
      state.values.foreach(_.cleanAll())
      state = Map.empty
    }

    override def createContext(projectName: String): EclipseProjectContext =
      state.get(projectName).getOrElse {
        val newContext = new Context(StandardContextProvider.createSetup(projectName))
        state += projectName -> newContext
        newContext
      }
  }

  private class Context(setup: EclipseProjectContext.ScalaDebugProjectSetup) extends EclipseProjectContext(setup) {
    // val so it is executed only once
    override val refreshBinaries: Unit = super.refreshBinaries

    override def cleanProject(): Unit = {}

    def cleanAll() = super.cleanProject()
  }

  @BeforeClass
  def withCachedContext(): Unit = {
    EclipseProjectContext.currentProvider match {
      case None =>
        EclipseProjectContext.currentProvider = Some(new Provider)
      case ignored =>
    }
  }

  @AfterClass
  def cleanCachedContext(): Unit = {
    EclipseProjectContext.currentProvider match {
      case Some(provider: Provider) =>
        provider.clean()
    }
  }
}
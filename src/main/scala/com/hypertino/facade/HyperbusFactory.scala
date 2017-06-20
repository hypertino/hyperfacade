package com.hypertino.facade

import java.util.concurrent.{Executor, SynchronousQueue, ThreadPoolExecutor, TimeUnit}

import com.hypertino.hyperbus.Hyperbus
import com.hypertino.hyperbus.transport.api.{TransportConfigurationLoader, TransportManager}
import com.typesafe.config.Config
import scaldi.Injector

import scala.concurrent.ExecutionContext

class HyperbusFactory(val config: Config, val inj: Injector) {
  lazy val hyperbus = new Hyperbus(newTransportManager(), HyperbusFactory.defaultHyperbusGroup(config))

  private def newPoolExecutor(): Executor = {
    val maximumPoolSize: Int = Runtime.getRuntime.availableProcessors() * 16
    new ThreadPoolExecutor(0, maximumPoolSize, 5 * 60L, TimeUnit.SECONDS, new SynchronousQueue[Runnable])
  }

  private def newTransportManager(): TransportManager = {
    new TransportManager(TransportConfigurationLoader.fromConfig(config, inj))(inj)
  }
}

object HyperbusFactory {
  def defaultHyperbusGroup(config: Config) = {
    if (config.hasPath(FacadeConfigPaths.HYPERBUS_GROUP))
      Some(config.getString(FacadeConfigPaths.HYPERBUS_GROUP))
    else None
  }
}
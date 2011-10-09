package t1000658

object ThreadPoolConfig {
  type FlowHandler = String
}

case class ThreadPoolConfig(flowHandler: ThreadPoolConfig.FlowHandler = "")
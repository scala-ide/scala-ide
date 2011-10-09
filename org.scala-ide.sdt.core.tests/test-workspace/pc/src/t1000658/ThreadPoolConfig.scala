package t1000658

import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy
import java.util.concurrent.RejectedExecutionHandler

object ThreadPoolConfig {
  type Bounds = Int
  type FlowHandler = Either[RejectedExecutionHandler, Bounds]
  
  def defaultFlowHandler: FlowHandler = flowHandler(new CallerRunsPolicy)
  
  def flowHandler(rejectionHandler: RejectedExecutionHandler): FlowHandler = Left(rejectionHandler)
}

case class ThreadPoolConfig(flowHandler: ThreadPoolConfig.FlowHandler = ThreadPoolConfig.defaultFlowHandler)
package t1000692.akka.config

import t1000692.akka.exception.AkkaException

/* To make the code work, either:
 * 
 * 1) remove the import clause and use the fully qualified AkkaException type
 * 
 * 2) remove the Config object or invert the declaration order (i.e., declare Config BEFORE ModuleNotAvailableException)
 */
class ModuleNotAvailableException(message: String, cause: Throwable = null) extends AkkaException(message, cause)

object Config
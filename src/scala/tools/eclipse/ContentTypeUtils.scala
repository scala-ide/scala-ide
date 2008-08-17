package scala.tools.eclipse

import org.eclipse.core.runtime.{ ListenerList, Platform }
import org.eclipse.core.runtime.content.IContentTypeSettings
import org.eclipse.jdt.core.JavaCore
import org.eclipse.jdt.internal.core.util.Util

import lampion.util.ReflectionUtils

object ContentTypeUtils extends ReflectionUtils {
  private val ctm = Platform.getContentTypeManager
  private val contentType = ctm.getContentType(JavaCore.JAVA_SOURCE_CONTENT_TYPE)
  private val ctmClazz = Class.forName("org.eclipse.core.internal.content.ContentTypeManager")
  private val contentTypeListenersField = getField(ctmClazz, "contentTypeListeners")
  
  def addJavaLikeExtension {
    silently { contentType.addFileSpec("scala", IContentTypeSettings.FILE_EXTENSION_SPEC) }
  }
  
  def removeJavaLikeExtension {
    silently { contentType.removeFileSpec("scala", IContentTypeSettings.FILE_EXTENSION_SPEC) }
  }
  
  private def silently[T](b : => T) : T = {
    val oldList = contentTypeListenersField.get(ctm)
    try {
      
      contentTypeListenersField.set(ctm, new ListenerList)
      val result = b
      Util.resetJavaLikeExtensions
      result
    } finally {
      contentTypeListenersField.set(ctm, oldList)
    }
  } 
  
  def withJavaLikeExtension[T](b : => T) : T = {
    val wasJavaLike = hasJavaLikeExtension
    try {
      if (!wasJavaLike)
        addJavaLikeExtension
      b
    } finally {
      if (!wasJavaLike)
        removeJavaLikeExtension
    }
  }

  def withoutJavaLikeExtension[T](b : => T) : T = {
    val wasJavaLike = hasJavaLikeExtension
    try {
      if (wasJavaLike)
        removeJavaLikeExtension
      b
    } finally {
      if (wasJavaLike)
        addJavaLikeExtension
    }
  }
  
  private def hasJavaLikeExtension = {
    contentType.getFileSpecs(IContentTypeSettings.FILE_EXTENSION_SPEC).contains("scala")
  }
}

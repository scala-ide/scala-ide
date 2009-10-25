package scala.tools.eclipse.contribution.weaving.jdt.hierarchy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ResourceBundle;

import org.eclipse.jdt.internal.ui.javaeditor.JavaSelectAnnotationRulerAction;
import org.eclipse.jface.action.Action;
import org.eclipse.ui.texteditor.ResourceAction;
import org.eclipse.ui.texteditor.SelectMarkerRulerAction;

import scala.tools.eclipse.contribution.weaving.jdt.util.ReflectionUtils;

@SuppressWarnings("restriction")
public class JavaSelectAnnotationRulerActionUtils {
  private static final Method initializeMethod;
  private static final Method hasMarkersMethod;
  private static final Method setEnabledMethod;
  
  static {
    Class<?> raClazz = ResourceAction.class;
    initializeMethod = ReflectionUtils.getMethod(raClazz, "initialize", ResourceBundle.class, String.class);
    
    Class<?> smraClazz = SelectMarkerRulerAction.class;
    hasMarkersMethod = ReflectionUtils.getMethod(smraClazz, "hasMarkers");

    Class<?> aClazz = Action.class;
    setEnabledMethod = ReflectionUtils.getMethod(aClazz, "setEnabled", boolean.class);
  }
  
  public static void initialize(JavaSelectAnnotationRulerAction jsara, ResourceBundle bundle, String prefix) {
    try {
      initializeMethod.invoke(jsara, bundle, prefix);
    } catch (IllegalArgumentException e) {
      throw new RuntimeException(e);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    } catch (InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  public static boolean hasMarkers(JavaSelectAnnotationRulerAction jsara) {
    try {
      return (Boolean)hasMarkersMethod.invoke(jsara);
    } catch (IllegalArgumentException e) {
      throw new RuntimeException(e);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    } catch (InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  public static void setEnabled(JavaSelectAnnotationRulerAction jsara, boolean enabled) {
    try {
      setEnabledMethod.invoke(jsara, enabled);
    } catch (IllegalArgumentException e) {
      throw new RuntimeException(e);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    } catch (InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }
}

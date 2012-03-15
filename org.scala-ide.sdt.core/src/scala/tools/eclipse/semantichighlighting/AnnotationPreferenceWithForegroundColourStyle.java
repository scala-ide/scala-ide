package scala.tools.eclipse.semantichighlighting;

import org.eclipse.ui.texteditor.AnnotationPreference;

// Written in Java so we can access the protected static field TEXT_STYLE_PREFERENCE_KEY
public class AnnotationPreferenceWithForegroundColourStyle extends AnnotationPreference {
  
  public AnnotationPreferenceWithForegroundColourStyle(Object annotationType, String textKey, String styleKey) {
    super(annotationType, "not-used", textKey, "not-used", 0);
    setValue(TEXT_STYLE_PREFERENCE_KEY, styleKey);
  }
  
}

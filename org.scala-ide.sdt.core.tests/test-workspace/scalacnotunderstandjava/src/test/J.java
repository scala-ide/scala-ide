package test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(value=RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE_USE, ElementType.PARAMETER, ElementType.METHOD, ElementType.TYPE})
@interface ScalacDoesNotGetMe {}

class J<@ScalacDoesNotGetMe X> {
  public int f = (new S()).foo();
}
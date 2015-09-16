package debug;

public class JavaLibClass {

  static class ToStringProvider {
    private final int someInt;

    public ToStringProvider(int x) {
      someInt = x;
    }

    @Override
    public String toString() {
      return String.format("%s(%s)", this.getClass().getSimpleName(), someInt);
    }
  }

  static class GenericToStringProvider<T> {
    private final int someInt;
    private final T someT;

    public GenericToStringProvider(int x, T y) {
        someInt = x;
        someT = y;
      }

    @Override
    public String toString() {
      return String.format("%s[%s](%s, %s)", this.getClass().getSimpleName(), someT.getClass().getSimpleName(), someInt, someT);
    }
  }

  public class InnerClass extends ToStringProvider {

    public class InnerInInner extends ToStringProvider {

      public InnerInInner(int x) {
        super(x);
      }
    }

    public InnerClass(int x) {
      super(x);
    }
  }

  public class InnerGenericClass<T> extends GenericToStringProvider<T> {

    public InnerGenericClass(int x, T y) {
      super(x, y);
    }
  }

  public static class InnerStaticClass extends ToStringProvider {

    public class InnerInStatic extends ToStringProvider {

      public InnerInStatic(int x) {
        super(x);
      }
    }

    public static class InnerStaticInStatic extends ToStringProvider {
      public static int staticInt = -4;

      public InnerStaticInStatic(int x) {
        super(x);
      }

      public static String innerStaticStringMethod(String prefix, int someNumber) {
        return prefix + someNumber + "_otherSuffix";
      }

      public static int innerStaticIntMethod() {
        return 77;
      }
    }

    public static JavaLibClass staticInstanceOfOuterClass = new JavaLibClass();

    public static String staticString = "otherString";
    public static double innerStaticDouble = 2.5 + staticInt;

    public InnerStaticClass(int x) {
      super(x);
    }

    public static String innerStaticMethod(String prefix) {
      return staticStringMethod(prefix) + "_additionalSuffix";
    }

    public static <T> T innerStaticGenericMethod(T x) {
      return x;
    }
  }

  public static class InnerStaticGenericClass<T> extends GenericToStringProvider<T> {

    public class InnerGenericInStaticGeneric<K> extends GenericToStringProvider<K> {

      public InnerGenericInStaticGeneric(int x, K y) {
        super(x, y);
      }
    }

    public static class InnerStaticGenericInStaticGeneric<K> extends GenericToStringProvider<K> {

      public InnerStaticGenericInStaticGeneric(int x, K y) {
        super(x, y);
      }
    }

    public InnerStaticGenericClass(int x, T y) {
      super(x, y);
    }
  }

  public static String staticString = "staticString";
  public static int staticInt = 1;
  public static String staticNull = null;

  public int normalInt = 1;
  public String normalString = "it's not static";
  public JavaLibClass self = this;
  public String normalStringToChange = "original";
  public Double normalNull = null;

  public static String staticStringMethod(String prefix) {
    return prefix + "_suffix";
  }

  public static int staticIntMethod() {
    return 700;
  }

  public static <T> T staticGenericMethod(T x) {
    return x;
  }

  public <T> T genericMethod(T x) {
    return x;
  }

  public int[] varArgMethod(int... x) {
      return x;
  }

  public <T> T[] varArgGenericMethod(T... x) {
      return x;
  }

  public static int[] staticVarArgMethod(int... x) {
      return x;
  }

  public static <T> T[] staticGenericVarArgMethod(T... x) {
      return x;
  }

  @Override
  public String toString() {
    return "JavaLibClass()";
  }
}

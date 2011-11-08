package ticket_1000412.test;

import ticket_1000412.model.ClassA;
/*!*/
public class TestJavaReference {

    private ClassA get() {
        return null;
    }

    private static ClassA b= new ClassA();

    private String var1= b. // this case is a limitation. The AST doesn't contain any trace of the 'b.'.

    private String var2= b.ge/*!*/

    private String var3= b.getX/*!*/();

    public static void foo1() {
        ClassA a = new ClassA();
        a.getX/*!*/();
    }

    public static void foo2() {
        ClassA a = new ClassA();
        a./*!*/
        // just to check that comments are correctly managed
    /*and it is case too*/}

    public static void foo3() {
        ClassA a = new ClassA();
        a.ge/*!*/
    }

    public static void foo4() {
        ClassA a = new ClassA();
        a.ge/*!*/=25;
    }

    public static void foo5() {
        ClassA a = new ClassA();
        a.getX/*!*/().length();
    }

    public static void foo6() {
        ClassA a = new ClassA();/*!*/
        a.getX().length();
    }

    public void foo7() {
        get().ge/*!*/
    }

    public void foo8() {
        get()./*!*/
    }

    public static void foo9() {
        ClassA a = new ClassA();
        String s = a./*!*/
    }

    public void foo10() {
        ClassA a = new ClassA();
        String s = get()./*!*/
    }

    public static void foo11() {
        ClassA a = new ClassA()./*!*/
    }

    public static void foo12() {
        new ClassA()./*!*/
    }

    public static void foo13() {
        new ClassA().ge/*!*/;
    }

}


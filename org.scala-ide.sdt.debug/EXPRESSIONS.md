Expression Evaluator
====================

This site summarizes abilities of Scala IDE Debugger Expression evaluator (code name _Firefly_).

Firefly comes with two main features: expression evaluator and conditional breakpoints. 

Supported features
------------------

#### Access to fields and methods from enclosed class

Access to fields and methods from enclosing class and it's parents is supported, both with 
explicit and implicit `this`.

Debug context:
```scala
class A {
  val a = 1
  def b() = 2
}

class B extends A {
  val c = 3
  def d() = 4
  def foo() {
    // breakpoint here
  }
}
```

Expression to evaluate:
```scala
this.a // 1
b() // 2
c // 3
this.d() // 4
```

#### Access to arguments of debugged method

Arguments to methods are visible from inside of methods. Argument can shadow local variables with same name.

Debug context:
```scala
class Foo {
  val a: Int = 1

  def foo(a: String) {
    // breakpoint here
  }
}
```

Expression to evaluate:
```scala
a // returns a of type 'String'
```

#### Methods with multiple parameter lists

Multiple arguments lists are also supported without any restrictions.

Debug context:
```scala
(...)
def foo(a: Int)(b: String): String = b + a
(...)
```

Expression to evaluate:
```scala
foo(12)("ala ma kota")
```

#### Variadic methods (varargs)

TODO - more about this feature

Debug context:
```scala
code you debug
```

Expression to evaluate:
```scala
code you evaluate
```

#### Nested methods closing over values

In nested methods you have access to values from outer scopes but *only if* they are used in
nested function body. Unused values are inaccessible because of how Scala implements nested
methods.

Debug context:
```scala
(...)
def outer(): Int = {

  val outerUsed = 1
  val outerUnused = 2

  def inner(): Int = {
    val result = outerUsed + 1
    // breakpoint here
    result
  }

  inner()
}

```

Expression to evaluate:
```scala
outerUsed // 1
outerUnused // will fail
```

#### Conditional expressions

Conditional expressions (both simple and nested) are supported and can be used in evaluated 
expression.

Debug context:
```scala
val condition: Boolean = (...)
```

Expression to evaluate:
```scala
if (condition) 1 else 2
```

#### Numeric, string, symbol and character literals

Literals for all numbers, characters, symbols and strings are supported (with exception of floats).

Expression to evaluate:
```scala
1
0xFFFFFFFF
1L
123456789L
1.0
1e30
3.14159
1.0e100
.1
'c'
'\t'
'ala
"ala"
"""ala"""
```

#### Synthetic methods on primitives

Synthetic methods on primitive (boxed) types are implemented and can be used.

That includes:
- numeric operations: `+`, `-`, `/`, `*`, `%` and unary `-`
- comparison: `==`, `!=`, `<`, `>`, `<=`, `>=` (last 4 yet unsupported on booleans)
- bitwise: `|`, `&`, `^`, `<<`, `>>>`, `>>` and unary `~`
- logic (on booleans): `||`, `&&` and unary `!`

Debug context:
```scala
val byte: Byte = 4
val short: Short = 6
val int = 1
val double = 1.1
val float = 1.1f
```

Expression to evaluate:
```scala
((6.0 / double).toLong ^ (float * short).toByte) < (-int ^ 2) / 1 + double - float
```

#### Tuple literals

Tuple literals are supported and can be used in expressions.

Debug context:
```scala
def foo(data: (Int, Double)): Boolean
```

Expression to evaluate:
```scala
foo((2, 3.0))
```

#### Creating instances using `new` operator

New instances of classes could be created on debugged machine using `new` keyword.

Debug context:
```scala
class Foo(arg: Int) {
  (...)
}

val a: Foo = (...)
```

Expression to evaluate:
```scala
a == new Foo(12)
```

#### Lambdas

Lambdas can be used in expressions. Code generated for them is then loaded to 
remote JVM and executed.

Not all kinds of lambdas are supported yet, the problem lies in lack of generic 
types in runtime. Provided expression are type-checked so when we lack information 
about a type we can't replace this type with our dynamic proxy.

It applies to method that require special type example like filter, flatMap etc.

Primitives and synthetic operation on them are also a problem, as we have to treat 
them specially (there is no + method on `Integer`, it is translated to `iadd` bytecode).

We try to do as much as we can to avoid those problems.

Closures are currently not supported - but we plan to support this.

What we know that works:

Debug context:
```scala
val intList: List[Int] = List(1, 2, 3)
```

Expression to evaluate:
```scala
intList.map((_: Int) + 1)
intList.filter( (_: Int) > 1)
intList.foreach(println)
```

Basically if for you get compilation error for code that is valid - try providing
types for arguments.

For example
```scala
intList.map(_ + 1)
```
won't work but this will:
```scala
intList.map((_: Int) + 1)
```

#### Partial functions

Partial function are lambdas - so they have similar problems.
We currently support only simple case of partial function from A to B.
Scala compiler does some magic so you can apply partial function to things like
(A, B) => C but those are not supported yet.

Debug context:
```scala
val intList: List[Int] = List(1, 2, 3)
```

Expression to evaluate:
```scala
intList.filter{case a: Int => a > 2}
```

#### Assigning new values to variables

Assigning new values to variables are currently supported for fields only.

Debug context:
```scala
(...)
class Foo {
  var a: Int = 1

  def bar() {  
    val foo: Foo = (...)
    // breakpoint here
  }
(...)
```

Expression to evaluate:
```scala
a = 13
this.a = 13
foo.a = 13
```

#### Implicit arguments and conversions

Implicits from companion objects, package objects and public field of enclosing class are supported.

WARNING - expression evaluator knows nothing about imports you have in debugged code, so it cannot
use them.

Debug context:
```scala
case class ImplicitLibClass(val value: Int)

object ImplicitLibClass {
  implicit val defult: ImplicitLibClass = new ImplicitLibClass(1)

  implicit def int2ImplcitLibClass(value: Int) = new ImplicitLibClass(value)
}

object Ala{
  def withImplicitConversion(param: ImplicitLibClass) = param.value
}

```

Expression to evaluate:
```scala
Ala.withImplicitConversion(1)
```

Unsupported features
--------------------

Due to complex nature of Scala programming language and JVM runtime specifics some features
are not yet implemented. Those of them that are known to us are listed below with reasons why
they are not yet implemented.

#### Operations on generic types

Some operations on generic types are currently unsupported due to lack of generic type 
signatures in runtime.

Debug context:
```scala
val list: List[Int] = (...)
```

Expression to evaluate:
```scala
list.filter(_ > 0)
```

#### Assigning new values to method-local variables

Assigning new values to variables that are local to debugged method are currently unsupported.

Debug context:
```scala
(...)
def foo() {
  var x = 12
  // breakpoint here
}
(...)
```

Expression to evaluate:
```scala
x = 13
```

Template for feature
--------------------

#### Feature name

Short feature description.

Debug context:
```scala
code you debug
```

Expression to evaluate:
```scala
code you evaluate
```

/*
 * Copyright 2010 LAMP/EPFL
 * @author Tim Clendenen
 *
 */
package scala.tools.eclipse.wizards

import scala.util.parsing.combinator.JavaTokenParsers

/**
 * Not currently used
 */
object SignatureSupport extends JavaTokenParsers {

  def primitive: Parser[String] =
    ("B" | "C" | "D" | "F" | "I" | "J" | "S" | "V" | "Z") ^^ {
    convertSignature _ }

  def typeSignature: Parser[String] = (
    primitive
    | "T" ~> ident <~ ";" ^^ { i => i }
    | "[" ~> typeSignature ^^ { i => "Array[" + i + "]"}
    | "!" ~> typeSignature
    | unresolvedClassTypeSignature
    | resolvedClassTypeSignature)

  def sepident = ( "." | "/" | "$" ) ~ ident ^^ { case p~i if(p=="/") => "."+i
                                                case p~i => p+i}

  def qualifiedName:Parser[String] = ident ~ rep(sepident) ^^ { case i~l => i+l.mkString }

  def resolvedClassTypeSignature: Parser[String] = (
    //("L" ~> ident ~ optionalTypeArguments ~ qualifiedName ~ optionalTypeArguments <~ ";")  ^^ { case a~b~c~d => a + b.toString + c + d}
      ("L" ~> qualifiedName ~ optionalTypeArguments <~ ";") ^^ { case a~b => a + b }
    | (optionalTypeParameters <~ "L" ~> qualifiedName <~ ";") ^^ { case l => l.mkString(", ") } )

  def unresolvedClassTypeSignature: Parser[String] = (
    //("Q" ~> ident ~ optionalTypeArguments ~ qualifiedName ~ optionalTypeArguments <~ ";") ^^ { case a~b~c~d => a + b.toString + c + d}
    ("Q" ~> parameterizedName ~ parameterizedPer ~ parameterizedArg ~ parameterizedSemi) ^^ { case a~b~c~d => a + "[" + c + "]" }
    | ("Q" ~> qualifiedName ~ optionalTypeArguments <~ ";") ^^ { case a~b => a + b }
    | (optionalTypeParameters <~ "Q" ~> qualifiedName <~ ";") ^^ { case l => l.mkString(", ") })

  def optionalTypeArguments: Parser[String] = opt(
    ("[" ~> rep1(typeArgument) <~ "]")
    | ("<" ~> rep1(typeArgument) <~ ">")) ^^ { case Some(i) => i.mkString("[",",","]")
                                               case None => "" }

  def typeArgument: Parser[String] = (
    typeSignature
    | "*" ^^ { i => "_"}
    | ("+" ~ typeSignature) ^^ { case a~b => a+b }
    | ("-" ~ typeSignature) ^^ { case a~b => a+b } )

  def optionalTypeParameters: Parser[String] = opt(
    ("[" ~> rep1(formalTypeParameterSignature) <~ "]"
    | "<" ~> rep1(formalTypeParameterSignature) <~ ">")) ^^ { case Some(i) => i.mkString("[",",","]")
                                               case None => "" }

  def methodSignature: Parser[String] = (optionalTypeParameters ~
      "(" ~ paramTypeSignature ~ ")" ~ returnTypeSignature) ^^ { case a~"("~b~")"~c => a+"("+b+")"+c.mkString }

  def paramTypeSignature: Parser[String] = rep(typeSignature) ^^ { i => i.mkString(", ") }

  def returnTypeSignature: Parser[String] = typeSignature

  def formalTypeParameterSignature: Parser[String] =
     (typeVariableName ~ optionalClassBound ~ rep(interfaceBound)) ^^ { case a~b~c => a+b+c.mkString }

  def typeVariableName: Parser[String] = ident

  def optionalClassBound: Parser[String] = opt(":" | ":" ~> typeSignature) ^^ { i => i.mkString }

  def interfaceBound: Parser[String] = ":" ~> typeSignature

  def parameterizedName: Parser[String] = """[a-z]*\.[a-z]*\.[A-Za-z]*""".r
  def parameterizedPer: Parser[String] = """\.""".r
  def parameterizedArg: Parser[String] = """[A-Z]""".r
  def parameterizedSemi: Parser[String] = """;""".r

  def parse(text: String, p: Parser[Any] = methodSignature) = parseAll(p, text)

  def convertSignature(s: String): String = {
    val ca = s.toCharArray
      val sig = ca(0) match {
        case 'Z' => "scala.Boolean"
        case 'B' => "scala.Byte"
        case 'C' => "scala.Char"
        case 'S' => "scala.Short"
        case 'I' => "scala.Int"
        case 'J' => "scala.Long"
        case 'F' => "scala.Float"
        case 'D' => "scala.Double"
        case 'V' => "scala.Unit"
      }
      sig
    }

  def main(args: Array[String]): Unit = {
    val r = List(
        "()Qjava.lang.String;",
        "()Qscala.collection.immutable.Set;",
        "()Qscala.Array;",
        "()Qscala.Array;",
        "(Qscala.math.Equiv.T;Qscala.math.Equiv.T;)Z",
        "(Qscala.math.Ordered.A;)I",
        "([Ljava/lang/Object;)[Ljava/lang/Object;",
        "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
        "(Ljava/util/Map;)V",
        "(TK;TV;)TV;",
        "(Ljava.lang.Object;)TV;",
        "()Ljava.util.Set<TK;>;",
        "()Ljava.util.Set<Ljava.util.Map$Entry<TK;TV;>;>;",
        "()V", "()I", "()Z") map (s => parse(s) + "\n")

    println(r)

    val q = List(
        "Ljava.util.Collection<+TE;>;",
        "Ljava.util.Collection<*>;",
        "Ljava.util.Map<+TK;+TV;>;",
        "TE;", "[TT;", "TT;")  map (s => parse(s, typeSignature) + "\n")

    println(q)
    println(parse("Ljava/lang/Object;", resolvedClassTypeSignature))
    println(parse("(JQscala.actors.Replyable.T;)Qscala.math.Ordered.A;"))
  }
}

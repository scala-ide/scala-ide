/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.contribution.weaving.jdt.core;

import org.eclipse.jdt.internal.core.util.BindingKeyParser;

@SuppressWarnings("restriction")
public privileged aspect BindingKeyAspect {
  pointcut nextToken(BindingKeyParser.Scanner scanner) :
    execution(int BindingKeyParser.Scanner.nextToken()) &&
    target(scanner);
  
  int around(BindingKeyParser.Scanner scanner) :
    nextToken(scanner) {
    int previousTokenEnd = scanner.index;
    scanner.start = scanner.index;
    int dollarIndex = -1;
    int length = scanner.source.length;
    while (scanner.index <= length) {
      char currentChar = scanner.index == length ? Character.MIN_VALUE : scanner.source[scanner.index];
      switch (currentChar) {
        case 'B':
        case 'C':
        case 'D':
        case 'F':
        case 'I':
        case 'J':
        case 'N':
        case 'S':
        case 'V':
        case 'Z':
          // base type
          if (scanner.index == previousTokenEnd
              && (scanner.index == 0 || scanner.source[scanner.index-1] != '.')) { // case of field or method starting with one of the character above
            scanner.index++;
            scanner.token = BindingKeyParser.Scanner.BASE_TYPE;
            return scanner.token;
          }
          break;
        case 'L':
        case 'T':
          if (scanner.index == previousTokenEnd
              && (scanner.index == 0 || scanner.source[scanner.index-1] != '.')) { // case of field or method starting with one of the character above
            scanner.start = scanner.index+1;
            dollarIndex = -1;
          }
          break;
        case ';':
          if (scanner.index == previousTokenEnd) {
            scanner.start = scanner.index+1;
            dollarIndex = -1;
            previousTokenEnd = scanner.start;
          } else {
            if (dollarIndex != -1) scanner.index = dollarIndex;
            scanner.token = BindingKeyParser.Scanner.TYPE;
            return scanner.token;
          }
          break;
        case '$':
          if (scanner.index+1 < length && Character.isJavaIdentifierStart(scanner.source[scanner.index+1])) {
            if (scanner.index == previousTokenEnd) {
              scanner.start = scanner.index+1;
              dollarIndex = -1;
            } else {
              if (dollarIndex == -1) {
                dollarIndex = scanner.index;
                break;
              }
              scanner.index = dollarIndex;
              scanner.token = BindingKeyParser.Scanner.TYPE;
              return scanner.token;
            }
          }
          break;
        case '~':
          if (scanner.index == previousTokenEnd) {
            scanner.start = scanner.index+1;
            dollarIndex = -1;
          } else {
            scanner.token = BindingKeyParser.Scanner.TYPE;
            return scanner.token;
          }
          break;
        case '.':
        case '%':
        case ':':
        case '>':
        case '@':
          scanner.start = scanner.index+1;
          dollarIndex = -1;
          previousTokenEnd = scanner.start;
          break;
        case '[':
          while (scanner.index < length && scanner.source[scanner.index] == '[')
            scanner.index++;
          scanner.token = BindingKeyParser.Scanner.ARRAY;
          return scanner.token;
        case '<':
          if (scanner.start > 0) {
            switch (scanner.source[scanner.start-1]) {
              case '.':
                if (scanner.source[scanner.start-2] == '>') {
                  // case of member type where enclosing type is parameterized
                  if (dollarIndex != -1) scanner.index = dollarIndex;
                  scanner.token = BindingKeyParser.Scanner.TYPE;
                } else {
                  scanner.token = BindingKeyParser.Scanner.METHOD;
                }
                return scanner.token;
              default:
                if (scanner.index == previousTokenEnd) {
                  scanner.start = scanner.index+1;
                  dollarIndex = -1;
                  previousTokenEnd = scanner.start;
                } else {
                  if (dollarIndex != -1) scanner.index = dollarIndex;
                  scanner.token = BindingKeyParser.Scanner.TYPE;
                  return scanner.token;
                }
            }
          }
          break;
        case '(':
          scanner.token = BindingKeyParser.Scanner.METHOD;
          return scanner.token;
        case ')':
          if (scanner.token == BindingKeyParser.Scanner.TYPE) {
            scanner.token = BindingKeyParser.Scanner.FIELD;
            return scanner.token;
          }
          scanner.start = scanner.index+1;
          dollarIndex = -1;
          previousTokenEnd = scanner.start;
          break;
        case '#':
          if (scanner.index == previousTokenEnd) {
            scanner.start = scanner.index+1;
            dollarIndex = -1;
            previousTokenEnd = scanner.start;
          } else {
            scanner.token = BindingKeyParser.Scanner.LOCAL_VAR;
            return scanner.token;
          }
          break;
        case Character.MIN_VALUE:
          if (scanner.token == BindingKeyParser.Scanner.START)
            scanner.token = BindingKeyParser.Scanner.PACKAGE;
          else if (scanner.token == BindingKeyParser.Scanner.METHOD || scanner.token == BindingKeyParser.Scanner.LOCAL_VAR)
            scanner.token = BindingKeyParser.Scanner.LOCAL_VAR;
          else if (scanner.token == BindingKeyParser.Scanner.TYPE) {
            if (scanner.index > scanner.start && scanner.source[scanner.start-1] == '.')
              scanner.token = BindingKeyParser.Scanner.FIELD;
            else
              scanner.token = BindingKeyParser.Scanner.END;
          } else if (scanner.token == BindingKeyParser.Scanner.WILDCARD)
            scanner.token = BindingKeyParser.Scanner.TYPE;
          else
            scanner.token = BindingKeyParser.Scanner.END;
          return scanner.token;
        case '*':
        case '+':
        case '-':
          scanner.index++;
          scanner.token = BindingKeyParser.Scanner.WILDCARD;
          return scanner.token;
        case '!':
        case '&':
          scanner.index++;
          scanner.token = BindingKeyParser.Scanner.CAPTURE;
          return scanner.token;
      }
      scanner.index++;
    }
    scanner.token = BindingKeyParser.Scanner.END;
    return scanner.token;
  }
}

/**
 * 
 */
//package scala.tools.eclipse.contribution.weaving.jdt.core;
package org.eclipse.jdt.core.dom;

/**
 * This class is the package org.eclipse.jdt.core.dom, because ASTConverter is not public.
 * 
 * @author Matthew Farwell
 *
 */
@SuppressWarnings("restriction")
public privileged aspect ASTConverterAspect {

  pointcut isPrimitiveType(ASTConverter converter, char[] name) :
    execution(boolean ASTConverter.isPrimitiveType(char[])) &&
    args(name) &&
    target(converter)
    ;

  boolean around(ASTConverter converter, char[] name) :
    isPrimitiveType(converter, name) {
     if (name == null || name.length == 0) {
       return false;
     }
     
       switch(name[0]) {
           case 'i' :
               if (name.length == 3 && name[1] == 'n' && name[2] == 't') {
                   return true;
               }
               return false;
           case 'l' :
               if (name.length == 4 && name[1] == 'o' && name[2] == 'n' && name[3] == 'g') {
                   return true;
               }
               return false;
           case 'd' :
               if (name.length == 6
                    && name[1] == 'o'
                    && name[2] == 'u'
                    && name[3] == 'b'
                    && name[4] == 'l'
                    && name[5] == 'e') {
                   return true;
               }
               return false;
           case 'f' :
               if (name.length == 5
                    && name[1] == 'l'
                    && name[2] == 'o'
                    && name[3] == 'a'
                    && name[4] == 't') {
                   return true;
               }
               return false;
           case 'b' :
               if (name.length == 4
                    && name[1] == 'y'
                    && name[2] == 't'
                    && name[3] == 'e') {
                   return true;
               } else
                   if (name.length == 7
                        && name[1] == 'o'
                        && name[2] == 'o'
                        && name[3] == 'l'
                        && name[4] == 'e'
                        && name[5] == 'a'
                        && name[6] == 'n') {
                   return true;
               }
               return false;
           case 'c' :
               if (name.length == 4
                    && name[1] == 'h'
                    && name[2] == 'a'
                    && name[3] == 'r') {
                   return true;
               }
               return false;
           case 's' :
               if (name.length == 5
                    && name[1] == 'h'
                    && name[2] == 'o'
                    && name[3] == 'r'
                    && name[4] == 't') {
                   return true;
               }
               return false;
           case 'v' :
               if (name.length == 4
                    && name[1] == 'o'
                    && name[2] == 'i'
                    && name[3] == 'd') {
                   return true;
               }
               return false;
       }
       return false;
   }
}

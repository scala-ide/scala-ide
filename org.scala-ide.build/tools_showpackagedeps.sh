#! /bin/sh

java -cp  ${HOME}/.m2/repository/jdepend/jdepend/2.9.1/jdepend-2.9.1.jar \
          jdepend.textui.JDepend \
          org.scala-ide.sdt.editor.text/target/classes

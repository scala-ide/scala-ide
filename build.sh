set -x

mvn -Pscala-2.11.x,eclipse-luna,scala-ide-nightly -Dscala-ide-branch=luna clean verify

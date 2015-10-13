#!/bin/bash

function usage() {
    cat <<EOF

usage:
    set-versions.sh <version>

Delegate to the maven-tycho-plugin to set version of all subprojects. It maintains the POM and
MANIFEST version numbers in sync. This script runs maven on each parent project.

Note:
    Use only numeric versions (do not specify -SNAPSHOT at the end, it is added by this script).

Example:
    set-versions.sh 3.0.3

    This sets the pom version to 3.0.3-SNAPSHOT and the Bundle-Version to 3.0.3.qualifier

EOF
    exit 1
}

# $1 - version number to validate
function validate() {

    if [[ ! $1 =~ [0-9]+\.[0-9]+\.[0-9]+$ ]];
    then
        echo "Invalid version: $1. Versions should have 3 numeric components (i.e. 1.0.1) with no qualifier or suffix."
        exit 1
    fi
}

if [ $# -ne 1 ]; then
    usage
fi

function setVersion() {
    mvn -Dtycho.mode=maven org.eclipse.tycho:tycho-versions-plugin:set-version -DnewVersion=$1-SNAPSHOT
}

validate $1

echo "Setting version to $1"

setVersion $1

(cd org.scala-ide.build-toolchain && setVersion $1)
(cd org.scala-ide.sdt.build && setVersion $1)
(cd org.scala-ide.p2-toolchain && setVersion $1)

cat <<EOF
!!!!
!!!! Some files still need to be updated manually:
!!!!     <parent> tag of pom.xml in org.scala-ide.{sdt.build,build-toolchain,p2-toolchain}
!!!!     */resources/{feature,pom}.xml
!!!!     */resources/META-INF/MANIFEST-*.MF
!!!!
!!!! This can be done with the following command:
!!!!
!!!!   ag "<old-version>" | awk -F ':' '{print $1}' | xargs sed -i '' 's/<old-version>/<new-version>/'
!!!!
!!!! where <old-version> is the version of the previous release and <new-version> the version of the next release.
!!!! ag is a tool similar to grep or ack, it is also called "The Silver Searcher": https://github.com/ggreer/the_silver_searcher
!!!! In constract to grep or ack, ag considers the .gitignore files and therefore makes updating the relevant versions easier.

EOF

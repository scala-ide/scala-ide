#!/bin/sh


# MAVEN needs to point to a MAVEN3 installation:
if which mvn >/dev/null; then
  mvn -version | grep "Maven 3" > /dev/null
  if [ $? -eq 0 ]; then
    MAVEN="mvn"
  fi
fi

if [ "X$MAVEN" = "X" ] ; then
  echo "Missing environment variable \"MAVEN\". This has to point to a maven 3.0 installation, "
  echo "e.g. add the following line to your .bashrc (and make sure the path is correct):"
  echo "export MAVEN=/opt/apache-maven-3.0-beta-1/bin/mvn"
  exit
fi

set_version()
{
  OSGI_VERSION=$(echo $1 | sed s/-SNAPSHOT/.qualifier/)
  ${MAVEN} -f pom.xml org.sonatype.tycho:tycho-versions-plugin:set-version -DnewVersion=$1 -Dscala.version=${SCALA_VERSION}
  # ${MAVEN} -f pom.xml -N versions:set -DnewVersion=$1
  # ${MAVEN} -f pom.xml -N versions:update-child-modules
}

#to find last commit timestamp (author) and short hash made under ${DIR}
#git rev-list --pretty=format:%ad-%h --date=iso -n 1  HEAD -- ${DIR} |tail -n 1
#TODO to use to have the timestamp on nigthly build for each module/eclipse-plugin and not a global one.

timestamp()
{
  UNCOMMITTED_CHANGES=$(git status -s | wc -l)
  if [ $UNCOMMITTED_CHANGES -eq 0 ] ; then
    # timestamp of the last commit
    # in UTC (or in LOCAL time zone ?)
    T=$(git log -1 --format='%ci' | tr -d ': -')
    BACK=$(expr substr $T 1 12)
  else
    BACK=$(date +%Y%m%d%H%M)
  fi
  echo $BACK
#  return BACK
}

current_branch()
{
  git branch --no-color 2> /dev/null | sed -e '/^[^*]/d' -e 's/* \(.*\)/-\1/'
}
hash()
{
  git log -1 --pretty=format:"-%h"
}
milestone()
{
  git log --decorate -1 | grep 'tag:' | sed -e 's/^.*tag: \(M[^,]*\).*$/-\1/'
}
qualifier()
{
  echo $(timestamp)$(current_branch)$(milestone)
}
build()
{
  QUALIFIER=$(qualifier)
  echo QUALIFIER=$QUALIFIER
  ${MAVEN} \
    -U \
    -Dscala.version=${SCALA_VERSION} \
    -DforceContextQualifier=${QUALIFIER} \
    clean install $*
}
MILESTONE=-M1

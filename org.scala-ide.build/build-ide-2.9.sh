#!/bin/sh

. $(dirname $0)/env.sh

SCALA_VERSION=2.9.2-SNAPSHOT

PROFILE="-P scala-2.9.2-SNAPSHOT"

build $*

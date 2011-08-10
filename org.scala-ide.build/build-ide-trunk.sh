#!/bin/sh

. $(dirname $0)/env.sh

SCALA_VERSION=2.10.0-SNAPSHOT
SBT_VERSION=2.10.0-SNAPSHOT

build $*

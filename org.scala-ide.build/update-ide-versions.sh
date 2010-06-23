#!/bin/sh

. $(dirname $0)/env.sh

${MAVEN} \
-N versions:update-child-modules

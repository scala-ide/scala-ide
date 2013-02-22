#!/bin/bash

##############################################################################
# This scripts output a changelog for all changes from <since> to <until> It #
# makes many assumptions on how github works, mainly that all changes are    #
# made through pull requests.                                                #
#                                                                            #
# It adopts something akin to the standard SVN changelog format. The merge  #
# commit log (the pull request message) is used as a content string, and the #
# logs for the branch are grepped to find a mention of something looking     #
# like "fixes #1001234", indicating this commit fixes an Assembla ticket.    #
##############################################################################

DEBUG=""

function usage(){
    echo "Usage : $0 <since> <until>"
}

if [ $# -lt 2 ]; then
    usage
    exit 1
fi

start=$(git rev-parse $1)
end=$(git rev-parse $2)

if [ $DEBUG ]
then
    echo "I'm going to start from $start"
    echo "I'm going to end at $end"
fi

merges=$(git rev-list --merges $end ^$start)

for m in $merges
do
    echo ""
    if [ $DEBUG ]; then
        echo $m
    fi
    # we want the author of the merge branch, not the one of the merge
    # commit
    realAuthorName=$(git log -1 --format='%an' $m^2)
    mergeDate=$(git log -1 --format='%aD' $m)
    echo -e "$mergeDate - $realAuthorName\n"
    echo "$(git log -1 --format='  * %b' $m)"

    # $(git rev-list --parents -n 1 $m) is the set of direct parents of
    # the merge commits (plus the merge commit itself)
    PARENTSET=$(git rev-list --parents -n 1 $m)
    
    # $(git merge-base --octopus $PARENTSET) is the root of the merge
    # --octopus here just in case
    # FIXME: how does this behave if the PR is NOT rebased before merge ?
    BRANCH_ORIGIN=$(git merge-base --octopus $PARENTSET)

    # $m^2 is the head of the merged branch, right before merge
    FINAL_COMMIT="$m^2"
    # $(git log <opts> $BRANCH_ORIGIN..$FINAL_COMMIT) is the log for the branch
    fixes=$(git log --format=%B $BRANCH_ORIGIN..$FINAL_COMMIT|grep -oie "fix[[:alpha:]]*[[:space:]]*\?\#\?100[[:digit:]]\{4\}"|awk '{print $NF}')

    # convert this to a list (there is shorter, but less portable)
    fixesList=$(echo $fixes| sed ':a;{N;s/\n/\, /};ba')

    if [ ! -z "$fixesList" ]
    then
        echo "  * FIXES: $fixesList"
    fi
done









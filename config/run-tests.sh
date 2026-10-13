#!/bin/sh
# Build in the container; the reports mount must not be deleted by Maven clean.
set -eu
find /reports -mindepth 1 -delete
result=0
mvn --batch-mode --no-transfer-progress clean verify || result=$?
if [ -d target ]; then
    cp -a target/. /reports/
fi
exit "$result"

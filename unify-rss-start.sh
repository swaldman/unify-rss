#!/usr/bin/env bash

export PATH=${PATH}:/usr/local/pkg/scala-cli/bin

mkdir -p ./log
scala-cli ./main >> log/unify-rss-interfluidity.log 2>&1 &
echo $! > ./log/PID-unify-rss-interfluidity








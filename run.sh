#!/usr/bin/env bash

docker kill dumbster
docker rm dumbster
docker run -d --init -p 2525:1025 --name=dumbster dumbster
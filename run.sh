#!/usr/bin/env bash

docker kill blackhole_smtp
docker rm blackhole_smtp
docker run -d --init -p 2525:1025 --name=blackhole_smtp blackhole_smtp
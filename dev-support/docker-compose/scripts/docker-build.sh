#!/bin/bash

docker build -t hadoop:2.7.4 -f dev-support/docker-compose/scripts/Dockerfile .
pushd dev-support/docker-compose/kerberos; docker build -t hadoop-krb:2.7.4 -f Dockerfile .; popd;
docker build -t hadoop-nn:2.7.4 -f dev-support/docker-compose/scripts/Dockerfile-nn .
docker build -t hadoop-dn:2.7.4 -f dev-support/docker-compose/scripts/Dockerfile-dn .
docker build -t hadoop-rm:2.7.4 -f dev-support/docker-compose/scripts/Dockerfile-rm .
docker build -t hadoop-nm:2.7.4 -f dev-support/docker-compose/scripts/Dockerfile-nm .
docker build -t hadoop-httpfs:2.7.4 -f dev-support/docker-compose/scripts/Dockerfile-httpfs .
docker build -t hadoop-hive:2.7.4 -f dev-support/docker-compose/scripts/Dockerfile-hive .
# docker build -t hadoop-spark:2.7.4 -f dev-support/docker-compose/scripts/Dockerfile-spark .

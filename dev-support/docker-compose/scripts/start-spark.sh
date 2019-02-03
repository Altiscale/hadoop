#!/bin/bash

sleep 5
# jupyter notebook --port 8008 --ip spark.hadoop --allow-root -y &

kinit -kt /var/lib/krb5kdc/root.keytab root/spark.hadoop@HADOOP
tail -f /dev/null

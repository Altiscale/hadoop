export JAVA_HOME=$(readlink -f /usr/bin/java | sed "s:/jre/bin/java::")
export MAVEN_OPTS="-Xms256m -Xmx512m"
export HADOOP_PROTOC_PATH=/opt/protobuf-2.5.0/bin/protoc
export PATH=$PATH:/opt/apache-maven/bin
export FINDBUGS_HOME=/opt/findbugs

#!/bin/bash -l

# find this script and establish base directory
SCRIPT_DIR="$( dirname "${BASH_SOURCE[0]}" )"
cd "$SCRIPT_DIR" &> /dev/null
MY_DIR="$(pwd)"
echo "[INFO] Executing in ${MY_DIR}"

# PATH does not contain mvn and protobuf in this login shell
export M2_HOME=/opt/mvn3
export JAVA_HOME=/opt/sapjvm_7
export PATH=$M2_HOME/bin:$JAVA_HOME/bin:$PATH
export PATH=/opt/protobuf/bin:$PATH
export FINDBUGS_HOME=/opt/findbugs
export HADOOP_PROTOC_PATH=/opt/protobuf/bin/protoc

cd $MY_DIR

mkdir -p $FINDBUGS_HOME/src/xsl
unzip /imports/findbugs-*.jar "*.xsl" -d $FINDBUGS_HOME/src/xsl/


ls -l $FINDBUGS_HOME/src/xsl/

#------------------------------------------------------------------------------
#
#  ***** compile and package hadoop *****
#
#------------------------------------------------------------------------------

HADOOP_VERSION="${HADOOP_VERSION:-2.7.7}"
ARTIFACT_VERSION="${HADOOP_VERSION}"
export MAVEN_OPTS="-Xms256m -Xmx512m"

mvn versions:set -DnewVersion=${ARTIFACT_VERSION}
if [ "$RUN_UNIT_TESTS" == "true" ]; then
  mvn -Pdist,docs,src,native --fail-never -Dtar -Dbundle.snappy  -Dsnappy.lib=/usr/lib64 -Drequire.fuse=true -Drequire.snappy -Dcontainer-executor.conf.dir=/etc/hadoop clean install
else
  mvn -Pdist,docs,src,native -Dtar -DskipTests -Dbundle.snappy -Dsnappy.lib=/usr/lib64 -Drequire.fuse=true -Drequire.snappy -Dcontainer-executor.conf.dir=/etc/hadoop clean install -X
fi


if [[ "$?" -ne 0 ]] ; then
  echo 'Error compiling and packaging tez'; exit 1
fi

#------------------------------------------------------------------------------
#
#  ***** setup the environment for generating HADOOP RPM via fpm *****
#
#------------------------------------------------------------------------------


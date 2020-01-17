#!/bin/bash -l

# find this script and establish base directory
SCRIPT_DIR="$( dirname "${BASH_SOURCE[0]}" )"
cd "$SCRIPT_DIR" &> /dev/null || exit
MY_DIR="$(pwd)"
echo "[INFO] Executing in ${MY_DIR}"

# PATH does not contain mvn and protobuf in this login shell
export M2_HOME=/opt/mvn3
export JAVA_HOME=/opt/sapjvm_7
export PATH=$M2_HOME/bin:$JAVA_HOME/bin:$PATH
export PATH=/opt/protobuf/bin:$PATH
export FINDBUGS_HOME=/opt/findbugs
export HADOOP_PROTOC_PATH=/opt/protobuf/bin/protoc
cd "$MY_DIR" || exit


# Importing the findbugs jar from nexus and extracting the xsl files
# Cannot download the tar.gz from the internet
mkdir -p $FINDBUGS_HOME/src/xsl
unzip /imports/findbugs-*.jar "*.xsl" -d $FINDBUGS_HOME/src/xsl/

#------------------------------------------------------------------------------
#
#  ***** compile and package hadoop *****
#
#------------------------------------------------------------------------------

HADOOP_VERSION="${HADOOP_VERSION:-2.7.7}"
ARTIFACT_VERSION="${HADOOP_VERSION}"
export MAVEN_OPTS="-Xms256m -Xmx512m"

# pointing the build to download tomcat from nexus repo instead of the internet
# need to also change the pom explicitly to unzip the file since no tar.gz file is available in nexus
export TOMCAT_VERSION=6.0.48
export TOMCAT_DOWNLOAD_URL=http://nexus.wdf.sap.corp:8081/nexus/content/groups/build.milestones/org/apache/tomcat/tomcat/${TOMCAT_VERSION}/tomcat-${TOMCAT_VERSION}.tar.gz

mvn versions:set -DnewVersion=${ARTIFACT_VERSION}

if [ "$RUN_UNIT_TESTS" == "true" ]; then
  mvn -Pdist,docs,src,native --fail-never -Dtar -Dbundle.snappy  -Dsnappy.lib=/usr/lib64 -Drequire.fuse=true -Drequire.snappy -Dcontainer-executor.conf.dir=/etc/hadoop -Dtomcat.download.url=${TOMCAT_DOWNLOAD_URL} -Dtomcat.version=${TOMCAT_VERSION} clean install
else
  mvn -Pdist,docs,src,native -Dtar -DskipTests -Dbundle.snappy -Dsnappy.lib=/usr/lib64 -Drequire.fuse=true -Drequire.snappy -Dcontainer-executor.conf.dir=/etc/hadoop -Dtomcat.download.url=${TOMCAT_DOWNLOAD_URL} -Dtomcat.version=${TOMCAT_VERSION} clean install
fi


if [[ "$?" -ne 0 ]] ; then
  echo 'Error compiling and packaging hadoop'; exit 1
fi

#------------------------------------------------------------------------------------
#
#  ***** creating /opt/ and /etc/ directories and copying over the config files *****
#
#------------------------------------------------------------------------------------

# create the installation directory (to stage artifacts)
INSTALL_DIR="$MY_DIR/hadooprpmbuild"
mkdir --mode=0755 -p "${INSTALL_DIR}"

OPT_DIR="${INSTALL_DIR}"/opt
mkdir --mode=0755 -p "${OPT_DIR}"
cd "${OPT_DIR}" || exit

tar -xvzpf "${MY_DIR}"/hadoop-dist/target/hadoop-"${ARTIFACT_VERSION}".tar.gz
chmod 755 "${OPT_DIR}"/hadoop-"${ARTIFACT_VERSION}"

# https://verticloud.atlassian.net/browse/OPS-731
# create /etc/hadoop, in a future version of the build we may move the config there directly
ETC_DIR="${INSTALL_DIR}"/etc/hadoop-"${ARTIFACT_VERSION}"
mkdir --mode=0755 -p "${ETC_DIR}"

# move the config directory to /etc
cp -rp "${OPT_DIR}"/hadoop-"${ARTIFACT_VERSION}"/etc/hadoop/* "${ETC_DIR}"
mv "${OPT_DIR}"/hadoop-"${ARTIFACT_VERSION}"/etc/hadoop "${OPT_DIR}"/hadoop-"${ARTIFACT_VERSION}"/etc/hadoop-templates

# Add init.d scripts and sysconfig
mkdir --mode=0755 -p "${INSTALL_DIR}"/etc/rc.d/init.d
cp "${MY_DIR}"/sap-xmake-etc/init.d/* "${INSTALL_DIR}"/etc/rc.d/init.d
mkdir --mode=0755 -p "${INSTALL_DIR}"/etc/sysconfig
cp "${MY_DIR}"/sap-xmake-etc/sysconfig/* "${INSTALL_DIR}"/etc/sysconfig

#------------------------------------------------------------------------------------
#
#  ***** interleaving the imported hadoop-lzo artifacts *****
#
#------------------------------------------------------------------------------------

cd "${INSTALL_DIR}" || exit
for i in share/hadoop/httpfs/tomcat/webapps/webhdfs/WEB-INF/lib share/hadoop/mapreduce/lib share/hadoop/yarn/lib share/hadoop/common/lib; do
  cp -rp /imports/hadoop-lzo-[0-9]*.[0-9]*.[0-9]*.jar "${OPT_DIR}"/hadoop-"${ARTIFACT_VERSION}"/$i
done

# extracting the libgplcompression libraries and interleaving with hadoop
TMP_DIR="${INSTALL_DIR}"/libgplcompression_tmp/
mkdir --mode=0755 -p "${TMP_DIR}"
tar -xvzpf /imports/hadoop-lzo-libgplcompression-[0-9]*.[0-9]*.[0-9]*.tar.gz -C "${TMP_DIR}"

cp -P "${TMP_DIR}"/lib/libgplcompression.* "${OPT_DIR}"/hadoop-"${ARTIFACT_VERSION}"/lib/native/

# Fix all permissions
chmod 755 "${INSTALL_DIR}"/opt/hadoop-"${ARTIFACT_VERSION}"/sbin/*.sh
chmod 755 "${INSTALL_DIR}"/opt/hadoop-"${ARTIFACT_VERSION}"/sbin/*.cmd

# All config files:
export CONFIG_FILES="--config-files /etc/hadoop-${ARTIFACT_VERSION} \
  --config-files /etc/sysconfig "

#------------------------------------------------------------------------------------
#
#  ***** generating HADOOP RPM via fpm *****
#
#------------------------------------------------------------------------------------

ALTISCALE_RELEASE="${ALTISCALE_RELEASE:-5.0.0}"
GIT_REPO="https://github.com/Altiscale/hadoop"
DATE_STRING=$(date +%Y%m%d%H%M)
export RPM_NAME=$(echo alti-hadoop-"${ARTIFACT_VERSION}")
export RPM_DESCRIPTION="Apache Hadoop ${ARTIFACT_VERSION}\n\n${DESCRIPTION}"
export RPM_DIR="${RPM_DIR:-"${INSTALL_DIR}/hadoop-artifact"}"
mkdir --mode=0755 -p "${RPM_DIR}"

cd ${RPM_DIR}

fpm --verbose \
--maintainer support@altiscale.com \
--vendor Altiscale \
--provides ${RPM_NAME} \
--provides "libhdfs.so.0.0.0()(64bit)" \
--provides "libhdfs(x86-64)" \
--provides libhdfs \
--replaces alti-hadoop \
--depends 'lzo > 2.0' \
--url ${GIT_REPO} \
--license "Apache License v2" \
-s dir \
-t rpm \
-n ${RPM_NAME}  \
-v ${ALTISCALE_RELEASE} \
--iteration ${DATE_STRING} \
--description "$(printf "${RPM_DESCRIPTION}")" \
${CONFIG_FILES} \
--rpm-attr 644,root,root:/etc/sysconfig/hadoop_journalnode \
--rpm-attr 644,root,root:/etc/sysconfig/hadoop_datanode \
--rpm-attr 644,root,root:/etc/sysconfig/hadoop_historyserver \
--rpm-attr 644,root,root:/etc/sysconfig/hadoop_namenode \
--rpm-attr 644,root,root:/etc/sysconfig/hadoop_nodemanager \
--rpm-attr 644,root,root:/etc/sysconfig/hadoop_resourcemanager \
--rpm-attr 644,root,root:/etc/sysconfig/hadoop_secondarynamenode \
--rpm-attr 644,root,root:/etc/sysconfig/hadoop_timelineserver \
--rpm-attr 755,root,root:/etc/rc.d/init.d/hadoop_datanode \
--rpm-attr 755,root,root:/etc/rc.d/init.d/hadoop_historyserver \
--rpm-attr 755,root,root:/etc/rc.d/init.d/hadoop_httpfs \
--rpm-attr 755,root,root:/etc/rc.d/init.d/hadoop_journalnode \
--rpm-attr 755,root,root:/etc/rc.d/init.d/hadoop_namenode \
--rpm-attr 755,root,root:/etc/rc.d/init.d/hadoop_nodemanager \
--rpm-attr 755,root,root:/etc/rc.d/init.d/hadoop_resourcemanager \
--rpm-attr 755,root,root:/etc/rc.d/init.d/hadoop_secondarynamenode \
--rpm-attr 755,root,root:/etc/rc.d/init.d/hadoop_timelineserver \
--rpm-user hadoop \
--rpm-group hadoop \
-C ${INSTALL_DIR} \
opt etc

mv "${RPM_DIR}"/"${RPM_NAME}"-"${ALTISCALE_RELEASE}"-"${DATE_STRING}".x86_64.rpm "${RPM_DIR}"/alti-hadoop-"${XMAKE_PROJECT_VERSION}".rpm

exit 0

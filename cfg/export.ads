artifacts builderVersion: "1.1", {

  group "com.sap.bds.ats-altiscale", {

    artifact "hadoop", {
      file "${gendir}/src/hadooprpmbuild/hadoop-artifact/alti-hadoop-${buildVersion}.rpm"
    }
  }
}

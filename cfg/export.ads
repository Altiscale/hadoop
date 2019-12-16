Properties properties = new Properties()
File propertiesFile = new File("$gendir/src/hadoop-lzo-artifacts/build.properties")
propertiesFile.withInputStream {
    properties.load(it)
}

artifacts builderVersion: "1.1", {

  group "com.sap.bds.ats-altiscale", {

    artifact "hadoop-lzo", {
      file "$gendir/src/hadoop-lzo-artifacts/hadoop-lzo-${properties.ARTIFACT_VERSION}.jar"
    }
    artifact "hadoop-lzo-libgplcompression", {
      file "$gendir/src/hadoop-lzo-artifacts/hadoop-lzo-libgplcompression-${properties.ARTIFACT_VERSION}.tar.gz"
    }
  }
}

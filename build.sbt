val nexus = "https://oss.sonatype.org/"
val nexusSnapshots = nexus + "content/repositories/snapshots";
val nexusReleases = nexus + "service/local/staging/deploy/maven2";

ThisBuild / organization := "com.mchange"
ThisBuild / version := "0.0.1-SNAPSHOT"
ThisBuild / resolvers += ("releases" at nexusReleases)
ThisBuild / resolvers += ("snapshots" at nexusSnapshots)

import com.mchange.sc.v1.unrevokedsigned.DataStore

lazy val root = (project in file(".")).settings (
  name := "unrevoked-signed-client",
  dataStore := new DataStore.JsonFileBased( baseDirectory.value / "data-store" ),
  ethcfgNodeChainId := 3,
  unrevokedSignedContractAddress := "0x83beeb43ac63db9e4828fe6177f9024c94398f01"
)



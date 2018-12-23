val nexus = "https://oss.sonatype.org/"
val nexusSnapshots = nexus + "content/repositories/snapshots";
val nexusReleases = nexus + "service/local/staging/deploy/maven2";

ThisBuild / organization := "com.mchange"
ThisBuild / version := "0.0.1-SNAPSHOT"
ThisBuild / resolvers += ("releases" at nexusReleases)
ThisBuild / resolvers += ("snapshots" at nexusSnapshots)

lazy val root = (project in file(".")).settings (
  name := "unrevoked-signed-client",
  fileBasedDataStoreDataDir := baseDirectory.value / "data-store",
  dataStore := new DataStore.JsonFileBased( fileBasedDataStoreDataDir.value ),
  libraryDependencies += "com.mchange" %% "unrevoked-signed" % "0.0.1-SNAPSHOT" changing()
)

/*
 *  Custom keys and tasks below
 */ 

import com.mchange.sc.v1.consuela._ // for toImmutableSeq, we have it in the build classpath via the sbt-ethereum plugin
import com.mchange.sc.v1.consuela.ethereum.EthHash
import sbt.complete.DefaultParsers._

val fileBasedDataStoreDataDir = settingKey[File]("Directory in which profile and document data/metadata should be stored.")

val dataStore = settingKey[DataStore]("Implementation of DataStore trait (see in directory 'project/') in which files should be stored.")

val createPlaintextProfile = inputKey[EthHash]("Creates a profile for the current sbt-ethereum sender from a given file path, as 'text/plain']")
val createJsonProfile      = inputKey[EthHash]("Creates a profile for the current sbt-ethereum sender from a given file path, as 'application/json', normalizing to no-space text")
val createJpegProfile      = inputKey[EthHash]("Creates a profile for the current sbt-ethereum sender from a given file path, as 'image/jpeg'" )
val createPngProfile       = inputKey[EthHash]("Creates a profile for the current sbt-ethereum sender from a given file path, as 'image/png'" )

val storeSignPlaintextDocument = inputKey[EthHash]("Creates a document marked signed by the current sbt-ethereum sender from a given file path, as 'text/plain']")
val storeSignJsonDocument      = inputKey[EthHash]("Creates a document marked signed by the current sbt-ethereum sender from a given file path, as 'application/json', normalizing to no-space text")
val storeSignJpegDocument      = inputKey[EthHash]("Creates a document marked signed by the current sbt-ethereum sender from a given file path, as 'image/jpeg'" )
val storeSignPngDocument       = inputKey[EthHash]("Creates a document marked signed by the current sbt-ethereum sender from a given file path, as 'image/png'" )

/*

def createProfile( contentType : String ) : Initialize.InputTask[EthHash] = Def.inputTask {
  val store = dataStore.value

  implicit val ( sctx, ssender ) = xethStubEnvironment.value

  val filePath = token( NotSpace ).examples("<file-path-to-profile>").parsed
  val profileBytes = java.nio.file.Files.readAllBytesPaths.get(filePath).toImmutableSeq
  val stub = UnrevokedSigned


}

*/ 

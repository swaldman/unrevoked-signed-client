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
  Compile / createPlaintextProfile := { createProfile( "text/plain" )( Compile ).evaluated },
  Compile / createJsonProfile := { createProfile( "application/json" )( Compile ).evaluated },
  Compile / createJpegProfile := { createProfile( "image/jpeg" )( Compile ).evaluated },
  Compile / createPngProfile := { createProfile( "image/png" )( Compile ).evaluated },
  Compile / storeSignPlaintextDocument := { storeSignDocument( "text/plain" )( Compile ).evaluated },
  Compile / storeSignJsonDocument := { storeSignDocument( "application/json" )( Compile ).evaluated },
  Compile / storeSignJpegDocument := { storeSignDocument( "image/jpeg" )( Compile ).evaluated },
  Compile / storeSignPngDocument := { storeSignDocument( "image/png" )( Compile ).evaluated }
)

/*
 *  Custom keys and tasks below
 */ 

import sbt.Def.Initialize
import com.mchange.sc.v1.consuela._ // for toImmutableSeq, we have it in the build classpath via the sbt-ethereum plugin
import com.mchange.sc.v1.consuela.ethereum.{EthAddress,EthHash}
import com.mchange.sc.v1.consuela.ethereum.stub
import com.mchange.sc.v1.consuela.ethereum.stub.sol
import com.mchange.sc.v1.sbtethereum.lib._
import com.mchange.sc.v1.sbtethereum.lib.Parsers._
import sbt.complete.DefaultParsers._

import com.mchange.sc.v1.unrevokedsigned.contract._

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

def createProfile( contentType : String )( config : Configuration ) : Initialize[InputTask[EthHash]] = {
  val parserGen = parserGeneratorForAddress( "<unrevoked-signed-contract-address>" ) { addressParser =>
    addressParser.flatMap( addr => (token(Space.+) ~> token( NotSpace ).examples("<file-path-to-profile>")).map( path => ( addr, path ) ) )
  }
  val parser = Defaults.loadForParser( config / xethFindCacheRichParserInfo )( parserGen )

  Def.inputTask {
    val log = streams.value.log
    val store = dataStore.value

    implicit val ( sctx, ssender ) = (config / xethStubEnvironment).value

    val (contractAddress, filePath) = parser.parsed
    val profileBytes = {
      import java.nio.file._
      Files.readAllBytes( Paths.get(filePath) ).toImmutableSeq
    }
    val stub = new UnrevokedSigned( contractAddress )

    val hash = store.put( contentType, profileBytes ).assert
    stub.transaction.createIdentityForSender( sol.Bytes32( hash.bytes ) )

    log.warn( s"The document at path '${filePath}' has been stored, and defined as the profile for sender address '0x${ssender.address}' on contract at '0x${contractAddress}'." )
    hash
  }
}

def storeSignDocument( contentType : String )( config : Configuration ) : Initialize[InputTask[EthHash]] = {
  val parserGen = parserGeneratorForAddress( "<unrevoked-signed-contract-address>" ) { addressParser =>
    addressParser.flatMap( addr => (token(Space.+) ~> token( NotSpace ).examples("<file-path-to-document>")).map( path => ( addr, path ) ) )
  }
  val parser = Defaults.loadForParser( config / xethFindCacheRichParserInfo )( parserGen )

  Def.inputTask {
    val log = streams.value.log
    val store = dataStore.value

    implicit val ( sctx, ssender ) = ( config / xethStubEnvironment ).value

    val (contractAddress, filePath) = parser.parsed
    val documentBytes = {
      import java.nio.file._
      Files.readAllBytes( Paths.get(filePath) ).toImmutableSeq
    }
    val stub = new UnrevokedSigned( contractAddress )

    val hash = store.put( contentType, documentBytes ).assert
    stub.transaction.markSigned( sol.Bytes32( hash.bytes ) )

    log.warn( s"The document at path '${filePath}' has been stored, and is marked signed for sender address '0x${ssender.address}' on contract at '0x${contractAddress}'." )
    hash
  }
}



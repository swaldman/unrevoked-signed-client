val nexus = "https://oss.sonatype.org/"
val nexusSnapshots = nexus + "content/repositories/snapshots";
val nexusReleases = nexus + "service/local/staging/deploy/maven2";

val akkaHttpVersion = "10.1.6"
val akkaVersion     = "2.5.19"
val failableVersion = "0.0.2-SNAPSHOT"

ThisBuild / organization := "com.mchange"
ThisBuild / version := "0.0.1-SNAPSHOT"
ThisBuild / resolvers += ("releases" at nexusReleases)
ThisBuild / resolvers += ("snapshots" at nexusSnapshots)

import com.mchange.sc.v1.unrevokedsigned.DataStore

lazy val root = (project in file(".")).settings (
  name := "unrevoked-signed-client",
  dataStore := {
    val local = Option( sys.props( "unrevokedsigned.local" ) ).map( _.toBoolean ).getOrElse( false )

    if ( local ) {
      new DataStore.JsonFileBased( unrevokedSignedJsonDataDir.value )
    }
    else {
      new HttpProxyDataStore( unrevokedSignedHttpClientBaseUrl.value )
    }
  },
  ethcfgNodeChainId := 3,
  unrevokedSignedContractAddress := "0xa240691bb281131ebca3caaffec4a504c66b5849",
  unrevokedSignedHttpServerInterface := "localhost",
  unrevokedSignedHttpServerPort := 8998,
  unrevokedSignedHttpClientBaseUrl := "http://unrevokedsigned.mchange.com/",
  unrevokedSignedJsonDataDir := baseDirectory.value / "data-store",
  libraryDependencies ++= Seq(
    "com.mchange"       %% "consuela"             % "0.0.11-SNAPSHOT" changing(),
    "com.mchange"       %% "failable"             % failableVersion,
    "com.mchange"       %% "failable-logging"     % failableVersion,
    "com.mchange"       %% "unrevoked-signed"     % "0.0.1-SNAPSHOT",
    "com.typesafe.akka" %% "akka-http"            % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-xml"        % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-stream"          % akkaVersion
  ),
  Compile / resourceGenerators += Def.task {
    val baseDir = (Compile / resourceManaged).value
    val dataDir = unrevokedSignedJsonDataDir.value
    val contractAddress = (Compile / unrevokedSignedContractAddress).value
    val chainId = ethcfgNodeChainId.value
    val nodeUrl = (Compile / ethNodeUrl).value
    val iface = unrevokedSignedHttpServerInterface.value
    val port = unrevokedSignedHttpServerPort.value
    val clientBaseUrl = unrevokedSignedHttpClientBaseUrl.value

    val out = makeConfigFile( baseDir, dataDir, contractAddress, chainId, nodeUrl, iface, port, clientBaseUrl )
    println( s"File: ${out}" )
    out :: Nil
  }.taskValue
)

val unrevokedSignedHttpServerInterface = settingKey[String]("The interface on which the unrevoked-signed http server runs.")
val unrevokedSignedHttpServerPort      = settingKey[Int]   ("The port on which the unrevoked-signed http server runs.")
val unrevokedSignedHttpClientBaseUrl   = settingKey[String]("The URL at which clients find the HTTP service.")
val unrevokedSignedJsonDataDir         = settingKey[File]  ("The directory in which JSON-formatted file data should be stored, if it is to be stored locally.")

import com.mchange.sc.v2.io.RichFile

def makeConfigFile( baseDir : File, dataDir : File, contractAddress : String, chainId : Int, nodeUrl : String, iface : String, port : Int, clientBaseUrl : String ) : File = {
  val contents = {
    s"""|unrevokedsigned.eth.contractAddress=${contractAddress}
        |unrevokedsigned.eth.chainId=${chainId}
        |unrevokedsigned.eth.nodeUrl=${nodeUrl}
        |unrevokedsigned.data.store.json.data.dir=${dataDir.getPath}
        |unrevokedsigned.http.server.interface=${iface}
        |unrevokedsigned.http.server.port=${port}
        |unrevokedsigned.http.client.url=${clientBaseUrl}
        |""".stripMargin
  }

  baseDir.mkdirs()
  val out = new File( baseDir, "unrevokedsigned.properties" )
  out.replaceContents( contents )
  out
}



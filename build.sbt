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
  unrevokedSignedContractAddress := "0x83beeb43ac63db9e4828fe6177f9024c94398f01",
  unrevokedSignedHttpServerInterface := "localhost",
  unrevokedSignedHttpServerPort := 8998,
  unrevokedSignedHttpClientBaseUrl := "http://unrevokedsigned.mchange.com/",
  Compile / resourceGenerators += Def.task {
    val baseDir = (Compile / resourceManaged).value
    val contractAddress = (Compile / unrevokedSignedContractAddress).value
    val chainId = ethcfgNodeChainId.value
    val nodeUrl = (Compile / ethNodeUrl).value
    val iface = unrevokedSignedHttpServerInterface.value
    val port = unrevokedSignedHttpServerPort.value
    val clientBaseUrl = unrevokedSignedHttpClientBaseUrl.value

    makeConfigFile( baseDir, contractAddress, chainId, nodeUrl, iface, port, clientBaseUrl ) :: Nil
  }.taskValue
)

val unrevokedSignedHttpServerInterface = settingKey[String]("The interface on which the unrevoked-signed http server runs.")
val unrevokedSignedHttpServerPort      = settingKey[Int]("The port on which the unrevoked-signed http server runs.")
val unrevokedSignedHttpClientBaseUrl   = settingKey[String]("The URL at which clients find the HTTP service.")

import com.mchange.sc.v2.io.RichFile

def makeConfigFile( baseDir : File, contractAddress : String, chainId : Int, nodeUrl : String, iface : String, port : Int, clientBaseUrl : String ) : File = {
  val contents = {
    s"""|unrevokedsigned.eth.contractAddress=${contractAddress}
        |unrevokedsigned.eth.chainId=${chainId}
        |unrevokedsigned.eth.nodeUrl=${nodeUrl}
        |unrevokedsigned.http.server.interface=${iface}
        |unrevokedsigned.http.server.port=${port}
        |unrevokedsigned.http.client.url=${clientBaseUrl}
        |""".stripMargin
  }

  val out = new File( baseDir, "unrevokedsigned.properties" )
  out.replaceContents( contents )
  out
}



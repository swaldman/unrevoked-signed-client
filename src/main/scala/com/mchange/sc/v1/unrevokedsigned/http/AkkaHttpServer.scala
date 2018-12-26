package com.mchange.sc.v1.unrevokedsigned.http

import com.mchange.sc.v1.consuela._

import com.mchange.sc.v1.unrevokedsigned.DataStore

import com.mchange.sc.v1.log.MLevel._

import scala.collection._

import scala.concurrent.Await
import scala.concurrent.duration._

import akka.actor.{ ActorRef, ActorSystem }
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route

import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.{ContentType, HttpEntity, StatusCodes }
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.MethodDirectives.get
import akka.http.scaladsl.server.directives.MethodDirectives.put
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import akka.http.scaladsl.server.directives.PathDirectives.path

import com.mchange.sc.v1.consuela.ethereum.{ stub, EthAddress, EthChainId, EthHash }
import com.mchange.sc.v1.consuela.ethereum.stub.sol

import com.mchange.sc.v1.unrevokedsigned.contract.UnrevokedSigned

import akka.pattern.ask
import akka.util.{ByteString,Timeout}

object AkkaHttpServer {

  implicit val system       : ActorSystem       = ActorSystem("UnrevokedSignedAkkaHttp")
  implicit val materializer : ActorMaterializer = ActorMaterializer()

  implicit lazy val timeout = Timeout(5.seconds)

  lazy val unrevokedSignedHttpActor : ActorRef = system.actorOf(UnrevokedSignedHttpActor.props, "unrevokedSignedHttpActor")

  val contractAddress = EthAddress( ConfigProps.getProperty( "unrevokedsigned.eth.contractAddress" ) )

  val nodeUrl = ConfigProps.getProperty( "unrevokedsigned.eth.nodeUrl" )
  val chainId = ConfigProps.getProperty( "unrevokedsigned.eth.chainId" ).toInt

  lazy val us : UnrevokedSigned = UnrevokedSigned.build(
    jsonRpcUrl = nodeUrl,
    contractAddress = contractAddress,
    chainId = Some( EthChainId( chainId ) )
  )

  implicit lazy val ssender = stub.Sender.Default

  lazy val routes : Route = concat(
    pathPrefix("data-store") {
      concat (
        pathPrefix("put") {
          pathEnd {
            put {
              entity(as[Array[Byte]]) { bytes =>
                extractRequestContext { ctx =>
                  val contentType = ctx.request.entity.contentType.toString
                  val f_bool = (unrevokedSignedHttpActor ? UnrevokedSignedHttpActor.Put( contentType, bytes.toImmutableSeq )).mapTo[Boolean]
                  onSuccess( f_bool ) { ok =>
                    complete( if (ok) StatusCodes.Created else StatusCodes.InternalServerError )
                  }
                }
              }
            }
          }
        },
        pathPrefix("get") {
          path(Segment) { hex =>
            pathEnd {
              val f_getResponse = (unrevokedSignedHttpActor ? UnrevokedSignedHttpActor.Get( EthHash.withBytes( hex.decodeHex ) ) ).mapTo[UnrevokedSignedHttpActor.GetResponse]
              onSuccess( f_getResponse ) {
                case UnrevokedSignedHttpActor.GetResponse.Success( contentType, data ) => {
                  ContentType.parse( contentType ) match {
                    case Right( ct ) => complete( HttpEntity( ct, data.toArray ) )
                    case Left( error ) => {
                      WARNING.log( s"Couldn't parse content type! ${error}" )
                      complete( StatusCodes.InternalServerError )
                    }
                  }
                }
                case UnrevokedSignedHttpActor.GetResponse.NotFound => {
                  complete( StatusCodes.NotFound )
                }
                case UnrevokedSignedHttpActor.GetResponse.Failed( message ) => {
                  complete( StatusCodes.InternalServerError )
                }
              }
            }
          }
        },
        pathPrefix("contains") {
          path(Segment) { hex =>
            pathEnd {
              val f_containsResponse = (unrevokedSignedHttpActor ? UnrevokedSignedHttpActor.Contains( EthHash.withBytes( hex.decodeHex ) ) ).mapTo[UnrevokedSignedHttpActor.ContainsResponse]
              onSuccess( f_containsResponse ) {
                case UnrevokedSignedHttpActor.ContainsResponse.Success( found )  => complete( if (found) StatusCodes.OK else StatusCodes.NotFound )
                case UnrevokedSignedHttpActor.ContainsResponse.Failed( message ) => complete( StatusCodes.InternalServerError )
              }
            }
          }
        }
      )
    },
    pathPrefix("find-signers") {
      concat (
        get {
          path(Segment) { hex =>
            pathEnd {
              complete( findSigners( hex.decodeHexAsSeq ) )
            }
          }
        },
        post {
          fileUpload("document") {
            case (fileInfo, fileStream ) => {
              val f_bytestring = fileStream.runWith( Sink.reduce( (a : ByteString, b : ByteString) => a ++ b ) )
              onSuccess( f_bytestring ) { bytestring =>
                complete( findSigners( EthHash.hash(bytestring.compact.toArray.toImmutableSeq).bytes ) )
              }
            }
          }
        },
        put {
          entity(as[Array[Byte]]) { bytes =>
            complete( findSigners( EthHash.hash(bytes).bytes ) )
          }
        }
      )
    },
    pathPrefix("find-profile") {
      path(Segment) { hex =>
        pathEnd {
          val signerAddr = EthAddress( hex )
          val solHash = us.constant.profileHashForSigner( signerAddr )
          complete( Profile( solHash.widen.hex ) )
        }
      }
    },
    pathPrefix("metadata") {
      complete( Metadata( chainId, contractAddress.hex ) )
    },
    pathPrefix("assets") {
      path( RemainingPath ) { path =>
        getFromResource( s"assets/${path}" )
      }
    }
  )

  private def findSigners( bytes : immutable.Seq[Byte] ) = {
    val docHash = sol.Bytes32( bytes )
    val len = us.constant.countSigners( docHash ).widen.toInt
    val tups = ( 0 until len ).map( i => us.constant.fetchSigner( docHash, sol.UInt256(i) ) )
    val signersList = {
      tups
        .filter { case ( signerAddr, valid, profileHash ) => valid }
        .map { case ( signerAddr, valid, profileHash ) => Signer( signerAddr.hex, profileHash.widen.hex ) }
        .toList
    }
    Signers( signersList )
  }

  def main( argv : Array[String] ) : Unit = {
    val port      = ConfigProps.getProperty( "unrevokedsigned.http.server.port" ).toInt
    val iface     = ConfigProps.getProperty( "unrevokedsigned.http.server.interface" )
    val clientUrl = ConfigProps.getProperty( "unrevokedsigned.http.client.url" )

    Http().bindAndHandle(routes, iface, port)

    println(s"Server online at 'http://${iface}:${port}/', should be exported to client URL '${clientUrl}'")

    Await.result(system.whenTerminated, Duration.Inf)
  }
}

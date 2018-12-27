package com.mchange.sc.v1.unrevokedsigned.http

import com.mchange.sc.v1.consuela._

import com.mchange.sc.v1.unrevokedsigned.{DataStore,Normalizer}

import com.mchange.sc.v1.log.MLevel._

import scala.collection._

import scala.concurrent.{Await,Future}
import scala.concurrent.duration._

import java.io.File

import akka.actor.{ ActorRef, ActorSystem }
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route

import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Source,Sink}

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.{ContentType, HttpEntity, HttpResponse, StatusCodes }
import akka.http.scaladsl.server.{RequestContext, Route}
import akka.http.scaladsl.server.directives.MethodDirectives.get
import akka.http.scaladsl.server.directives.MethodDirectives.put
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import akka.http.scaladsl.server.directives.PathDirectives.path

import com.mchange.sc.v1.consuela.ethereum.{ stub, EthAddress, EthChainId, EthHash }
import com.mchange.sc.v1.consuela.ethereum.stub.sol

import com.mchange.sc.v1.unrevokedsigned.contract.UnrevokedSigned

import com.mchange.sc.v3.failable._
import com.mchange.sc.v3.failable.logging._

import akka.pattern.ask
import akka.util.{ByteString,Timeout}

object AkkaHttpServer {

  implicit val system       : ActorSystem       = ActorSystem("UnrevokedSignedAkkaHttp")
  implicit val materializer : ActorMaterializer = ActorMaterializer()

  lazy val contractAddress = EthAddress( ConfigProps.getProperty( "unrevokedsigned.eth.contractAddress" ) )

  lazy val nodeUrl = ConfigProps.getProperty( "unrevokedsigned.eth.nodeUrl" )
  lazy val chainId = ConfigProps.getProperty( "unrevokedsigned.eth.chainId" ).toInt

  lazy val localDataStore : DataStore = new DataStore.JsonFileBased( new File( ConfigProps.getProperty("unrevokedsigned.data.store.json.data.dir") ) )

  lazy val us : UnrevokedSigned = UnrevokedSigned.build(
    jsonRpcUrl = nodeUrl,
    contractAddress = contractAddress,
    chainId = Some( EthChainId( chainId ) )
  )

  implicit lazy val ssender = stub.Sender.Default

  lazy val routes : Route = {
    def ec( implicit ctx : RequestContext ) = ctx.executionContext

    extractRequestContext { implicit ctx =>
      concat(
        pathPrefix("data-store") {
          concat (
            pathPrefix("put") {
              pathEnd {
                put {
                  complete {
                    val f_bytestring : Future[ByteString] = ctx.request.entity.dataBytes.runWith( Sink.reduce( _ ++ _ ) )
                    f_bytestring.map { bytestring =>
                      val data = bytestring.compact.toArray.toImmutableSeq
                      val contentType = ctx.request.entity.contentType.toString
                      val ok = localDataStore.put( contentType, data ).xwarn( "Attempt to store incoming data failed." ).isSucceeded
                      if (ok) StatusCodes.Created else StatusCodes.InternalServerError
                    }( ec )
                  }
                }
              }
            },
            pathPrefix("get") {
              path(Segment) { hex =>
                pathEnd {
                  complete {
                    Future {
                      localDataStore.get( EthHash.withBytes(hex.decodeHex ) ).xwarn( "Call to get in the local data store failed." ) match {
                        case Succeeded( Some( Tuple2( contentType, data ) ) ) => {
                          ContentType.parse( contentType ) match {
                            case Right( ct ) => {
                              HttpResponse( entity = HttpEntity( ct, data.toArray ) )
                            }
                            case Left( error ) => {
                              WARNING.log( s"Couldn't parse content type! ${error}" )
                              HttpResponse( status = StatusCodes.BadRequest )
                            }
                          }
                        }
                        case Succeeded( None ) => {
                          HttpResponse( status = StatusCodes.NotFound )
                        }
                        case Failed( src ) => {
                          HttpResponse( status = StatusCodes.InternalServerError )
                        }
                      }
                    }( ec )
                  }
                }
              }
            },
            pathPrefix("contains") {
              path(Segment) { hex =>
                pathEnd {
                  complete {
                    Future {
                      localDataStore.contains( EthHash.withBytes( hex.decodeHex ) ).xwarn( "Checking whether a hash is contained in the local data store failed." ) match {
                        case Succeeded( found ) => if (found) StatusCodes.OK else StatusCodes.NotFound
                        case Failed( src )      => StatusCodes.InternalServerError
                      }
                    }( ec )
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
                  complete {
                    Future {
                      findSigners( hex.decodeHexAsSeq )
                    }( ec )
                  }
                }
              }
            },
            post {
              fileUpload("document") {
                case (fileInfo, fileStream ) => {
                  complete {
                    val f_bytestring : Future[ByteString] = fileStream.runWith( Sink.reduce( _ ++ _ ) )
                    f_bytestring.map { bytestring =>
                      val normalizer = Normalizer.choose( Option( fileInfo.contentType.toString ), Option( fileInfo.fileName ) )
                      findSigners( EthHash.hash(normalizer(bytestring.compact.toArray)).bytes )
                    }( ec )
                  }
                }
              }
            },
            put {
              complete {
                val f_bytestring : Future[ByteString] = ctx.request.entity.dataBytes.runWith( Sink.reduce( _ ++ _ ) )
                f_bytestring.map { bytestring =>
                  val data = bytestring.compact.toArray
                  val normalizer = Normalizer.choose( Some( ctx.request.entity.contentType.toString ), None )
                  findSigners( EthHash.hash(normalizer(data)).bytes )
                }( ec )
              }
            }
          )
        },
        pathPrefix("find-profile") {
          path(Segment) { hex =>
            pathEnd {
              complete {
                Future {
                  val signerAddr = EthAddress( hex )
                  val solHash = us.constant.profileHashForSigner( signerAddr )
                  Profile( solHash.widen.hex )
                }( ec )
              }
            }
          }
        },
        pathPrefix("metadata") {
          complete {
            Future {
              Metadata( chainId, contractAddress.hex )
            }( ec )
          }
        },
        pathPrefix("assets") {
          path( RemainingPath ) { path =>
            getFromResource( s"assets/${path}" )
          }
        }
      )
    }
  }

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
    DocumentSigners( docHash.widen.hex, signersList )
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

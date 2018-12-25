package com.mchange.sc.v1.unrevokedsigned.http

import com.mchange.sc.v1.consuela._

import com.mchange.sc.v1.unrevokedsigned.DataStore

import com.mchange.sc.v1.log.MLevel._

import scala.concurrent.Await
import scala.concurrent.duration.Duration

import akka.actor.{ ActorRef, ActorSystem }
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.{ContentType, HttpEntity, StatusCodes }
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.MethodDirectives.get
import akka.http.scaladsl.server.directives.MethodDirectives.put
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import akka.http.scaladsl.server.directives.PathDirectives.path

import com.mchange.sc.v1.consuela.ethereum.EthHash

import scala.concurrent.duration._
import akka.pattern.ask
import akka.util.Timeout

object AkkaHttpServer {

  implicit val system       : ActorSystem       = ActorSystem("UnrevokedSignedAkkaHttp")
  implicit val materializer : ActorMaterializer = ActorMaterializer()

  implicit lazy val timeout = Timeout(5.seconds)

  val unrevokedSignedHttpActor : ActorRef = system.actorOf(UnrevokedSignedHttpActor.props, "unrevokedSignedHttpActor")

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
      path(Segment) { hex =>
        pathEnd {
          complete( StatusCodes.NotImplemented )
        }
      }
    },
    pathPrefix("find-profile") {
      path(Segment) { hex =>
        pathEnd {
          complete( StatusCodes.NotImplemented )
        }
      }
    }
  )

  def main( argv : Array[String] ) : Unit = {
    val port      = ConfigProps.getProperty( "unrevokedsigned.http.server.port" ).toInt
    val iface     = ConfigProps.getProperty( "unrevokedsigned.http.server.interface" )
    val clientUrl = ConfigProps.getProperty( "unrevokedsigned.http.client.url" )

    Http().bindAndHandle(routes, iface, port)

    println(s"Server online at 'http://${iface}:${port}/', should be exported to client URL '${clientUrl}'")

    Await.result(system.whenTerminated, Duration.Inf)
  }
}
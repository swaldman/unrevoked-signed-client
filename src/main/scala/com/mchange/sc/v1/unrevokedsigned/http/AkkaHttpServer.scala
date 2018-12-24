package com.mchange.sc.v1.unrevokedsigned.http

import scala.concurrent.Await
import scala.concurrent.duration.Duration

import akka.actor.{ ActorRef, ActorSystem }
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer

object AkkaHttpServer {

  implicit val system       : ActorSystem       = ActorSystem("UnrevokedSignedAkkaHttp")
  implicit val materializer : ActorMaterializer = ActorMaterializer()

  val unrevokedSignedHttpActor : ActorRef = system.actorOf(UnrevokedSignedHttpActor.props, "unrevokedSignedHttpActor")

  lazy val routes : Route = concat(
    pathPrefix("data-store") {
      concat (
        pathPrefix("put") {
          pathEnd {
            post {
              entity(as[Array[Byte]]) { bytes =>
                extractRequestContext { ctx =>
                  val contentType = ctx.request.entity.contentType.toString
                  val fbool = (unrevokedSignedHttpActor ? UnrevokedSignedHttpActor.Put( contentType, bytes )).mapTo[Boolean]
                  fbool.map( ok => complete( if (ok) StatusCodes.Created else StatusCodes.InternalServerError ) )
                }
              }
            }
          }
        },
        pathPrefix("get") {
          path(Segment) { hex =>
            pathEnd {
              (unrevokedSignedHttpActor ? UnrevokedSignedHttpActor.Get( EthHash( hex ) ) ).mapTo[UnrevokedSignedHttpActor.GetResponse].map {
                case UnrevokedSignedHttpActor.GetResponse.Success( contentType, data ) => {
                  ContentType.parse( contentType ) match {
                    case Right( ct ) => complete( HttpEntity( ct, data.toArray ) )
                    case Left( error ) => {
                      WARNING.log( s"Couldn't parse content type! ${error}" )
                      complete( StatusCodes.InternalServerError )
                    }
                  }
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
              (unrevokedSignedHttpActor ? UnrevokedSignedHttpActor.Contains( EthHash( hex ) ) ).mapTo[UnrevokedSignedHttpActor.ContainsResponse].map {
                case UnrevokedSignedHttpActor.ContainsResponse.Success( result )  => complete( if (found) StatusCodes.Ok else StatusCodes.NotFound )
                case UnrevokedSignedHttpActor.ContainsResponse.Failure( message ) => complete( StatusCodes.InternalServerError )
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
    val props = {
      borrow( classOf[UnrevokedSignedHttpActor].getClassLoader().getResource("unrevokedsigned.properties").openStream() ) { is =>
        java.util.Properties.load(is)
      }
    }
    val port      = props.getProperty( "unrevokedsigned.http.server.port" ).toInt
    val iface     = props.getProperty( "unrevokedsigned.http.server.interface" )
    val clientUrl = props.getProperty( "unrevokedsigned.http.client.url" )

    Http().bindAndHandle(routes, iface, port)

    println(s"Server online at 'http://${iface}:${port}/', should be exported to client URL '${clientUrl}'")

    Await.result(system.whenTerminated, Duration.Inf)
  }
}

package com.mchange.sc.v1.unrevokedsigned

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol

package object http extends SprayJsonSupport {

  // import the default encoders for primitive types (Int, String, Lists etc)
  import DefaultJsonProtocol._

  case class Signers( addressesHex : List[String] )
  case class Profile( hash : String )

  implicit val SignersJsonFormat = jsonFormat1( Signers )
  implicit val ProfileJsonFormat = jsonFormat1( Profile )

}

package com.mchange.sc.v1.unrevokedsigned

import com.mchange.sc.v2.lang.borrow

import com.mchange.sc.v1.log.MLevel._

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol

package object http extends SprayJsonSupport {

  // import the default encoders for primitive types (Int, String, Lists etc)
  import DefaultJsonProtocol._

  implicit lazy val logger = mlogger( this )

  case class Signers( addressesHex : List[String] )
  case class Profile( hash : String )

  implicit val SignersJsonFormat = jsonFormat1( Signers )
  implicit val ProfileJsonFormat = jsonFormat1( Profile )

  val ConfigProps = {
    borrow( classOf[UnrevokedSignedHttpActor].getClassLoader().getResource("unrevokedsigned.properties").openStream() ) { is =>
      val out = new java.util.Properties()
      out.load(is)
      out
    }
  }
}

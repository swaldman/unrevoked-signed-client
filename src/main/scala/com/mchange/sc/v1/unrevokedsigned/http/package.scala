package com.mchange.sc.v1.unrevokedsigned

import com.mchange.sc.v2.lang.borrow

import com.mchange.sc.v1.log.MLevel._

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol

package object http extends SprayJsonSupport {

  // import the default encoders for primitive types (Int, String, Lists etc)
  import DefaultJsonProtocol._

  implicit lazy val logger = mlogger( this )

  case class Signer( address : String, profileHash : String )
  case class DocumentSigners( documentHash : String, signers : List[Signer] )
  case class Profile( profileHash : String )
  case class Metadata( chainId : Int, contractAddress : String )

  implicit val SignerJsonFormat = jsonFormat2( Signer )

  implicit val SignersJsonFormat = jsonFormat2( DocumentSigners )

  implicit val ProfileJsonFormat = jsonFormat1( Profile )

  implicit val MetadataJsonFormat = jsonFormat2( Metadata )

  val ConfigProps = {
    // which class we choose here is arbitrary, we just want the ClassLoader loading this library
    borrow( classOf[Signer].getClassLoader().getResource("unrevokedsigned.properties").openStream() ) { is =>
      val out = new java.util.Properties()
      out.load(is)
      out
    }
  }
}

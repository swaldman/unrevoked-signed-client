package com.mchange.sc.v1.unrevokedsigned.http

import com.mchange.sc.v3.failable._
import com.mchange.sc.v3.failable.log._

import akka.actor.{ Actor, ActorLogging, Props }

object UnrevokedSignedHttpActor {
  final case class Put( contentType : String, data : immutable.Seq[Byte] )
  final case class Get( hash : EthHash )

  final case class Contains( hash : EthHash )

  final object ContainsResponse {
    final case class Success( result : Boolean ) extends ContainsResponse
    final case class Failed( message : String )  extends ContainsResponse
  }
  sealed trait ContainsResponse

  final object GetResponse {
    final case class Success( contentType : String, data : immutable.Seq[Byte] ) extends GetResponse
    final case class Failed( message : String ) extends GetResponse
  }
  sealed trait GetResponse

  def props : Props = Props[UserRegistryActor]
}
class UnrevokedSignedHttpActor( localDataStore : DataStore ) extends Actor with ActorLogging {
  private def findGetResponse( hash : EthHash ) : GetResponse = {
    localDataStore.get( hash ).xwarn( "Call to get in the local data store failed." ) match {
      case Succeeded( Tuple2( contentType, data ) ) => GetResponse.Success( contentType, data )
      case Failed( src )                            => GetResponse.Failed( src.toString )
    }
  }
  private def findContainsResponse( hash : EthHash ) : ContainsResponse = {
    localDataStore.contains( hash ).xwarn( "Checking whether a hash is contained in the local data store failed." ) match {
      case Succeeded( result ) => ContainsResponse.Success( result )
      case Failed( src )       => ContainsResponse.Failed( src.toString )
    }
  }
  def receive: Receive = {
    case Put( contentType, data ) => {
      sender() ! localDataStore.put( contentType, data ).xwarn( "Attempt to store incoming data failed." ).isSucceeded
    }
    case Get( hash ) => {
      sender() ! findGetResponse( hash )
    }
    case Contains( hash ) => {
      sender() ! findContainsResponse( hash )
    }
  }
}

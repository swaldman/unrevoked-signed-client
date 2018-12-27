import java.net.{ HttpURLConnection, URL }

import scala.collection._

import com.mchange.v1.io.InputStreamUtils
import com.mchange.sc.v2.lang.borrow
import com.mchange.sc.v3.failable._

import com.mchange.sc.v1.consuela._
import com.mchange.sc.v1.consuela.ethereum.EthHash
import com.mchange.sc.v1.unrevokedsigned.DataStore

class HttpProxyDataStore( dataStoreBaseUrl : String ) extends DataStore {

  private val base = if ( dataStoreBaseUrl.endsWith("/") ) dataStoreBaseUrl else dataStoreBaseUrl + "/"
  private def mkConn( path : String ) = (new URL( s"${base}${path}" )).openConnection().asInstanceOf[HttpURLConnection]

  def put( contentType : String, data : scala.Seq[Byte] ) : Failable[EthHash] = Failable.flatCreate {
    val dataAsArray = data.toArray
    borrow( mkConn( "data-store/put" ) )( _.disconnect() ) { conn =>
      conn.setRequestMethod( "PUT" )
      conn.setRequestProperty( "Content-Type", contentType )
      conn.setDoInput( true )
      conn.setDoOutput( true )
      conn.setUseCaches( false )
      borrow( conn.getOutputStream() ) { os =>
        os.write( dataAsArray )
      }
      val responseCode = conn.getResponseCode()
      responseCode match {
        case 201   => Failable.succeed( EthHash.hash( dataAsArray ) )
        case other => Failable.fail( s"HTTP Status Code: ${other}" )
      }
    }
  }
  def get( key : EthHash ) : Failable[Option[Tuple2[String,immutable.Seq[Byte]]]] = Failable.flatCreate {
    borrow( mkConn( s"data-store/get/${key.hex}" ) )( _.disconnect() ) { conn =>
      conn.setRequestMethod( "GET" )
      val responseCode = conn.getResponseCode()
      responseCode match {
        case 200 => {
          val contentType = conn.getContentType()
          val bytes = borrow( conn.getInputStream() ) { is =>
            InputStreamUtils.getBytes( is ).toImmutableSeq
          }
          Failable.succeed( Some( Tuple2( contentType, bytes ) ) )
        }
        case 404 => {
          Failable.succeed( None )
        }
        case other => {
          Failable.fail( s"HTTP Status Code: ${other}" )
        }
      }
    }
  }
  def contains( key : EthHash ) : Failable[Boolean] = Failable.flatCreate {
    borrow( mkConn( s"data-store/contains/${key.hex}" ) )( _.disconnect() ) { conn =>
      conn.setRequestMethod( "GET" )
      val responseCode = conn.getResponseCode()
      responseCode match {
        case 200   => Failable.succeed( true )
        case 404   => Failable.succeed( false )
        case other => Failable.fail( s"HTTP Status Code: ${other}" )
      }
    }
  }
}



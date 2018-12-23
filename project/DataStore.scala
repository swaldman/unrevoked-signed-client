import scala.collection._

import com.mchange.sc.v1.consuela._
import com.mchange.sc.v1.consuela.ethereum.EthHash

import com.mchange.sc.v2.io.RichFile
import com.mchange.sc.v2.lang.borrow

import com.mchange.sc.v3.failable._

import java.io.{File, BufferedInputStream, FileInputStream}

import java.util.Base64

import play.api.libs.json.Json


object DataStore {

  object JsonFileBased {
    object Record {
      implicit val RecordFormat = Json.format[Record]
    }
    case class Record( contentType : String, dataBase64 : String )
  }
  class JsonFileBased( dir : File ) extends DataStore {
    dir.mkdirs()
    require( dir.exists && dir.isDirectory && dir.canRead && dir.canWrite, s"Data directory '${dir}' must exist or be creatable, and must be readable and writable to users of this repository." )

    def put( contentType : String, data : scala.Seq[Byte] ) : Failable[EthHash] = Failable {
      val dataHash = EthHash.hash( data )
      val newFile = new File( dir, dataHash.hex )

      require( !newFile.exists , s"Cannot put, file '${newFile.getAbsolutePath}' already exists." )

      val dataBase64 = Base64.getEncoder().encodeToString( data.toArray )
      val jsonText = Json.stringify( Json.toJson( JsonFileBased.Record( contentType, dataBase64 ) ) )
      newFile.replaceContents( jsonText, scala.io.Codec.UTF8 )

      dataHash
    }

    def get( key : EthHash ) : Failable[Tuple2[String,immutable.Seq[Byte]]] = Failable {
      val file = new File( dir, key.hex )
      val record = borrow( new BufferedInputStream( new FileInputStream( file ) ) )( Json.parse ).as[JsonFileBased.Record]
      val rawData = Base64.getDecoder().decode( record.dataBase64 ).toImmutableSeq

      assert( EthHash.hash(rawData) == key, s"File '${file.getAbsolutePath}' corrupted! The hash of the loaded data does not match the file name!" )

      ( record.contentType, rawData )
    }

    def contains( key : EthHash ) : Failable[Boolean] = Failable {
      val file = new File( dir, key.hex )
      file.exists()
    }
  }
}
trait DataStore {
  def put( contentType : String, data : scala.Seq[Byte] ) : Failable[EthHash]
  def get( key : EthHash )                                : Failable[Tuple2[String,immutable.Seq[Byte]]]
  def contains( key : EthHash )                           : Failable[Boolean]
}

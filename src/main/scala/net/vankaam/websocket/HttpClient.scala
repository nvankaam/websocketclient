package net.vankaam.websocket

import net.vankaam.websocket.ActorSystemManager._
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import de.heikoseeberger.akkahttpjson4s.Json4sSupport._
import org.json4s.native.Serialization
import org.json4s.{DefaultFormats, Formats, MappingException, Serialization, native}

import scala.concurrent.{Await, Future}
import scala.async.Async.{async, await}
import scala.concurrent.ExecutionContext.Implicits.global




class HttpClient {
  /*
  TODO: Move this to some configuration
   */
  implicit val serialization: Serialization.type = native.Serialization
  implicit val formats       = DefaultFormats
  implicit val mat    = ActorMaterializer()

  /**
    * Post an http request to the give nurl
    * @param uri uri to post to
    * @param data the data to post
    * @tparam TData type of the data to post
    * @tparam TResult type of the result to recieve
    * @return
    */
  def post[TData <: AnyRef, TResult:Manifest](uri:String, data:TData): Future[TResult] = async {
    val entity = await(Marshal(data).to[RequestEntity])
    val request = HttpRequest(HttpMethods.POST, uri,entity = entity)
    val result = await(Http().singleRequest(request))
    if(result.status.intValue() == 200) {
      await(Unmarshal(result.entity).to[TResult])
    } else {
      throw new IllegalStateException(s"Http request responsed with ${result.status.intValue()}: ${result.status.value}")
    }
  }
}

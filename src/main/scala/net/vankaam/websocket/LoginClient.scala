package net.vankaam.websocket

import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import spray.json._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging

import scala.collection._
import scala.async.Async.{async, await}

/**
  * Simple client that performs a post to a url and obtains the cookie header
  */
class LoginCookieClient(uri:String, content:LoginRequest) extends LazyLogging with Serializable {


  def GetLoginCookie(implicit system:ActorSystem): Future[HttpCookie] = async {
      logger.debug("Requesting cookie")
      val entity = await(Marshal(content).to[RequestEntity])
      val response = await(Http().singleRequest(HttpRequest(HttpMethods.POST, uri, entity = entity)))
      val cookieHeaders = response.headers.collect { case `Set-Cookie`(x) => x }
      if (response.status.intValue() != 200) {
        throw new IllegalStateException(response.entity.toString)
      }
      if (cookieHeaders.size > 1) {
        throw new IllegalStateException(s"Multiple cookie headers recieved")
      }
      logger.debug("Got cookie")
      cookieHeaders.head
  }

  /**
    * Retrieves a cookie header
    * @return
    */
  def GetCookieHeader(implicit system:ActorSystem):Future[immutable.Seq[HttpHeader]] = async {
    val cookie = await(GetLoginCookie(system))
    val header = akka.http.scaladsl.model.headers.Cookie(cookie.pair())
    immutable.Seq(header)
  }
}


/**
  * Payload class for the login request
  * @param Username username to login with
  * @param Password password to login with
  */
case class LoginRequest(Username:String,Password:String)

object LoginRequest extends DefaultJsonProtocol {
  implicit val format: RootJsonFormat[LoginRequest] = jsonFormat2(LoginRequest.apply)
}
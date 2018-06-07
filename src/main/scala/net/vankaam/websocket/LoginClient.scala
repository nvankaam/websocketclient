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
import com.typesafe.scalalogging.LazyLogging

import scala.collection._
import scala.async.Async.{async, await}

/**
  * Simple client that performs a post to a url and obtains the cookie header
  */
class LoginCookieClient extends LazyLogging {
  implicit val system: ActorSystem = ActorSystem.create("WebSocketClient")

  def GetLoginCookie(uri: String, content: LoginRequest): Future[HttpCookie] = async  {
    logger.debug("Requesting cookie")
    val entity = await(Marshal(content).to[RequestEntity])
    val response = await(Http().singleRequest(HttpRequest(HttpMethods.POST, uri, entity = entity)))
    val cookieHeaders = response.headers.collect { case `Set-Cookie`(x) => x }
    if(cookieHeaders.size > 1) {
      throw new IllegalStateException(s"Multiple cookie headers recieved")
    }
    logger.debug("Got cookie")
    cookieHeaders.head
  }

  /**
    * Retrieves a cookie header
    * @param uri to request cookie on
    * @param content content to obtain the cookie
    * @return
    */
  def GetCookieHeader(uri:String, content:LoginRequest):Future[immutable.Seq[HttpHeader]] = async {
    val cookie = await(GetLoginCookie(uri,content))
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
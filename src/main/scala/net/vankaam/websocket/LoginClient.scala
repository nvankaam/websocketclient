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
import akka.http.scaladsl.settings.ConnectionPoolSettings
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging

import scala.collection._
import scala.async.Async.{async, await}
import scala.concurrent.duration.Duration

/**
  * Simple client that performs a post to a url and obtains the cookie header
  */
class LoginCookieClient(uri:String, loginTimeout:Duration,content:LoginRequest) extends LazyLogging with Serializable {


  def GetLoginCookie(implicit actorSystem:ActorSystem): Future[Either[Exception,HttpCookie]] = async {
      logger.debug("Requesting cookie")


      //Create connectionpoolsettings with timeout
      val orig = ConnectionPoolSettings(actorSystem.settings.config).copy(idleTimeout = loginTimeout)
      //Change the timeout on the clientconnection as well. Note that this is a different timeout than above
      val clientSettings = orig.connectionSettings.withIdleTimeout(loginTimeout)
      val settings = orig.copy(connectionSettings = clientSettings)

      val entity = await(Marshal(content).to[RequestEntity])
      val response = await(Http().singleRequest(HttpRequest(HttpMethods.POST, uri, entity = entity),settings=settings))

      val cookieHeaders = response.headers.collect { case `Set-Cookie`(x) => x }
      if (!response.status.isSuccess()) {
        Left(new IllegalStateException(response.entity.toString))
      } else if (cookieHeaders.size > 1) {
        Left(new IllegalStateException(s"Multiple cookie headers received"))
      } else {
        logger.debug("Got cookie")
        Right(cookieHeaders.head)
      }
  }

  /**
    * Retrieves a cookie header
    * @return
    */
  def GetCookieHeader(implicit system:ActorSystem):Future[Either[Exception,immutable.Seq[HttpHeader]]] = async {
    val cookie = await(GetLoginCookie(system))
    cookie match {
      case Right(c) =>
        val header = akka.http.scaladsl.model.headers.Cookie(c.pair())
        Right(immutable.Seq(header))
      case Left(e) => Left(e)
    }
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
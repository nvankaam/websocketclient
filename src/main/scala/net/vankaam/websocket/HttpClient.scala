package net.vankaam.websocket

import akka.actor.ActorSystem
import akka.http.javadsl.model.headers.HttpCredentials
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Authorization
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpjson4s.Json4sSupport._
import org.json4s.native.Serialization
import org.json4s.{DefaultFormats, Formats, native}
import org.slf4j.{LoggerFactory, MDC}
import resource.managed

import scala.collection._
import scala.concurrent.{Await, Future}
import scala.async.Async.{async, await}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration








/**
  * A simple http client
  * Please call terminate when actorSystem should terminate
  */
class HttpClient {
  private lazy val logger = LoggerFactory.getLogger(classOf[HttpClient])
  @transient implicit lazy val actorSystem = ActorSystem("HttpClient")

  /*
  TODO: Move this to some configuration
   */
  implicit val serialization: Serialization.type = native.Serialization
  implicit val formats: Formats = DefaultFormats ++ org.json4s.ext.JodaTimeSerializers.all
  implicit val mat: ActorMaterializer = ActorMaterializer()


  /**
    * Post an http request to the give nurl
    *
    * @param uri  uri to post to
    * @param data the data to post
    * @param timeout Timeout for the post
    * @tparam TData   type of the data to post
    * @tparam TResult type of the result to recieve
    * @return
    */
  def post[TData <: AnyRef, TResult: Manifest](uri: String, data: TData)(timeout:Duration): Future[Either[Exception, TResult]] = {
    val headers = immutable.Seq.empty[HttpHeader]
    postWithHeaders[TData, TResult](uri, data, headers)(timeout)
  }

  def postBasicAuth[TData <: AnyRef, TResult: Manifest](uri: String, data: TData, username: String, password: String)(timeout:Duration): Future[Either[Exception, TResult]] = {
    val auth = headers.Authorization(headers.BasicHttpCredentials(username, password))
    postWithHeaders[TData, TResult](uri, data, List(auth))(timeout)
  }

  /**
    * Marshals the entity.
    *
    * @param data
    * @tparam TEntity
    * @return
    */
  def doMarshal[TEntity <: AnyRef](data: TEntity): Future[Either[Exception, RequestEntity]] = {
    import de.heikoseeberger.akkahttpjson4s.Json4sSupport._
    implicit val serialization = native.Serialization

    Marshal(data).to[RequestEntity]
        .map (Right(_))
        .recover { case e: Exception => Left(e) }
  }


  def postWithHeaders[TData <: AnyRef, TResult: Manifest](uri: String, data: TData, headers: immutable.Seq[HttpHeader])(timeout:Duration): Future[Either[Exception, TResult]] = async {
    import de.heikoseeberger.akkahttpjson4s.Json4sSupport._
    if (logger.isTraceEnabled()) {
      logger.trace(s"Marshalling entity for uri $uri")
    }

    val entityResult = await(doMarshal(data))


    if (entityResult.isRight) {
      if (logger.isTraceEnabled()) {
        for (_ <- managed(MDC.putCloseable("requestdata", entityResult.right.get.toString))) {
          logger.trace(s"Sending data to uri $uri")
        }
      }
    }

    //Create connectionpoolsettings with timeout
    val orig = ConnectionPoolSettings(actorSystem.settings.config).copy(idleTimeout = timeout)
    //Change the timeout on the clientconnection aswell. Note that this is a different timeout than above
    val clientSettings = orig.connectionSettings.withIdleTimeout(timeout)
    val settings = orig.copy(connectionSettings = clientSettings)

    val webRequestResult = entityResult match {
      case Left(e) => Left(e)
      case Right(entity) => {
        val request = HttpRequest(HttpMethods.POST, uri, headers, entity)
        await(Http().singleRequest(request,settings=settings).map(o => Right(o)).recover { case e: Exception => Left(e) })
      }
    }

    //TODO: Restruture this
    webRequestResult match {
      case Left(e) => Left(e)
      case Right(result) =>
        if (result.status.intValue() == 200) {
          if (logger.isTraceEnabled()) {
            logger.trace(s"Unmarshalling entity on $uri")
          }
          val e = await(Unmarshal(result.entity).to[TResult]
            .map(o => Right(o).asInstanceOf[Either[Exception, TResult]])
            .recover { case e: Exception => Left(e) }
          )
          if (logger.isTraceEnabled()) {
            for (_ <- managed(MDC.putCloseable("responsedata", e.toString))) {
              logger.trace(s"Recieved data from uri $uri")
            }
          }
          e
        } else {
          Left(new IllegalStateException(s"Http request responsed with ${result.status.intValue()}: ${result.status.value}"))
        }
    }

  }


  def close(): Unit = {
    actorSystem.terminate()
  }


}

package net.vankaam.websocket

import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Authorization
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpjson4s.Json4sSupport._
import org.joda.time.Duration
import org.json4s.native.Serialization
import org.json4s.{DefaultFormats, Formats, native}
import org.slf4j.{LoggerFactory, MDC}
import resource.managed

import scala.collection._
import scala.concurrent.{Await, Future}
import scala.async.Async.{async, await}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{duration => scalaDur}
import scala.util.Try




/**
  * A simple http client
  * Please call terminate when actorSystem should terminate
  */
class HttpClient(val config:Config, classLoader:ClassLoader) {

  protected implicit def toScalaFiniteDuration(d:Duration): FiniteDuration =
    FiniteDuration(d.getMillis,scalaDur.MILLISECONDS)

  protected implicit def toScalaDuration(d:FiniteDuration): scalaDur.Duration =
    scalaDur.Duration(d.length,scalaDur.MILLISECONDS)


  private lazy val logger = LoggerFactory.getLogger(classOf[HttpClient])
  @transient implicit lazy val actorSystem: ActorSystem = {
    val name = s"HttpClient${UUID.randomUUID().toString}"
    logger.info(s"Creating actorsystem $name")
    ActorSystem(name,config,classLoader)
  }


  /*
  TODO: Move this to some configuration
   */
  @transient lazy implicit val serialization: Serialization.type = native.Serialization
  @transient lazy implicit val formats: Formats = DefaultFormats ++ org.json4s.ext.JodaTimeSerializers.all
  @transient lazy implicit val mat: ActorMaterializer = ActorMaterializer()


  /**
    * Post an http request to the given url
    *
    * @param uri  uri to post to
    * @param data the data to post
    * @param timeout Timeout for the post
    * @tparam TData   type of the data to post
    * @tparam TResult type of the result to receive
    * @return
    */
  def post[TData <: AnyRef, TResult: Manifest](uri: String, data: TData)(timeout:Duration): Future[Either[Exception, TResult]] = {
    val headers = immutable.Seq.empty[HttpHeader]
    postWithHeaders[TData, TResult](uri)(headers)(timeout)(data)
  }

  def postBasicAuth[TData <: AnyRef, TResult: Manifest](uri: String, data: TData, username: String, password: String)(timeout:Duration): Future[Either[Exception, TResult]] = {
    val auth = headers.Authorization(headers.BasicHttpCredentials(username, password))
    postWithHeaders[TData, TResult](uri)(List(auth))(timeout)(data)
  }

  /**
    * Marshals the entity.
    *
    * @param data
    * @tparam TEntity
    * @return
    */
  protected def doMarshal[TEntity <: AnyRef](data: TEntity, timeout:Duration): Future[Either[Exception, HttpEntity.Strict]] = async {
    import de.heikoseeberger.akkahttpjson4s.Json4sSupport._
    implicit val serialization = native.Serialization

    val httpEntity = await(Marshal(data).to[HttpEntity]
        .map (Right(_))
        .recover { case e: Exception => Left(new Exception(e)) })


    httpEntity match {
      case Right(h) => val f:Future[HttpEntity.Strict] = h.toStrict(timeout)
        Right(await(f))
      case Left(l) => Left(l)
    }
  }


  def postRawXmlWithHeaders[TResult:Manifest](uri:String)(headers: immutable.Seq[HttpHeader])(data:String)(timeout:Duration): Future[Either[Exception, TResult]] = {
    val entity = HttpEntity(ContentTypes.`text/xml(UTF-8)`,data)
    httpWithHeaders[TResult](uri)(headers)(timeout)(Some(entity))
  }








  def httpWithHeaders[TResult:Manifest](uri:String)(headers: immutable.Seq[HttpHeader])(timeout:Duration)(data:Option[HttpEntity.Strict] = None): Future[Either[Exception, TResult]] = async {
  //Create connectionpoolsettings with timeout
  val orig = ConnectionPoolSettings(actorSystem.settings.config).copy(timeout.getMillis.toInt)
  //Change the timeout on the clientconnection as well. Note that this is a different timeout than above
  val clientSettings = orig.connectionSettings.withIdleTimeout(timeout)
  val settings = orig.copy(connectionSettings = clientSettings)

    val request = data match {
      case None => HttpRequest(HttpMethods.GET,uri,headers)
      case Some(data) => HttpRequest(HttpMethods.POST, uri, headers, data)
    }

    logger.trace(s"Sending http request to ${request.uri}. ($request)")

  val webRequestResult = await(Http().singleRequest(request,settings=settings).map(o => Right(o)).recover { case e: Exception => Left(new Exception(e)) })


  //TODO: Restruture this
  webRequestResult match {
    case Left(e) => Left(e)
    case Right(result) =>
      if (result.status.isSuccess()) {
        if (logger.isTraceEnabled()) {
          val d = await(debugEntity(result.entity))
          logger.trace(s"Unmarshalling entity on $uri. Response was \n$d\n")
        }

        val e =result.status.intValue() match {
          case 204 => Try(().asInstanceOf[TResult]) match {
            case scala.util.Success(value) => Right(value)
            case scala.util.Failure(f) => Left(new Exception(f))
          }
          case _ => await(Unmarshal(result.entity).to[TResult]
        .map(o => Right(o).asInstanceOf[Either[Exception, TResult]])
        .recover { case e: Exception => Left(new Exception(e)) })
        }



        if (logger.isTraceEnabled()) {
          for (_ <- managed(MDC.putCloseable("responsedata", e.toString))) {
            logger.trace(s"Received data from uri $uri")
          }
        }
        e
      } else {
        val d = await(debugEntity(result.entity))
        Left(new IllegalStateException(s"Http request responsed with ${result.status.intValue()}: ${result.status.value}. Content was: \n$d\n"))
      }
  }

}

  def debugEntity(entity:ResponseEntity):Future[String] = async {
    val duration = Duration.millis(1000)
    val strict = await(entity.toStrict(duration))
    strict.getData().utf8String
  }


  def postWithHeaders[TData <: AnyRef, TResult: Manifest](uri: String)(headers: immutable.Seq[HttpHeader])(timeout:Duration)(data: TData): Future[Either[Exception, TResult]] = async {
    if (logger.isTraceEnabled()) {
      logger.trace(s"Marshalling entity for uri $uri")
    }

    val entityResult = await(doMarshal(data,timeout))


    entityResult match {
      case Left(v) => Left(v)
      case Right(entity) =>
        if (logger.isTraceEnabled()) {
          for (_ <- managed(MDC.putCloseable("requestdata", entityResult.right.get.toString))) {
            logger.trace(s"Sending data to uri $uri")
          }
        }


        await(httpWithHeaders(uri)(headers)(timeout)(Some(entity)))
    }
  }


  def close(): Unit = {
    actorSystem.terminate()
  }


}

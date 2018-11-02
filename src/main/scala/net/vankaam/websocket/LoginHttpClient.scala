package net.vankaam.websocket

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpHeader}
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import org.joda.time.{DateTime, Duration}

import scala.concurrent.Future
import scala.async.Async.{async, await}
import scala.collection.immutable
import scala.concurrent.{duration => scalaDur}
import scala.concurrent.ExecutionContext.Implicits.global

case class LoginHeader(creation:DateTime, header:Future[Either[Exception,immutable.Seq[HttpHeader]]]) {
  def isValid(timeoutDuration:Duration):Boolean =
    creation.plus(timeoutDuration).isAfter(DateTime.now())

}


case class LoginHttpClientConfig(
                                cookieTimeout:Duration,
                                cookieRequestTimeout:Duration,
                                loginUri:String,
                                loginUser:String,
                                loginPassword:String
                                )

class LoginHttpClient(akkaConfig:Config,config:LoginHttpClientConfig,classLoader:ClassLoader) extends HttpClient(akkaConfig, classLoader) with LazyLogging {

  @transient @volatile private var loginHeader:Option[LoginHeader] = None

  validateConfiguration()

  private def validateConfiguration():Unit = {
    val errorMessage = config match {
      case LoginHttpClientConfig(_,_,loginUri,_,_) if loginUri == null => Some("loginUri was null")
      case LoginHttpClientConfig(_,_,_,loginUser,_) if loginUser == null => Some("loginUser was null")
      case LoginHttpClientConfig(_,_,_,_,loginPassword) if loginPassword == null => Some("loginPassword was null")
      case _ => None
    }

    errorMessage match {
      case Some(m) =>
        val error = new IllegalArgumentException(s"LoginClientConfiguration was invalid: $m. Configuration: \'$config\'")
        logger.error("LoginClientConfiguration was invalid", error)
        throw error
      case None =>
    }
  }

  /**
    * Retrieves the current cookie header
    * @return
    */
  def getCookie():LoginHeader = {
    if(loginHeader.isEmpty || !loginHeader.get.isValid(config.cookieTimeout)) {
      loginHeader = Some(LoginHeader(DateTime.now(),getNewCookie()))
    }
    loginHeader.get
  }

  private def getNewCookie():Future[Either[Exception,immutable.Seq[HttpHeader]]] = {
    val cookieClient = new LoginCookieClient(config.loginUri,scalaDur.Duration(config.cookieRequestTimeout.getMillis, scalaDur.MILLISECONDS), LoginRequest(config.loginUser,config.loginPassword))
    cookieClient.GetCookieHeader
  }


  def postRawXmlWithLogin[TResult: Manifest](uri:String)(data:String)(timeout:Duration): Future[Either[Exception, TResult]] = {
    val entity = HttpEntity(ContentTypes.`text/xml(UTF-8)`,data)
    postEntityWithLogin(uri)(entity)(timeout)
  }

  def getWithLogin[TResult:Manifest](uri:String)(timeout:Duration): Future[Either[Exception,TResult]] = async {
    val headers = await(getCookie().header)
    headers match {
      case Left(e) => Left(e)
      case Right(h) => await(httpWithHeaders[TResult](uri)(h)(timeout)(None))
    }
  }

  def postEntityWithLogin[TResult: Manifest](uri: String)(data: HttpEntity.Strict)(timeout: Duration): Future[Either[Exception, TResult]] = async {
    val headers = await(getCookie().header)
    headers match {
      case Left(e) => Left(e)
      case Right(h) => await(httpWithHeaders(uri)(h)(timeout)(Some(data)))
    }
  }

  /**
    * Post data with a login cookie
    * Login cookies are cached for a configured duration
    * @param uri Uri to request
    * @param data Data to post
    * @param timeout Timeout to wait for the request
    * @tparam TData Data to post
    * @tparam TResult Result type
    * @return
    */
  def postWithLogin[TData <: AnyRef, TResult: Manifest](uri: String)(data: TData)(timeout:Duration): Future[Either[Exception, TResult]] = async {
    val entity = await(doMarshal(data, timeout))
    entity match {
      case Left(e) => Left(e)
      case Right(v) => await(postEntityWithLogin(uri)(v)(timeout))
    }
  }
}

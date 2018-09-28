package net.vankaam.websocket

import java.util.UUID

import akka.Done
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws._
import akka.stream.scaladsl._
import akka.stream.{ActorMaterializer, OverflowStrategy}

import scala.async.Async.{async, await}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future, Promise}
import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.duration._
import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}

import scala.collection._
import com.typesafe.scalalogging.LazyLogging



/**
  * Client that performs the polls for the web socket source function
  */
class WebSocketClient(url: String,objectName: String, callback: String => Unit,loginClient: Option[LoginCookieClient], val config:Config,val classLoader:ClassLoader) extends LazyLogging {

  @transient implicit lazy val actorSystem: ActorSystem = {
    val name = s"WebSocketClient_${UUID.randomUUID().toString}"
    logger.info(s"Creating actorsystem $name")
    ActorSystem(name,config,classLoader)
  }
  @transient implicit lazy val materializer: ActorMaterializer = ActorMaterializer()


  private var initMessages = 0
  private val initializePromise = Promise[Boolean]()

  /*
    Queue used to push messages onto the web socket
   */
  private var queue: SourceQueueWithComplete[Message] = _


  @volatile private var expecting: AtomicInteger = new AtomicInteger(0)
  private var pollComplete:Promise[Boolean] = _
  private val closePromise = Promise[Unit]()


  private val onClose:Future[Unit] = async {
    closePromise.future.flatMap(_ => actorSystem.terminate()).onComplete(t => {
      if(t.isFailure) {
        logger.error("Closepromise produced an error: ",t.failed.get)
        if(!pollComplete.isCompleted) {
          pollComplete.failure(t.failed.get)
        }
      } else {
        logger.info("Actor system for web socket client terminated")
        logger.info("Finishing poll complete because socket has closed")
        if(pollComplete != null && !pollComplete.isCompleted) {
          pollComplete.success(false)
        }
      }
    })

  }






  /**
    * forEach sink handling messages from the server
    */
  private val sink: Sink[Message, Future[Done]] = Sink.foreach {
    case message: TextMessage.Strict => onNextMessage(message.text)
    case message: TextMessage.Streamed =>
      message.textStream.runFold("")(_+_).onComplete(o => {
        if(o.isSuccess) {
          onNextMessage(o.get)
        } else {
          logger.error("Unexpected error while unfolding stream",o.failed)
        }
      })
    case _ =>
      logger.error("Unexpected message")
  }

  /**
    * Internal handler for a new message
    * @param message message to wait for
    */
  private def onNextMessage(message:String): Unit = {
        logger.trace(s"Got message: $message")
      if(message.startsWith("error") || message.startsWith("Error")) {
        val error = new IllegalStateException(message)
        logger.error(message,error)
        closePromise.failure(error)
        pollComplete.failure(error)
      }
        if(initializePromise.isCompleted) {
          message.split('\n').foreach(o => {
            val newValue = expecting.decrementAndGet()
            callback(o)
            if (newValue == 0) {
              logger.debug("Poll has completed")
              pollComplete.success(true)
            }
          })

          //If we received all messages the poll has finished

        } else {
          initMessage(message)
        }
  }

  /**
    * Handler for messages during intialization fase
    * For now just ignores the first two messages
    * @param str message to intialize the stream with
    */
  private def initMessage(str: String): Unit = {
    if(str == "EntityStreamController is busy") {
      initializePromise.success(false)
    }
    initMessages +=1
    if(initMessages == 2) {
      logger.debug(s"Got debug message: $str")
      initializePromise.success((true))
    }
  }

  def onClosed:Future[Unit] = onClose


  /**
    * Performs a poll of the given offset and number of messages
    * @param offset How many messages to skip
    * @param nr Number of messages from the passed offset
    */
  def poll(offset: Long, nr: Int): Future[Boolean] = {
    if(expecting.get() != 0) {
      throw new Exception("Cannot poll while not yet completed")
    }
    pollComplete = Promise()
    expecting.set(nr)

    queue.offer(TextMessage(s"$nr.$offset"))
    pollComplete.future
  }


  /**
    * Opens the web socket connection
    * @return a future when the connection has been opened
    */
  def open(): Future[Boolean] = async {
    //Obtain headers
    val headers = loginClient match {
      case Some(f) => await(f.GetCookieHeader)
      case None => immutable.Seq.empty[HttpHeader]
    }

    val webSocketFlow = Http().webSocketClientFlow(WebSocketRequest(url,headers))

    val ((q, upgradeResponse),s) = Source.queue[Message](Int.MaxValue, OverflowStrategy.backpressure)
      .viaMat(webSocketFlow)(Keep.both)
      .toMat(sink)(Keep.both)
      .run()
    queue = q

    if(queue == null) {
      throw new Exception(s"Stream produced an empty queue")
    }

    //When done, finish the close promise
    //If an error occured, the promise might already be completed at this point
    s.onSuccess {case b => {
      logger.info("Websocket stream has completed.")
      if(!closePromise.isCompleted) {
        closePromise.success()
      }
    }}
    s.onFailure {
      case e:PeerClosedConnectionException =>
        logger.warn(s"Received closed connection exception: ",e)
        if(!closePromise.isCompleted) {
          closePromise.success()
        }
      case t:Throwable=> {
      logger.error(s"Error in stream: ${t.getMessage}",t)
      if(!closePromise.isCompleted) {
        closePromise.failure(t)
      }
    }}



    val connected = upgradeResponse.map { upgrade =>
      if (upgrade.response.status != StatusCodes.SwitchingProtocols) {
        throw new RuntimeException(s"Connection failed: ${upgrade.response.status}")
      }
    }





    await(orClosed(connected))

    if(closePromise.isCompleted) {
      logger.error(s"Error while connecting. Closepromise completed")
      throw new IllegalStateException("Error while connecting. Closepromise completed")
    } else {
      logger.info("Connected to socket. Requesting subject")
      //Initialize the server with the object
      await(orClosed(queue.offer(TextMessage(objectName))))
      await(orClosed(initializePromise.future))
      logger.info(s"Socket opened and ready to receive data")
    }

    //Wait some time for the initialization promise, or just return false becasue something probably went wrong
    try {
      Await.result(initializePromise.future,5 seconds)
    } catch {
      case e:Exception =>
        logger.warn("Timeout on intialization promise", e)
        false
    }
  }

  /**
    * Closes the web socket. After this you should still wait on the "onClosed" method to wait for the actual source to close
    */
  def close(): Unit = {
    if(queue != null) {
      queue.complete()
    }
  }


  def orClosed[T](f:Future[T]) =
    Future.firstCompletedOf(List(f,closePromise.future))


}


trait WebSocketClientFactory extends Serializable {
  /**
    * Construct a new web socket
    * @param url url to the web socket
    * @param objectName name of the object to request from the web socket
    * @param callback callback method for data received from the web socket
    * @return
    */
  def getSocket(url: String,objectName: String, callback: String => Unit,config:Config,classLoader:ClassLoader): WebSocketClient

  /**
    * Construct a new web socket with a header factory
    * @param url url to the web socket
    * @param objectName name of the object to request
    * @param callback callback method for data received from the web socket
    * @param loginClient factory obtaining the headers asynchronously
    * @return
    */
  def getSocket(url: String,objectName: String, callback: String => Unit, loginClient: Option[LoginCookieClient],config:Config,classLoader: ClassLoader): WebSocketClient
}

object WebSocketClientFactory extends WebSocketClientFactory  {
  override def getSocket(url: String, objectName: String, callback: String => Unit,config:Config,classLoader:ClassLoader): WebSocketClient = new WebSocketClient(url,objectName,callback,None,config,classLoader)

  override def getSocket(url: String, objectName: String, callback: String => Unit, loginClient: Option[LoginCookieClient],config:Config,classLoader: ClassLoader) =
    new WebSocketClient(url,objectName,callback,loginClient,config,classLoader)
}
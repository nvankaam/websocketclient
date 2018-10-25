package net.vankaam.websocket

import akka.actor.Status.Success
import akka.stream.StreamTcpException
import com.typesafe.config.ConfigFactory
import org.joda.time.Duration
import org.scalatest.AsyncFlatSpec

import scala.async.Async.{async, await}
import scala.util.Failure


case class Data(name: String)
case class Response(data:String)

class HttpClientSpec extends AsyncFlatSpec {

  val timeout = Duration.millis(60*1000)

  "HttpClient.post" should "Post data to an url and await the result" in async {
    val client = new HttpClient(ConfigFactory.load(),this.getClass.getClassLoader)
    val data = Data("dataname")
    val result = await(client.post[Data,Response]("http://httpbin.org/post",data)(timeout))

    assert(result.right.get.data == "{\"name\":\"dataname\"}")
  }

  it should "throw an exception if the request is invalid" in async {
    val client = new HttpClient(ConfigFactory.load(),this.getClass.getClassLoader)
    val data = Data("dataname")
    var error = false

    val r = await(client.post[Data, Response]("http://localhost:5305/post", data)(timeout))

    assert(r.isLeft)
  }
}

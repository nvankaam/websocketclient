package net.vankaam.websocket

import akka.actor.Status.Success
import akka.stream.StreamTcpException
import org.scalatest.AsyncFlatSpec

import scala.async.Async.{async, await}
import scala.util.Failure
import scala.concurrent.duration._

case class Data(name: String)
case class Response(data:String)

class HttpClientSpec extends AsyncFlatSpec {
  "HttpClient.post" should "Post data to an url and await the result" in async {
    val client = new HttpClient()
    val data = Data("dataname")
    val result = await(client.post[Data,Response]("http://httpbin.org/post",data)(60 seconds))

    assert(result.right.get.data == "{\"name\":\"dataname\"}")
  }

  it should "throw an exception if the request is invalid" in async {
    val client = new HttpClient()
    val data = Data("dataname")
    var error = false

    val r = await(client.post[Data, Response]("http://localhost:5305/post", data)(60 seconds))

    assert(r.isLeft)
  }
}

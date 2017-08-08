package controllers

import java.util.concurrent.LinkedBlockingQueue

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpHeader, StatusCodes}
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.ws.{Message, TextMessage, WebSocketRequest}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play._
import org.scalatestplus.play.guice._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json}
import play.api.test._
import play.api.test.Helpers._

import scala.collection.immutable.Seq
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.Failure

class FunctionalSpec extends PlaySpec with ScalaFutures {

  "HomeController" should {
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    import system.dispatcher

    "reject a websocket flow if the origin is set incorrectly" in {
      lazy val port: Int = 9000
      val app = new GuiceApplicationBuilder().build()
      Helpers.running(TestServer(port, app)) {
        val serverUrl = s"ws://localhost:$port/ws"
        val flow = Flow.fromSinkAndSourceMat(Sink.ignore, Source.empty)(Keep.left).completionTimeout(3000.millis)
        val header = Seq(RawHeader("Origin", "ws://examplex123.com"))
        val (upgradeResponse, closed) = Http().singleWebSocketRequest(WebSocketRequest(serverUrl, header), flow)
        val result = Await.result(upgradeResponse, 3.seconds)
        result.response.status.value mustBe "403 Forbidden"
      }
    }

    "accept a websocket flow if the origin is set correctly" in {
      lazy val port: Int = 9000
      val app = new GuiceApplicationBuilder().build()
      val queue = new LinkedBlockingQueue[JsValue]()
      Helpers.running(TestServer(port, app)) {
        val serverUrl = s"ws://localhost:$port/ws"
        val sink: Sink[Message, Future[Done]] =
          Sink.foreach {
            case message: Message =>
              val json = Json.parse(message.asTextMessage.getStrictText)
              queue.put(json)
          }
        val flow = Flow.fromSinkAndSourceMat(sink, Source.maybe)(Keep.left).completionTimeout(3000.millis)
        val header = Seq(RawHeader("Origin", serverUrl))
        val (upgradeResponse, closed) = Http().singleWebSocketRequest(WebSocketRequest(serverUrl, header), flow)
        val result = Await.result(upgradeResponse, 3.seconds)
        result.response.status.value mustBe "101 Switching Protocols"

        val json = queue.take()
        val symbol = (json \ "symbol").as[String]
        List(symbol) must contain oneOf("AAPL", "GOOG", "ORCL")
      }
    }
  }
}

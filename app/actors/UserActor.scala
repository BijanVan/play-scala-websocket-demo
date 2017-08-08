package actors

import javax.inject.{Inject, Named}

import akka.actor.{Actor, ActorRef}
import akka.event.{LogMarker, MarkerLoggingAdapter}
import akka.pattern.ask
import akka.stream.scaladsl.{BroadcastHub, Flow, Keep, MergeHub, Sink}
import akka.stream.{KillSwitches, Materializer, UniqueKillSwitch}
import akka.util.Timeout
import akka.{Done, NotUsed}
import com.google.inject.assistedinject.Assisted
import play.api.libs.json._
import stocks.{Stock, StockSymbol}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class UserActor @Inject()(@Assisted id: String, @Named("stocksActor") stocksActor: ActorRef)
                         (implicit mat: Materializer, ec: ExecutionContext) extends Actor {
  private val marker = LogMarker(self.path.name)
  private val log: MarkerLoggingAdapter = akka.event.Logging.withMarker(context.system, this.getClass)
  private implicit val timeout = Timeout(50.millis)
  private var stocksMap: Map[StockSymbol, UniqueKillSwitch] = Map.empty
  private val (hubSink, hubSource) = MergeHub.source[JsValue]
    .toMat(BroadcastHub.sink(bufferSize = 256))(Keep.both)
    .run()
  private val jsonSink: Sink[JsValue, Future[Done]] = Sink.foreach { json =>
    val symbol = (json \ "Symbol").as[StockSymbol]
    addStocks(Set(symbol))
  }

  private lazy val websocketFlow: Flow[JsValue, JsValue, NotUsed] = Flow.fromSinkAndSourceCoupled(jsonSink, hubSource)
    .watchTermination() { (_, termination) =>
      termination.foreach(_ => context.stop(self))
      NotUsed
    }

  override def receive: Receive = {
    case WatchStocks(symbols) =>
      addStocks(symbols)
      sender() ! websocketFlow
    case UnwatchStocks(symbols) =>
      unwatchStocks(symbols)
  }

  override def postStop(): Unit = {
    log.info(marker, s"Stopping actor $self")
    unwatchStocks(stocksMap.keySet)
  }

  private def addStocks(symbols: Set[StockSymbol]): Future[Unit] = {
    val future = (stocksActor ? WatchStocks(symbols)).mapTo[Stocks]
    future.map { newStocks =>
      newStocks.stocks.foreach { stock =>
        if (!stocksMap.contains(stock.symbol)) {
          log.info(marker, s"Adding stock $stock")
          addStock(stock)
        }
      }
    }
  }

  private def addStock(stock: Stock): Unit = {
    val historySource = stock.history(50).map(Json.toJson(_))
    val updateSource = stock.update.map(Json.toJson(_))
    val stockSource = historySource.concat(updateSource)

    val killSwitchFlow = Flow[JsValue]
      .joinMat(KillSwitches.singleBidi[JsValue, JsValue])(Keep.right)
      .backpressureTimeout(1.second)

    val stockFlow = stockSource
      .viaMat(killSwitchFlow)(Keep.right)
      .to(hubSink)
      .named(s"stock-${stock.symbol}-$id")

    val killSwitch = stockFlow.run()
    stocksMap += (stock.symbol -> killSwitch)
  }

  private def unwatchStocks(symbols: Set[StockSymbol]): Unit = symbols.foreach { symbol =>
    stocksMap.get(symbol).foreach { killSwitch =>
      killSwitch.shutdown()
    }
    stocksMap -= symbol
  }
}

object UserActor {

  trait Factory {
    def apply(id: String): Actor
  }

}

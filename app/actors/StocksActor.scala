package actors

import akka.actor.{Actor, ActorLogging}
import akka.event.LoggingReceive
import stocks.{Stock, StockSymbol}

import scala.collection.mutable

class StocksActor extends Actor with ActorLogging {
  private val stocksMap: mutable.Map[StockSymbol, Stock] = mutable.HashMap()

  override def receive: Receive = LoggingReceive {
    case WatchStocks(symbols) =>
      val stocks = symbols.map(symbol => stocksMap.getOrElseUpdate(symbol, new Stock(symbol)))
      sender ! Stocks(stocks)
  }
}

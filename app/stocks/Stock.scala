package stocks

import akka.NotUsed
import akka.stream.ThrottleMode
import akka.stream.scaladsl.Source
import play.api.libs.json._

import scala.concurrent.duration._
import scala.util.Random

class Stock(val symbol: StockSymbol) {
  private val stockQuoteGenerator = new FakeStockQuoteGenerator(symbol)
  private val source: Source[StockQuote, NotUsed] = Source.unfold(stockQuoteGenerator.seed)(last => {
    val next = stockQuoteGenerator.newQuote(last)
    Some(next, next)
  })

  def history(n: Int): Source[StockHistory, NotUsed] = source
    .grouped(n).map(grp => StockHistory(symbol, grp.map(_.price)))

  def update: Source[StockUpdate, NotUsed] = source
    .throttle(elements = 1, per = 75.millis, maximumBurst = 1, mode = ThrottleMode.shaping)
    .map(sq => StockUpdate(sq.symbol, sq.price))

  override def toString: String = s"Stock($symbol)"
}

class StockSymbol private(val raw: String) extends AnyVal {
  override def toString: String = raw
}

object StockSymbol {

  def apply(raw: String) = new StockSymbol(raw)

  implicit val stockSymbolReads: Reads[StockSymbol] = JsPath.read[String].map(StockSymbol(_))

  implicit val stockSymbolWrites: Writes[StockSymbol] = symbol => JsString(symbol.raw)

}

class StockPrice private(val raw: Double) extends AnyVal {
  override def toString: String = raw.toString
}

object StockPrice {
  def apply(raw: Double): StockPrice = new StockPrice(raw)

  implicit val stockPriceWrites: Writes[StockPrice] = price => JsNumber(price.raw)
}

case class StockQuote(symbol: StockSymbol, price: StockPrice)

case class StockHistory(symbol: StockSymbol, prices: Seq[StockPrice])

object StockHistory {
  implicit val stockHistoryWrites: Writes[StockHistory] = history => Json.obj(
    "type" -> "stockhistory",
    "symbol" -> history.symbol,
    "history" -> history.prices)
}

case class StockUpdate(symbol: StockSymbol, price: StockPrice)

object StockUpdate {
  implicit val stockUpdateWrites: Writes[StockUpdate] = update => Json.obj(
    "type" -> "stockupdate",
    "symbol" -> update.symbol,
    "history" -> update.price)
}

trait StockQuoteGenerator {
  def seed: StockQuote

  def newQuote(lastQuote: StockQuote): StockQuote
}

class FakeStockQuoteGenerator(symbol: StockSymbol) extends StockQuoteGenerator {
  override def seed: StockQuote = StockQuote(symbol, StockPrice(Random.nextDouble() * 800))

  override def newQuote(lastQuote: StockQuote): StockQuote =
    StockQuote(symbol, StockPrice(lastQuote.price.raw * (0.95 + (0.1 * Random.nextDouble()))))
}



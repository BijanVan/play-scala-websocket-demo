package controllers

import javax.inject._

import actors._
import akka.NotUsed
import akka.actor.ActorRef
import akka.pattern.ask
import akka.stream.scaladsl.Flow
import akka.util.Timeout
import play.api._
import play.api.libs.json.{JsValue, Json}
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

/**
  * This controller creates an `Action` to handle HTTP requests to the
  * application's home page.
  */
@Singleton
class HomeController @Inject()(@Named("userParentActor") userParentActor: ActorRef,
                               cc: ControllerComponents)
                              (implicit ec: ExecutionContext) extends AbstractController(cc) with SameOriginCheck {

  val logger = play.api.Logger(getClass)

  def index() = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.index())
  }

  def ws: WebSocket = WebSocket.acceptOrResult[JsValue, JsValue] {
    case rh if sameOriginCheck(rh) =>
      wsFutureFlow(rh).map { flow =>
        Right(flow)
      }.recover {
        case e: Exception =>
          logger.error("Cannot create websocket", e)
          Left(InternalServerError(Json.obj("error" -> "Cannot create websocket")))
      }

    case rejected =>
      logger.error(s"Request $rejected failed same origin check")
      Future.successful {
        Left(Forbidden("forbidden"))
      }
  }

  private def wsFutureFlow(request: RequestHeader) = {
    implicit val timeout = Timeout(1.second)

    val future = userParentActor ? Create(request.id.toString)
    future.mapTo[Flow[JsValue, JsValue, NotUsed]]
  }
}

trait SameOriginCheck {
  def logger: Logger

  def sameOriginCheck(rh: RequestHeader): Boolean = rh.headers.get("Origin") match {
    case Some(originValue) if originMatches(originValue) =>
      logger.debug(s"originCheck: originValue = $originValue")
      true
    case Some(badOrigin) =>
      logger.error(s"originCheck: rejecting request because Origin header value $badOrigin is not in the same origin")
      false

    case None =>
      logger.error("originCheck: rejecting request because no Origin header found")
      false
  }

  def originMatches(origin: String): Boolean = origin.contains("localhost:9000") || origin.contains("localhost:19001")
}

package reactive

import akka.actor.{ActorSelection, ActorPath, Actor, ActorRef}
import akka.event.{Logging, LoggingReceive}

object AuctionSearch {

  final case class GetAuctions(name: String)

  val Name = "auctionSearch"

  case object Register

  final case class AuctionList(auctions: List[ActorPath])

}

class AuctionSearch extends Actor {
  var auctions = List[ActorPath]()
  val log = Logging(context.system, this)
  log.info("initialized AuctionSearch!")

  override def receive = LoggingReceive {
    case AuctionSearch.Register => auctions = sender.path :: auctions
      log.info(s"received registration from ${sender().path}!")
    case AuctionSearch.GetAuctions(name) =>
      sender ! AuctionSearch.AuctionList(auctions.filter(actorPath => actorPath.name contains name))
    case _ => // ignore
  }
}

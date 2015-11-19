package reactive

import akka.actor.{Actor, ActorRef}
import akka.event.{Logging, LoggingReceive}

object AuctionSearch {

  final case class GetAuctions(name: String)

  val Name = "auctionSearch"

  case object Register

  final case class AuctionList(auctions: List[ActorRef])

}

class AuctionSearch extends Actor {
  var auctions = List[ActorRef]()
  val log = Logging(context.system, this)

  override def receive = LoggingReceive {
    case AuctionSearch.Register => auctions = sender() :: auctions
      log.info(s"received registration from ${sender().path}!")
    case AuctionSearch.GetAuctions(name) =>
      sender ! AuctionSearch.AuctionList(auctions.filter(actor => actor.path.name contains name))
    case _ => // ignore
  }
}

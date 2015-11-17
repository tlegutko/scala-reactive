package reactive

import akka.actor.{ActorRef, Actor}
import akka.actor.Actor.Receive
import akka.event.LoggingReceive
import reactive.AuctionMessage.ItemSold

object Seller {
  final case class StartAuction(auction: ActorRef)
}

class Seller extends Actor {
  val rand = scala.util.Random
  override def receive = LoggingReceive {
    case Seller.StartAuction(auction) => auction ! AuctionMessage.StartAuction(rand.nextInt(300))
    case ItemSold => println(s"${self.path.name} sold ${sender.path.name}!")
    case _ => // ignore
  }
}

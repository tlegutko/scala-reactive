package reactive

import akka.actor.{Actor, ActorRef, Props}
import akka.event.LoggingReceive
import reactive.AuctionMessage.{ItemSold, Bid}

object Buyer {
  def props(auctionActors: List[ActorRef]): Props = Props(new Buyer(auctionActors))
}

class Buyer(actorRefs: List[ActorRef]) extends Actor {
  val rand = scala.util.Random

  def bidAuctions: Receive = LoggingReceive {
    case Bid(amount) => actorRefs(rand.nextInt(actorRefs.length)) ! Bid(amount) // bid random auction
    case ItemSold => println(s"${self.path.name} bought ${sender.path.name}!")
    case _ => //ignore
  }

  override def receive = bidAuctions

}

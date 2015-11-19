package reactive

import akka.actor.{Stash, Actor, ActorRef}
import akka.event.LoggingReceive
import reactive.AuctionMessage.{FindAndBid, ItemSold}

object Buyer {
  val MaxAmount = 500;
}

class Buyer extends Actor with Stash {
  val rand = scala.util.Random

  def bidAuctions: Receive = LoggingReceive {
    case FindAndBid(name, amount) =>
      context.actorSelection("/user/" + AuctionSearch.Name) ! AuctionSearch.GetAuctions(name)
      context become waitingForAuctionList(amount)
    case ItemSold => println(s"${self.path.name} bought ${sender.path.name}!")
    case _ => //ignore
  }

  def waitingForAuctionList(amountToBid: BigDecimal) = LoggingReceive {
    case AuctionSearch.AuctionList(auctions: List[ActorRef]) =>
      auctions(rand.nextInt(auctions.length)) ! AuctionMessage.Bid(rand.nextInt(Buyer.MaxAmount))
      unstashAll()
      context become bidAuctions
    case _ => stash()
  }

  override def receive = bidAuctions

}

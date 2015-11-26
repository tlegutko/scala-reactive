package reactive

import akka.actor.{ActorPath, Stash, Actor, ActorRef}
import akka.event.{Logging, LoggingReceive}
import reactive.AuctionMessage.{FindAndBid, ItemSold}

class Buyer(initialMoney: BigDecimal) extends Actor with Stash {
  val log = Logging(context.system, this)
  val rand = scala.util.Random

  def bidAuctions(moneyLeft: BigDecimal): Receive = LoggingReceive {
    case FindAndBid(name, amount) =>
      context.actorSelection("/user/" + AuctionSearch.Name) ! AuctionSearch.GetAuctions(name)
    context become waitingForAuctionList(moneyLeft, amount)
    case AuctionMessage.BidAccepted(amount) =>
      context become bidAuctions(if (moneyLeft - amount > 0) moneyLeft - amount else 0)
    case AuctionMessage.OutBid(amount) =>
      if (moneyLeft > amount)
        sender ! AuctionMessage.Bid(amount+1)
    case ItemSold => println(s"${self.path.name} bought ${sender.path.name}!")
    case _ => //ignore
  }

  def waitingForAuctionList(moneyLeft: BigDecimal, amountToBid: BigDecimal) = LoggingReceive {
    case AuctionSearch.AuctionList(auctions: List[ActorPath]) =>
      if (auctions.nonEmpty)
        context.actorSelection(auctions(rand.nextInt(auctions.length))) ! AuctionMessage.Bid(amountToBid)
      else
        log.error("Auction not found! Bid not made.")
      unstashAll()
      context become bidAuctions(moneyLeft)
    case _ => stash()
  }

  override def receive = bidAuctions(initialMoney)

}

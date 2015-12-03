package reactive

import akka.actor.{Actor, ActorPath, Stash}
import akka.event.{Logging, LoggingReceive}
import reactive.Buyer.FindAndBid

object Buyer {

  case class FindAndBid(name: String, amount: Int)

}

class Buyer(initialMoney: Int) extends Actor with Stash {
  val log = Logging(context.system, this)
  val rand = scala.util.Random

  def bidAuctions(moneyLeft: Int): Receive = LoggingReceive {
    case FindAndBid(name, amount) =>
      context.actorSelection("/user/" + AuctionSearch.Name) ! AuctionSearch.GetAuctions(name)
      context become waitingForAuctionList(moneyLeft, amount)
    case BidAccepted(amount) =>
      context become bidAuctions(if (moneyLeft - amount > 0) moneyLeft - amount else 0)
    case OutBid(amount) =>
      if (moneyLeft > amount)
        sender ! Bid(amount + 1)
    case ItemSold(amount) => println(s"${self.path.name} bought ${sender.path.name} for ${amount}!")
    case _ => //ignore
  }

  def waitingForAuctionList(moneyLeft: Int, amountToBid: Int) = LoggingReceive {
    case AuctionSearch.AuctionList(auctions: List[ActorPath]) =>
      if (auctions.nonEmpty)
        context.actorSelection(auctions(rand.nextInt(auctions.length))) ! Bid(amountToBid)
      else
        log.error("Auction not found! Bid not made.")
      unstashAll()
      context become bidAuctions(moneyLeft)
    case _ => stash()
  }

  override def receive = bidAuctions(initialMoney)

}

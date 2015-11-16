package reactive

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.event.LoggingReceive
import reactive.Auction.{BidTimerExpired, DeleteTimerExpired, Initialize, Relist}
import reactive.AuctionMessage.{Bid, ItemSold}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object Auction {
  def props(seller: ActorRef, price: BigDecimal): Props = Props(new Auction(seller, price))

  case object Initialize

  case object BidTimerExpired

  case object DeleteTimerExpired

  case object Relist

  val BidTimerDuration = 10 second
  val DeleteTimerDuration = 10 second
}

class Auction(val seller: ActorRef, val startingPrice: BigDecimal) extends Actor {
  self ! Initialize

  override def receive = uninitialized

  def uninitialized: Receive = LoggingReceive {
    case Initialize =>
      context.system.scheduler.scheduleOnce(Auction.BidTimerDuration, self, BidTimerExpired)
      context become created
    case _ => // ignore
  }

  def sold(finalPrice: BigDecimal): Receive = LoggingReceive {
    case DeleteTimerExpired => context stop self
    case _ => // ignore
  }

  def ignored: Receive = LoggingReceive {
    case DeleteTimerExpired => context stop self
    case Relist =>
      context.system.scheduler.scheduleOnce(Auction.BidTimerDuration, self, BidTimerExpired)
      context become created
    case _ => // ignore
  }

  def created: Receive = LoggingReceive {
    case Bid(amount) if amount > startingPrice => context become activated(amount, sender)
    case BidTimerExpired =>
      context.system.scheduler.scheduleOnce(Auction.DeleteTimerDuration, self, DeleteTimerExpired)
      context become ignored
    case _ => // ignore
  }

  def activated(currentPrice: BigDecimal, highestBidder: ActorRef): Receive = LoggingReceive {
    case Bid(amount) if amount > currentPrice => context become activated(amount, sender)
    case BidTimerExpired =>
      context.system.scheduler.scheduleOnce(Auction.DeleteTimerDuration, self, DeleteTimerExpired)
      seller ! ItemSold
      highestBidder ! ItemSold
      context become sold(currentPrice)
    case _ => // ignore
  }

}

object AuctionApp extends App {
  val system = ActorSystem("Reactive2")
  val seller1 = system.actorOf(Props[Seller], "seller1")
  val auction1 = system.actorOf(Auction.props(seller1, 40), "auction1")
  val auction2 = system.actorOf(Auction.props(seller1, 10), "auction2")
  val auction3 = system.actorOf(Auction.props(seller1, 1), "auction3")
  val auction4 = system.actorOf(Auction.props(seller1, 100), "auction4")
  val auctionList = List(auction1, auction2, auction3, auction4)

  val buyer1 = system.actorOf(Buyer.props(auctionList), "buyer1")
  val buyer2 = system.actorOf(Buyer.props(auctionList), "buyer2")
  val buyer3 = system.actorOf(Buyer.props(auctionList), "buyer3")

  val buyersList = List(buyer1, buyer2, buyer3)

  val rand = scala.util.Random

  for (i <- 1 to 5) {
    for (buyer <- buyersList) buyer ! Bid(rand.nextInt(i * 200))
  }

  system.awaitTermination()

}
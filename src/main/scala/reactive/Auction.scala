package reactive

import akka.actor._
import reactive.AuctionMessage.{Relist, StartAuction, ItemSold, Bid}

import scala.concurrent.duration._

sealed trait AuctionState

case object InitialState extends AuctionState

case object Created extends AuctionState

case object Activated extends AuctionState

case object Ignored extends AuctionState

case object Sold extends AuctionState


sealed trait AuctionData

case object Uninitialized extends AuctionData

final case class InitializedAuction(seller: ActorRef, startingPrice: BigDecimal) extends AuctionData {
  require(startingPrice > 0)
}

final case class ActivatedAuction(highestBidder: ActorRef, seller: ActorRef, currentPrice: BigDecimal) extends AuctionData {
  require(currentPrice > 0)
}

object Timer {
  val BidDuration = 10 second
  val DeleteDuration = 10 second
}

class Auction extends FSM[AuctionState, AuctionData] {

  startWith(InitialState, Uninitialized)

  when(InitialState) {
    case Event(StartAuction(startingPrice), Uninitialized) =>
      goto(Created) using InitializedAuction(sender(), startingPrice)
  }

  when(Created, stateTimeout = Timer.BidDuration) {
    case Event(Bid(amount), auctionData: InitializedAuction) if amount > auctionData.startingPrice =>
      goto(Activated) using ActivatedAuction(sender(), auctionData.seller, amount)
    case Event(Bid(amount), auctionData: InitializedAuction) =>
      log.info(s"bid $amount too low (current price: ${auctionData.startingPrice})")
      stay()
    case Event(StateTimeout, auctionData: InitializedAuction) =>
      goto(Ignored) using auctionData
  }

  when(Ignored, stateTimeout = Timer.DeleteDuration) {
    case Event(Relist, auctionData: InitializedAuction) =>
      goto(Created) using auctionData
    case Event(StateTimeout, auctionData: InitializedAuction) =>
      stop()
  }

  when(Activated, stateTimeout = Timer.BidDuration) {
    case Event(Bid(amount), auctionData: ActivatedAuction) if amount > auctionData.currentPrice =>
      stay using ActivatedAuction(sender(), auctionData.seller, amount)
    case Event(Bid(amount), auctionData: ActivatedAuction) =>
      log.info(s"$stateName: bid $amount too low (current price: ${auctionData.currentPrice})")
      stay()
    case Event(StateTimeout, auctionData: ActivatedAuction) =>
      auctionData.seller ! ItemSold
      auctionData.highestBidder ! ItemSold
      goto(Sold) using auctionData
  }

  when(Sold, stateTimeout = Timer.DeleteDuration) {
    case Event(StateTimeout, auctionData: ActivatedAuction) =>
      stop()
  }

  whenUnhandled {
    case Event(e, s) =>
      log.warning("received unhandled request {} in state {}/{}", e, stateName, s)
      stay()
  }

  initialize()
}

object AuctionApp extends App {
  val system = ActorSystem("Reactive2")
  val seller1 = system.actorOf(Props[Seller], "seller1")

  val auction1 = system.actorOf(Props[Auction], "auction1")
  val auction2 = system.actorOf(Props[Auction], "auction2")
  val auction3 = system.actorOf(Props[Auction], "auction3")
  val auction4 = system.actorOf(Props[Auction], "auction4")
  seller1 ! Seller.StartAuction(auction1)
  seller1 ! Seller.StartAuction(auction2)
  seller1 ! Seller.StartAuction(auction3)
  seller1 ! Seller.StartAuction(auction4)

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
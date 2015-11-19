package reactive

import akka.actor._
import reactive.AuctionMessage.{Bid, ItemSold, Relist}

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

final case class ActivatedAuction(seller: ActorRef, highestBidder: ActorRef, currentPrice: BigDecimal) extends AuctionData {
  require(currentPrice > 0)
}

object Timer {
  val BidDuration = 3 seconds
  val DeleteDuration = 3 seconds
}

case object Initialize

class Auction(seller: ActorRef, startingPrice: BigDecimal) extends FSM[AuctionState, AuctionData] with Stash {
  println(s"${self.path}")
  startWith(InitialState, Uninitialized)
  self ! Initialize

  when(InitialState) {
    case Event(Initialize, Uninitialized) =>
      context.actorSelection("/user/" + AuctionSearch.Name) ! AuctionSearch.Register
      unstashAll()
      goto(Created) using InitializedAuction(seller, startingPrice)
    case _ =>
      stash()
      stay()
  }

  when(Created, stateTimeout = Timer.BidDuration) {
    case Event(Bid(amount), auctionData: InitializedAuction) if amount > auctionData.startingPrice =>
      goto(Activated) using ActivatedAuction(auctionData.seller, sender(), amount)
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
      stay using ActivatedAuction(auctionData.seller, sender(), amount)
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

  val auctionList = List("czadowy_komputer", "krzeslo_mistrzow", "krzywy_stol", "zamkniete_drzwi")

  val auctionSearch = system.actorOf(Props[AuctionSearch], AuctionSearch.Name)
  val seller1 = system.actorOf(Seller.props(auctionList), "seller1")

  val buyer1 = system.actorOf(Props[Buyer], "buyer1")

  import system.dispatcher

  system.scheduler.scheduleOnce(1 second) {
    buyer1 ! AuctionMessage.FindAndBid("komputer", 400)
    buyer1 ! AuctionMessage.FindAndBid("krzeslo", 400)
    buyer1 ! AuctionMessage.FindAndBid("stol", 400)
  }

  system.awaitTermination()

}
package reactive

import java.util.Date

import akka.actor._
import akka.event.LoggingReceive
import akka.persistence.{SnapshotOffer, RecoveryCompleted}
import akka.persistence.fsm.PersistentFSM
import akka.persistence.fsm.PersistentFSM.FSMState
import reactive.Auction.{Timer, Initialize}
import reactive.AuctionData.{ActivatedAuction, InitializedAuction, Uninitialized}
import reactive.AuctionMessage.{Bid, ItemSold, Relist}
import reactive.AuctionState._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.reflect._

sealed trait AuctionState extends FSMState

object AuctionState {
  case object InitialState extends AuctionState {
    override def identifier: String = "InitialState"
  }

  case object Created extends AuctionState {
    override def identifier: String = "Created"
  }

  case object Activated extends AuctionState {
    override def identifier: String = "Activated"
  }

  case object Ignored extends AuctionState {
    override def identifier: String = "Ignored"
  }

  case object Sold extends AuctionState {
    override def identifier: String = "Sold"
  }
}

case class AuctionEvent()

sealed trait AuctionData

object AuctionData {
  case class Uninitialized(date: Date) extends AuctionData

  final case class InitializedAuction(seller: ActorPath, startingPrice: BigDecimal) extends AuctionData {
    require(startingPrice > 0)
  }

  final case class ActivatedAuction(seller: ActorPath, highestBidder: ActorPath, currentPrice: BigDecimal) extends AuctionData {
    require(currentPrice > 0)
  }
}

object Auction {
  object Timer {
    val BidDuration = 5 seconds
    val DeleteDuration = 5 seconds
  }

  case class Initialize(date: Date)

}

class Auction(seller: ActorPath, startingPrice: BigDecimal) extends PersistentFSM[AuctionState, AuctionData, AuctionData] with Stash {

  override def persistenceId = self.path.name
  override def domainEventClassTag: ClassTag[AuctionData] = classTag[AuctionData]

  startWith(InitialState, Uninitialized(new Date()))
  self ! Initialize(new Date())

  when(InitialState) {
    case Event(Initialize(date), Uninitialized(initialDate)) =>
      log.info(date.toString)
      context.actorSelection("/user/" + AuctionSearch.Name) ! AuctionSearch.Register
      unstashAll()
      goto(Created) applying InitializedAuction(seller, startingPrice)
    case _ =>
      stash()
      stay()
  }

  when(Created, stateTimeout = Timer.BidDuration) {
    case Event(Bid(amount), auctionData: InitializedAuction) if amount > auctionData.startingPrice =>
      sender ! AuctionMessage.BidAccepted(amount)
      goto(Activated) applying ActivatedAuction(auctionData.seller, sender.path, amount)
    case Event(Bid(amount), auctionData: InitializedAuction) =>
      log.info(s"bid $amount too low (current price: ${auctionData.startingPrice})")
      stay()
    case Event(StateTimeout, auctionData: InitializedAuction) =>
      goto(Ignored) applying auctionData
  }

  when(Ignored, stateTimeout = Timer.DeleteDuration) {
    case Event(Relist, auctionData: InitializedAuction) =>
      goto(Created) applying auctionData
    case Event(StateTimeout, auctionData: InitializedAuction) =>
      log.info("Auction finished without buyer!")
      stop()
  }

  when(Activated, stateTimeout = Timer.BidDuration) {
    case Event(Bid(amount), auctionData: ActivatedAuction) if amount > auctionData.currentPrice =>
      sender ! AuctionMessage.BidAccepted(amount)
      context.actorSelection(auctionData.highestBidder) ! AuctionMessage.OutBid(amount)
      stay applying ActivatedAuction(auctionData.seller, sender.path, amount)
    case Event(Bid(amount), auctionData: ActivatedAuction) =>
      log.info(s"$stateName: bid $amount too low (current price: ${auctionData.currentPrice})")
      stay()
    case Event(StateTimeout, auctionData: ActivatedAuction) =>
      context.actorSelection(auctionData.seller) ! ItemSold
      context.actorSelection(auctionData.highestBidder) ! ItemSold
      goto(Sold) applying auctionData
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

  override def applyEvent(event: AuctionData, data: AuctionData): AuctionData = {
    println("event: " + event.toString)
    event
  }

  override def receiveRecover = LoggingReceive {
    case RecoveryCompleted =>
      log.info(s"current time: ${new Date().toString}")
      log.info(s"Successfully recovered ${self.path.name}")
      log.info(s"${stateData.toString}")
    case SnapshotOffer(_, offeredSnapshot) =>
      log.info(offeredSnapshot.toString)
    case evt =>
      println("gotcha " + evt.toString)
  }

  initialize()
}
package reactive

import akka.actor._
import akka.event.LoggingReceive
import akka.persistence.RecoveryCompleted
import akka.persistence.fsm.PersistentFSM
import akka.persistence.fsm.PersistentFSM.FSMState

import scala.concurrent.duration._
import scala.reflect._

sealed trait AuctionState extends FSMState

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

sealed trait AuctionEvent

case class AuctionInitialized(seller: ActorPath, initialPrice: Int) extends AuctionEvent

case class AuctionBid(seller: ActorPath, currentPrice: Int, highestBidder: ActorPath) extends AuctionEvent

case object AuctionSold extends AuctionEvent

sealed trait AuctionData {
  def initializeAuction(seller: ActorPath, initialPrice: Int, timeLeft: FiniteDuration): AuctionData

  def bidAuction(seller: ActorPath, currentPrice: Int, highestBidder: ActorPath, timeLeft: FiniteDuration): AuctionData

  def timeLeft: FiniteDuration

  def endAuction: AuctionData = SoldAuction

}

case class UninitializedAuction() extends AuctionData {
  override def initializeAuction(seller: ActorPath, initialPrice: Int, timeLeft: FiniteDuration): AuctionData =
    CreatedAuction(seller, initialPrice, timeLeft)

  override def bidAuction(seller: ActorPath, currentPrice: Int, highestBidder: ActorPath, timeLeft: FiniteDuration): AuctionData = ???

  override def timeLeft: FiniteDuration = FiniteDuration(5, SECONDS)
}

case class CreatedAuction(seller: ActorPath, currentPrice: Int, timeLeft: FiniteDuration) extends AuctionData {
  override def initializeAuction(seller: ActorPath, initialPrice: Int, timeLeft: FiniteDuration): AuctionData = ???

  override def bidAuction(seller: ActorPath, currentPrice: Int, highestBidder: ActorPath, timeLeft: FiniteDuration): AuctionData =
    ActiveAuction(seller, currentPrice, highestBidder, timeLeft)

}

case class ActiveAuction(seller: ActorPath, currentPrice: Int, highestBidder: ActorPath, timeLeft: FiniteDuration)
  extends AuctionData {
  override def initializeAuction(seller: ActorPath, initialPrice: Int, timeLeft: FiniteDuration): AuctionData = ???

  override def bidAuction(seller: ActorPath, currentPrice: Int, highestBidder: ActorPath, timeLeft: FiniteDuration): AuctionData =
    ActiveAuction(seller, currentPrice, highestBidder, timeLeft)
}

case object SoldAuction extends AuctionData {
  override def initializeAuction(seller: ActorPath, initialPrice: Int, timeLeft: FiniteDuration): AuctionData =
    SoldAuction

  override def bidAuction(seller: ActorPath, currentPrice: Int, highestBidder: ActorPath, timeLeft: FiniteDuration):
  AuctionData = SoldAuction

  override def timeLeft: FiniteDuration = FiniteDuration(0, SECONDS)
}

sealed trait Command

case object Initialize extends Command

case class Bid(amount: Int) extends Command

case object Relist extends Command

case object AuctionAlreadyFinished extends Command


sealed trait Notification

case class BidAccepted(amount: Int) extends Notification

case class OutBid(amount: Int) extends Notification

case class ItemSold(amount: Int) extends Notification

case object ItemAlreadySold extends Notification


class Auction(seller: ActorPath, startingPrice: Int) extends PersistentFSM[AuctionState, AuctionData, AuctionEvent] {

  override def persistenceId = self.path.name

  override def domainEventClassTag: ClassTag[AuctionEvent] = classTag[AuctionEvent]

  startWith(InitialState, UninitializedAuction())
  self ! Initialize

  when(InitialState) {
    case Event(Initialize, auctionData: UninitializedAuction) =>
      context.actorSelection("/user/" + AuctionSearch.Name) ! AuctionSearch.Register
      goto(Created) applying AuctionInitialized(seller, startingPrice) forMax (auctionData.timeLeft)
  }

  when(Created) {
    case Event(Bid(amount), auctionData: CreatedAuction) if amount > auctionData.currentPrice =>
      log.info("created")
      goto(Activated) applying AuctionBid(auctionData.seller, amount, sender.path) forMax (auctionData.timeLeft) replying BidAccepted(amount)
    case Event(Bid(amount), auctionData: CreatedAuction) =>
      //      log.info(s"$stateName: bid $amount too low (current price: ${auctionData.currentPrice})")
      stay()
    case Event(StateTimeout, auctionData: CreatedAuction) =>
      goto(Ignored) forMax (auctionData.timeLeft)
    case Event(AuctionAlreadyFinished, _) => stop()
  }

  when(Ignored) {
    case Event(Relist, auctionData: CreatedAuction) =>
      goto(Created) forMax (auctionData.timeLeft)
    case Event(StateTimeout, _) =>
      log.info("Auction finished without buyer!")
      stop()
  }

  when(Activated) {
    case Event(Bid(amount), auctionData: ActiveAuction) if amount > auctionData.currentPrice =>
      log.info("activated")
      context.actorSelection(auctionData.highestBidder) ! OutBid(amount)
      stay applying AuctionBid(auctionData.seller, amount, sender.path) replying BidAccepted(amount)
    case Event(Bid(amount), auctionData: ActiveAuction) =>
      //      log.info(s"$stateName: bid $amount too low (current price: ${auctionData.currentPrice})")
      stay()
    case Event(StateTimeout, auctionData: ActiveAuction) =>
      context.actorSelection(auctionData.seller) ! ItemSold(auctionData.currentPrice)
      context.actorSelection(auctionData.highestBidder) ! ItemSold(auctionData.currentPrice)
      goto(Sold) applying AuctionSold forMax (auctionData.timeLeft)
  }

  when(Sold) {
    case Event(StateTimeout, _) =>
      //      stay() replying ItemAlreadySold
      println("auctionStopped")
      stop()
  }

  whenUnhandled {
    case Event(e, s) =>
      log.warning(s"received unhandled request {} in state {}/{}", e, stateName, s)
      stay()
  }

  override def applyEvent(event: AuctionEvent, data: AuctionData): AuctionData = {
    println(s"applyEvent: ${event} / ${data}")
    //    data match {
    //      case SoldAuction => println("haha!"); SoldAuction
    //      case _ =>
    event match {
      case AuctionInitialized(seller, initialPrice) => data.initializeAuction(seller, initialPrice, data.timeLeft)
      case AuctionBid(seller, currentPrice, highestBidder) => data.bidAuction(seller, currentPrice, highestBidder,
        data.timeLeft)
      case AuctionSold => data.endAuction
      //      case AuctionInitialized(seller, initialPrice) => CreatedAuction(seller, initialPrice, 5 seconds)
      //      case AuctionBid(seller, currentPrice, highestBidder) => ActiveAuction(seller, currentPrice, highestBidder, 5
      //        seconds)
      //      case AuctionSold => SoldAuction
    }
    //    }
  }

  var recoveryData: AuctionData = UninitializedAuction()

  override def receiveRecover = LoggingReceive {
    case RecoveryCompleted =>
      println(s"recovery completed in ${stateName}/{$recoveryData}")
      if (recoveryData.timeLeft <= 0.second) {
        self ! AuctionAlreadyFinished
      }
    case evt: AuctionEvent =>
      recoveryData = applyEvent(evt, recoveryData)
    case evt =>
      println(s"recovery received ${evt}")
  }

  initialize()
}

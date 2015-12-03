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

case class AuctionInitialized(seller: ActorPath, initialPrice: Int, startingTime: FiniteDuration) extends AuctionEvent

case class AuctionBid(seller: ActorPath, currentPrice: Int, highestBidder: ActorPath) extends AuctionEvent

case object AuctionSold extends AuctionEvent

sealed trait AuctionData {
  def initializeAuction(seller: ActorPath, initialPrice: Int, timeLeft: FiniteDuration): AuctionData

  def bidAuction(seller: ActorPath, currentPrice: Int, highestBidder: ActorPath, timeLeft: FiniteDuration): AuctionData

  def startingTime: FiniteDuration

  def endAuction: AuctionData = SoldAuction(startingTime)

  def timeLeft: FiniteDuration = time

  var time: FiniteDuration = 5 seconds

}

case class UninitializedAuction() extends AuctionData {
  override def initializeAuction(seller: ActorPath, initialPrice: Int, timeLeft: FiniteDuration): AuctionData =
    CreatedAuction(seller, initialPrice, timeLeft)

  override def bidAuction(seller: ActorPath, currentPrice: Int, highestBidder: ActorPath, startingTime: FiniteDuration): AuctionData = ???

  override def startingTime: FiniteDuration = FiniteDuration(System.currentTimeMillis(), MILLISECONDS)
}

case class CreatedAuction(seller: ActorPath, currentPrice: Int, startingTime: FiniteDuration) extends AuctionData {
  override def initializeAuction(seller: ActorPath, initialPrice: Int, startingTime: FiniteDuration): AuctionData = ???

  override def bidAuction(seller: ActorPath, currentPrice: Int, highestBidder: ActorPath, startingTime: FiniteDuration): AuctionData =
    ActiveAuction(seller, currentPrice, highestBidder, startingTime)
}

case class ActiveAuction(seller: ActorPath, currentPrice: Int, highestBidder: ActorPath, startingTime: FiniteDuration)
  extends AuctionData {
  override def initializeAuction(seller: ActorPath, initialPrice: Int, startingTime: FiniteDuration): AuctionData = ???

  override def bidAuction(seller: ActorPath, currentPrice: Int, highestBidder: ActorPath, startingTime: FiniteDuration): AuctionData =
    ActiveAuction(seller, currentPrice, highestBidder, startingTime)
}

case class SoldAuction(startingTime: FiniteDuration) extends AuctionData {
  override def initializeAuction(seller: ActorPath, initialPrice: Int, startingTime: FiniteDuration): AuctionData =
    SoldAuction(startingTime)

  override def bidAuction(seller: ActorPath, currentPrice: Int, highestBidder: ActorPath, startingTime: FiniteDuration):
  AuctionData = SoldAuction(startingTime)

}

sealed trait Command

case class Initialize(startingTime: FiniteDuration) extends Command

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
  self ! Initialize(FiniteDuration(System.currentTimeMillis(), MILLISECONDS))

  when(InitialState) {
    case Event(Initialize(startingTime), auctionData: UninitializedAuction) =>
      context.actorSelection("/user/" + AuctionSearch.Name) ! AuctionSearch.Register
      goto(Created) applying AuctionInitialized(seller, startingPrice, startingTime) forMax (auctionData.timeLeft)
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

  def timeDiff(startingTime: FiniteDuration, howLong: FiniteDuration): FiniteDuration = {
    FiniteDuration(System.currentTimeMillis(), MILLISECONDS) - startingTime - howLong
  }

  override def applyEvent(event: AuctionEvent, data: AuctionData): AuctionData = {
    println(s"applyEvent: ${event} / ${data}")
    //    data match {
    //      case SoldAuction => println("haha!"); SoldAuction
    //      case _ =>
    event match {
      case AuctionInitialized(seller, initialPrice, startingTime) => data.initializeAuction(seller, initialPrice, startingTime)
      case AuctionBid(seller, currentPrice, highestBidder) => data.bidAuction(seller, currentPrice, highestBidder,
        data.startingTime)
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
//      println(s"recovery completed in ${stateName}/{$recoveryData}")
      recoveryData match {
      case UninitializedAuction() => // uninitialized, do nothing
      case SoldAuction(_) => stateData.time = 0.seconds; self ! AuctionAlreadyFinished
      case _ => {
        stateData.time = timeDiff(recoveryData.startingTime, 0 seconds)
        if (stateData.time < 0.second) {
          printf("already finished!")
          self ! AuctionAlreadyFinished
        }
      }
    }
      println(s"time left: ${stateData.time}");
    case evt: AuctionEvent =>
      recoveryData = applyEvent(evt, recoveryData)
    case evt =>
      println(s"recovery received ${evt}")
  }

  initialize()
}

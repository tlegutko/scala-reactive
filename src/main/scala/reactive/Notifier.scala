package reactive

import akka.actor.Actor
import akka.event.LoggingReceive

object Notifier {
  val Name = "notifier"
}

class Notifier extends Actor {
  override def receive: Receive = LoggingReceive {
    case AuctionBidAccepted(amount, name, path) => {
      println(s"NOTIFIER: AuctionBid ${amount}, ${name}, ${path}")
      context.actorSelection(AuctionPublisher.Name) !AuctionBidAccepted
      (amount, name, path)
    }
    case AuctionFinished(amount, name, path) => {
      println(s"NOTIFIER: AuctionFinished ${amount}, ${name}, ${path}")
      context.actorSelection(AuctionPublisher.Name) !AuctionBidAccepted(amount, name, path)
    }

    case _ => // ignore
  }
}

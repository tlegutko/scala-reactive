package reactive

import akka.actor.Actor
import akka.actor.Actor.Receive
import akka.event.LoggingReceive
import reactive.AuctionMessage.ItemSold

class Seller extends Actor {
  override def receive = LoggingReceive {
    case ItemSold => println(s"${self.path.name} sold ${sender.path.name}!")
    case _ => // ignore
  }
}

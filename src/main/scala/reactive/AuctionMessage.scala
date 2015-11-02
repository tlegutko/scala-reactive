package reactive

object AuctionMessage {
  case class Bid(amount: BigDecimal) {
    require(amount > 0)
  }
  case object BidAccepted
  case object BidRejected
  case object ItemSold

}

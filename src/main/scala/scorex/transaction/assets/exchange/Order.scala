package scorex.transaction.assets.exchange

import com.google.common.primitives.Longs
import io.swagger.annotations.ApiModelProperty
import play.api.libs.json.{JsObject, Json}
import scorex.account.{PrivateKeyAccount, PublicKeyAccount}
import scorex.crypto.EllipticCurveImpl
import scorex.crypto.encode.Base58
import scorex.crypto.hash.FastCryptographicHash
import scorex.serialization.{BytesSerializable, Deser, JsonSerializable}
import scorex.transaction.TransactionParser._
import scorex.transaction._
import scorex.transaction.assets.exchange.Validation.booleanOperators
import scorex.utils.ByteArrayExtension

import scala.util.Try

sealed trait OrderType

object OrderType {

  case object BUY extends OrderType

  case object SELL extends OrderType

}

/**
  * Order to matcher service for asset exchange
  */
case class Order(@ApiModelProperty(dataType = "java.lang.String") senderPublicKey: PublicKeyAccount,
                 @ApiModelProperty(dataType = "java.lang.String", example = "") matcherPublicKey: PublicKeyAccount,
                 @ApiModelProperty(dataType = "java.lang.String") spendAssetId: Option[AssetId],
                 @ApiModelProperty(dataType = "java.lang.String") receiveAssetId: Option[AssetId],
                 @ApiModelProperty(value = "Price for AssetPair.second in AssetPair.first * 10^8",
                   example = "100000000") price: Long,
                 @ApiModelProperty("Amount in AssetPair.second") amount: Long,
                 @ApiModelProperty(value = "Creation timestamp") timestamp: Long,
                 @ApiModelProperty(value = "Order time to live, max = 30 days") expiration: Long,
                 @ApiModelProperty(example = "100000") matcherFee: Long,
                 @ApiModelProperty(dataType = "java.lang.String") signature: Array[Byte])
  extends BytesSerializable
    with JsonSerializable {

  import Order._

  def assetPair: AssetPair = AssetPair(spendAssetId, receiveAssetId)

  def orderType: OrderType = if (ByteArrayExtension.sameOption(receiveAssetId, assetPair.second)) OrderType.BUY else OrderType.SELL

  @ApiModelProperty(hidden = true)
  lazy val signatureValid = EllipticCurveImpl.verify(signature, toSign, senderPublicKey.publicKey)

  def isValid(atTime: Long): Validation = {
    (amount > 0) :| "amount should be > 0" &&
      (price > 0) :| "price should be > 0" &&
      (amount < MaxAmount) :| "amount too large" &&
      getSpendAmount(price, amount).isSuccess :| "SpendAmount too large" &&
      (getSpendAmount(price, amount).getOrElse(0L) > 0) :| "SpendAmount should be > 0" &&
      getReceiveAmount(price, amount).isSuccess :| "ReceiveAmount too large" &&
      (getReceiveAmount(price, amount).getOrElse(0L) > 0) :| "ReceiveAmount should be > 0" &&
      (matcherFee > 0) :| "matcherFee should be > 0" &&
      (matcherFee < MaxAmount) :| "matcherFee too large" &&
      (timestamp > 0) :| "timestamp should be > 0" &&
      (timestamp <= atTime) :| "timestamp should be before created before execution" &&
      (expiration - atTime <= MaxLiveTime) :| "expiration should be earlier than 30 days" &&
      (expiration >= atTime) :| "expiration should be > currentTime" &&
      !ByteArrayExtension.sameOption(spendAssetId, receiveAssetId) :| "Invalid AssetPair" &&
      signatureValid :| "signature should be valid"
  }

  @ApiModelProperty(hidden = true)
  lazy val toSign: Array[Byte] = senderPublicKey.publicKey ++ matcherPublicKey.publicKey ++
    assetIdBytes(spendAssetId) ++ assetIdBytes(receiveAssetId) ++
    Longs.toByteArray(price) ++ Longs.toByteArray(amount) ++
    Longs.toByteArray(timestamp) ++ Longs.toByteArray(expiration) ++
    Longs.toByteArray(matcherFee)

  @ApiModelProperty(hidden = true)
  lazy val id: Array[Byte] = FastCryptographicHash(toSign)

  @ApiModelProperty(hidden = true)
  lazy val idStr: String = Base58.encode(id)

  override def bytes: Array[Byte] = toSign ++ signature

  @ApiModelProperty(hidden = true)
  def getSpendAmount(matchPrice: Long, matchAmount: Long): Try[Long] = Try {
    if (orderType == OrderType.SELL) matchAmount
    else {
      val spend = BigInt(matchAmount) * matchPrice / PriceConstant
      if (spendAssetId.isEmpty && !(spend + matcherFee).isValidLong) {
        throw new ArithmeticException("BigInteger out of long range")
      } else spend.bigInteger.longValueExact()
    }
  }

  @ApiModelProperty(hidden = true)
  def getReceiveAmount(matchPrice: Long, matchAmount: Long): Try[Long] = Try {
    if (orderType == OrderType.BUY) matchAmount
    else {
      (BigInt(matchAmount) * matchPrice / PriceConstant ).bigInteger.longValueExact()
    }
  }

  override def json: JsObject = Json.obj(
    "id" -> Base58.encode(id),
    "senderPublicKey" -> Base58.encode(senderPublicKey.publicKey),
    "matcherPublicKey" -> Base58.encode(matcherPublicKey.publicKey),
    "spendAssetId" -> spendAssetId.map(Base58.encode),
    "receiveAssetId" -> receiveAssetId.map(Base58.encode),
    "price" -> price,
    "amount" -> amount,
    "timestamp" -> timestamp,
    "expiration" -> expiration,
    "matcherFee" -> matcherFee,
    "signature" -> Base58.encode(signature)
  )

  override def canEqual(that: Any): Boolean = that.isInstanceOf[Order]

  override def equals(obj: Any): Boolean = {
    obj match {
      case o: Order => o.canEqual(this) &&
        senderPublicKey == o.senderPublicKey &&
        matcherPublicKey == o.matcherPublicKey &&
        ByteArrayExtension.sameOption(spendAssetId, o.spendAssetId) &&
        ByteArrayExtension.sameOption(receiveAssetId, o.receiveAssetId) &&
        price == o.price &&
        amount == o.amount &&
        expiration == o.expiration &&
        matcherFee == o.matcherFee &&
        (signature sameElements o.signature)
      case _ => false
    }
  }

  override def hashCode(): Int = super.hashCode()
}

object Order {
  val MaxLiveTime: Long = 30L * 24L * 60L * 60L * 1000L
  val PriceConstant = 100000000L
  val MaxAmount = PriceConstant * PriceConstant
  private val AssetIdLength = 32


  def buy(sender: PrivateKeyAccount, matcher: PublicKeyAccount, pair: AssetPair,
          price: Long, amount: Long, timestamp: Long, expiration: Long, matcherFee: Long): Order = {
    val unsigned = Order(sender, matcher, pair.first, pair.second, price, amount, timestamp, expiration, matcherFee, Array())
    val sig = EllipticCurveImpl.sign(sender, unsigned.toSign)
    unsigned.copy(signature = sig)
  }

  def sell(sender: PrivateKeyAccount, matcher: PublicKeyAccount, pair: AssetPair,
           price: Long, amount: Long, timestamp: Long, expiration: Long, matcherFee: Long): Order = {
    val unsigned = Order(sender, matcher, pair.second, pair.first, price, amount, timestamp, expiration, matcherFee, Array())
    val sig = EllipticCurveImpl.sign(sender, unsigned.toSign)
    unsigned.copy(signature = sig)
  }

  def apply(sender: PrivateKeyAccount, matcher: PublicKeyAccount, spendAssetID: Option[AssetId], receiveAssetID: Option[AssetId],
            price: Long, amount: Long, timestamp: Long, expiration: Long, matcherFee: Long): Order = {
    val unsigned = Order(sender, matcher, spendAssetID, receiveAssetID, price, amount, timestamp, expiration, matcherFee, Array())
    val sig = EllipticCurveImpl.sign(sender, unsigned.toSign)
    Order(sender, matcher, spendAssetID, receiveAssetID, price, amount, timestamp, expiration, matcherFee, sig)
  }

  def parseBytes(bytes: Array[Byte]): Try[Order] = Try {
    import EllipticCurveImpl._
    var from = 0
    val sender = PublicKeyAccount(bytes.slice(from, from + KeyLength));
    from += KeyLength
    val matcher = PublicKeyAccount(bytes.slice(from, from + KeyLength));
    from += KeyLength
    val (spendAssetId, s0) = Deser.parseOption(bytes, from, AssetIdLength);
    from = s0
    val (receiveAssetId, s1) = Deser.parseOption(bytes, from, AssetIdLength);
    from = s1
    val price = Longs.fromByteArray(bytes.slice(from, from + AssetIdLength));
    from += 8
    val amount = Longs.fromByteArray(bytes.slice(from, from + AssetIdLength));
    from += 8
    val timestamp = Longs.fromByteArray(bytes.slice(from, from + AssetIdLength));
    from += 8
    val expiration = Longs.fromByteArray(bytes.slice(from, from + AssetIdLength));
    from += 8
    val matcherFee = Longs.fromByteArray(bytes.slice(from, from + AssetIdLength));
    from += 8
    val signature = bytes.slice(from, from + SignatureLength);
    from += SignatureLength
    Order(sender, matcher, spendAssetId, receiveAssetId, price, amount, timestamp, expiration, matcherFee, signature)
  }

  def sign(unsigned: Order, sender: PrivateKeyAccount): Order = {
    require(unsigned.senderPublicKey == sender)
    val sig = EllipticCurveImpl.sign(sender, unsigned.toSign)
    unsigned.copy(signature = sig)
  }

  def splitByType(o1: Order, o2: Order): (Order, Order) = {
    require(o1.orderType != o2.orderType)
    if (o1.orderType == OrderType.BUY) (o1, o2)
    else (o2, o1)
  }

  def assetIdBytes(assetId: Option[AssetId]): Array[Byte] = {
    assetId.map(a => (1: Byte) +: a).getOrElse(Array(0: Byte))
  }
}

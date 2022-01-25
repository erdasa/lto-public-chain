package com.ltonetwork.api.http.requests

import com.ltonetwork.account.KeyTypes.keyType
import com.ltonetwork.account.{PrivateKeyAccount, PublicKeyAccount}
import com.ltonetwork.transaction.ValidationError.{GenericError, InvalidPublicKey}
import com.ltonetwork.transaction.anchor.AnchorTransaction
import com.ltonetwork.transaction.association.{IssueAssociationTransaction, RevokeAssociationTransaction}
import com.ltonetwork.transaction.data.DataTransaction
import com.ltonetwork.transaction.lease.{CancelLeaseTransaction, LeaseTransaction}
import com.ltonetwork.transaction.register.RegisterTransaction
import com.ltonetwork.transaction.smart.SetScriptTransaction
import com.ltonetwork.transaction.sponsorship.{CancelSponsorshipTransaction, SponsorshipTransaction}
import com.ltonetwork.transaction.transfer.{MassTransferTransaction, TransferTransaction}
import com.ltonetwork.transaction.{Transaction, TransactionBuilders, ValidationError}
import com.ltonetwork.utils.Time
import com.ltonetwork.wallet.Wallet
import io.swagger.v3.oas.annotations.media.Schema
import play.api.libs.json.JsObject

trait TxRequest {
  type TransactionT <: Transaction

  val timestamp: Option[Long]
  val senderKeyType: Option[String]
  val senderPublicKey: Option[String]
  val sponsorKeyType: Option[String]
  val sponsorPublicKey: Option[String]

  protected def timestamp(time: Option[Time]): Long = timestamp.getOrElse(time.fold(defaultTimestamp)(_.getTimestamp()))

  protected def sign(tx: TransactionT, signer: PrivateKeyAccount): TransactionT

  private def publicKeyAccount(keyTypeOpt: Option[String], publicKey: String): Either[ValidationError, PublicKeyAccount] =
    for {
      kt  <- keyType(keyTypeOpt).fold(t => Left(InvalidPublicKey(t.getMessage)), Right(_))
      acc <- PublicKeyAccount.fromBase58String(kt, publicKey)
    } yield acc

  protected def resolveSender: Either[ValidationError, PublicKeyAccount] =
    senderPublicKey match {
      case Some(key) => publicKeyAccount(senderKeyType, key)
      case None      => Left(InvalidPublicKey("invalid.senderPublicKey"))
    }

  protected def resolveSender(default: PublicKeyAccount): Either[ValidationError, PublicKeyAccount] =
    senderPublicKey.map(key => PublicKeyAccount.fromBase58String(key)).getOrElse(Right(default))

  protected def resolveSponsor: Either[ValidationError, Option[PublicKeyAccount]] =
    sponsorPublicKey
      .map(publicKeyAccount(sponsorKeyType, _))
      .fold[Either[ValidationError, Option[PublicKeyAccount]]](Right(None))(_.map(k => Some(k)))

  protected def toTxFrom(sender: PublicKeyAccount, sponsor: Option[PublicKeyAccount], time: Option[Time]): Either[ValidationError, TransactionT]

  def toTx: Either[ValidationError, TransactionT] =
    for {
      sender  <- resolveSender
      sponsor <- resolveSponsor
      tx      <- toTxFrom(sender, sponsor, None)
    } yield tx

  def signTx(wallet: Wallet, signerAddress: String, time: Time): Either[ValidationError, TransactionT] =
    for {
      signer  <- wallet.findPrivateKey(signerAddress)
      sender  <- resolveSender(signer)
      sponsor <- resolveSponsor
      tx      <- toTxFrom(sender, sponsor, Some(time))
    } yield sign(tx, signer)

  def sponsorTx(wallet: Wallet, signerAddress: String, time: Time): Either[ValidationError, TransactionT] =
    for {
      signer <- wallet.findPrivateKey(signerAddress)
      sender <- resolveSender
      tx     <- toTxFrom(sender, Some(signer), Some(time))
    } yield sign(tx, signer)
}

object TxRequest {
  abstract class For[T <: Transaction] extends TxRequest {
    override type TransactionT = T
  }

  def fromJson(jsv: JsObject): Either[ValidationError, TxRequest] = {
    val typeId = (jsv \ "type").as[Byte]

    TransactionBuilders.by(typeId) match {
      case None => Left(GenericError(s"Bad transaction type ($typeId)"))
      case Some(x) =>
        x match {
          case AnchorTransaction            => Right(jsv.as[AnchorRequest])
          case IssueAssociationTransaction  => Right(jsv.as[IssueAssociationRequest])
          case RevokeAssociationTransaction => Right(jsv.as[RevokeAssociationRequest])
          case SponsorshipTransaction       => Right(jsv.as[SponsorshipRequest])
          case CancelSponsorshipTransaction => Right(jsv.as[CancelSponsorshipRequest])
          case TransferTransaction          => Right(jsv.as[TransferRequest])
          case MassTransferTransaction      => Right(jsv.as[MassTransferRequest])
          case LeaseTransaction             => Right(jsv.as[LeaseRequest])
          case CancelLeaseTransaction       => Right(jsv.as[CancelLeaseRequest])
          case DataTransaction              => Right(jsv.as[DataRequest])
          case SetScriptTransaction         => Right(jsv.as[SetScriptRequest])
          case RegisterTransaction          => Right(jsv.as[RegisterRequest])
          case _                            => Left(GenericError(s"Unsupported transaction type ($typeId)"))
        }
    }
  }

  // Used for swagger
  case class Tx(timestamp: Option[Long],
                senderKeyType: Option[String],
                senderPublicKey: Option[String],
                sponsorKeyType: Option[String],
                sponsorPublicKey: Option[String],
               ) extends TxRequest {
    override type TransactionT = Transaction

    override protected def sign(tx: Transaction, signer: PrivateKeyAccount): Transaction = ???
    override protected def toTxFrom(sender: PublicKeyAccount, sponsor: Option[PublicKeyAccount], time: Option[Time]): Either[ValidationError, Transaction] = ???
  }
}

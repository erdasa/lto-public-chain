package com.ltonetwork.state.diffs.smart.predef

import com.ltonetwork.state._
import com.ltonetwork.{NoShrink, TransactionGen}
import org.scalacheck.Gen
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import org.scalatest.matchers.should.Matchers
import org.scalatest.propspec.AnyPropSpec
import com.ltonetwork.account.Address
import com.ltonetwork.transaction.Transaction
import play.api.libs.json.Json // For string escapes.

class TransactionBindingsTest extends AnyPropSpec with ScalaCheckDrivenPropertyChecks with Matchers with TransactionGen with NoShrink {
  def provenPart(t: Transaction): String = {
    def pg(i: Int) = s"let proof$i = t.proofs[$i] == base58'${t.proofs.proofs.applyOrElse(i, (_: Int) => ByteStr.empty).base58}'"
    s"""
       |   let id = t.id == base58'${t.id().base58}'
       |   let fee = t.fee == ${t.fee}
       |   let timestamp = t.timestamp == ${t.timestamp}
       |   let bodyBytes = t.bodyBytes == base64'${ByteStr(t.bodyBytes.apply()).base64}'
       |   let sender = t.sender == addressFromPublicKey(base58'${ByteStr(t.sender.publicKey).base58}')
       |   let senderPublicKey = t.senderPublicKey == base58'${ByteStr(t.sender.publicKey).base58}'
       |   let version = t.version == ${t.version}
       |   ${Range(0, 8).map(pg).mkString("\n")}
     """.stripMargin
  }

  val assertProvenPart =
    "id && fee && timestamp && sender && senderPublicKey && proof0 && proof1 && proof2 && proof3 && proof4 && proof5 && proof6 && proof7 && bodyBytes && version"

  property("TransferTransaction binding") {
    forAll(Gen.oneOf(transferV1Gen, transferV2Gen)) { t =>
      // `version`  is not properly bound yet

      val result = runScript[Boolean](
        s"""
           |match tx {
           | case t : TransferTransaction  =>
           |   ${provenPart(t)}
           |   let amount = t.amount == ${t.amount}
           |   let recipient = t.recipient.bytes == base58'${t.recipient.cast[Address].map(_.bytes.base58).getOrElse("")}'
           |    let attachment = t.attachment == base58'${ByteStr(t.attachment).base58}'
           |   $assertProvenPart && amount && recipient && attachment
           | case other => throw()
           | }
           |""".stripMargin,
        t,
        'T'
      )
      result shouldBe Right(true)
    }
  }

  property("LeaseTransaction binding") {
    forAll(leaseGen) { t =>
      val result = runScript[Boolean](
        s"""
          |match tx {
          | case t : LeaseTransaction =>
          |   ${provenPart(t)}
          |   let amount = t.amount == ${t.amount}
          |   let recipient = t.recipient.bytes == base58'${t.recipient.cast[Address].map(_.bytes.base58).getOrElse("")}'
          |   $assertProvenPart && amount && recipient
          | case other => throw()
          | }
          |""".stripMargin,
        t,
        'T'
      )
      result shouldBe Right(true)
    }
  }

  property("LeaseCancelTransaction binding") {
    forAll(cancelLeaseGen) { t =>
      val result = runScript[Boolean](
        s"""
          |match tx {
          | case t : LeaseCancelTransaction =>
          |   ${provenPart(t)}
          |   let leaseId = t.leaseId == base58'${t.leaseId.base58}'
          |   $assertProvenPart && leaseId
          | case other => throw()
          | }
          |""".stripMargin,
        t,
        'T'
      )
      result shouldBe Right(true)
    }
  }

  property("SetScriptTransaction binding") {
    forAll(setScriptTransactionGen) { t =>
      val result = runScript[Boolean](
        s"""
           |match tx {
           | case t : SetScriptTransaction =>
           |   ${provenPart(t)}
           |   let script = if (${t.script.isDefined}) then extract(t.script) == base64'${t.script
             .map(_.bytes().base64)
             .getOrElse("")}' else isDefined(t.script) == false
           |   $assertProvenPart && script
           | case other => throw()
           | }
           |""".stripMargin,
        t,
        'T'
      )
      result shouldBe Right(true)
    }
  }
  property("MassTransferTransaction binding") {
    forAll(massTransferGen) { t =>
      def pg(i: Int): String =
        s"""let recipient$i =
           | t.transfers[$i].recipient.bytes == base58'${t.transfers(i).address.cast[Address].map(_.bytes.base58).getOrElse("")}'
           |let amount$i = t.transfers[$i].amount == ${t.transfers(i).amount}
         """.stripMargin

      val resString =
        if (t.transfers.isEmpty) assertProvenPart
        else
          assertProvenPart + s" &&" + {
            t.transfers.indices
              .map(i => s"recipient$i && amount$i")
              .mkString(" && ")
          }

      val script = s"""
                      |match tx {
                      | case t : MassTransferTransaction =>
                      |     let transferCount = t.transferCount == ${t.transfers.length}
                      |     let totalAmount = t.totalAmount == ${t.transfers.map(_.amount).sum}
                      |     let attachment = t.attachment == base58'${ByteStr(t.attachment).base58}'
                      |     ${t.transfers.indices.map(pg).mkString("\n")}
                      |   ${provenPart(t)}
                      |   $resString && transferCount && totalAmount && attachment
                      | case other => throw()
                      | }
                      |""".stripMargin

      val result = runScript[Boolean](
        script,
        t,
        'T'
      )
      result shouldBe Right(true)
    }
  }

}

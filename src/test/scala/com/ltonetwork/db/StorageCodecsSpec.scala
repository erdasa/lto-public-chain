package com.ltonetwork.db

import com.ltonetwork.network.{BlockCheckpoint, Checkpoint}
import com.ltonetwork.state.EitherExt2
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import org.scalatest.matchers.should.Matchers
import org.scalatest.freespec.AnyFreeSpec

import scala.util.Random

class StorageCodecsSpec extends AnyFreeSpec with Matchers with ScalaCheckDrivenPropertyChecks {
  private val signatureLength = 64
  private val signature = Array.fill(signatureLength) {
    0.toByte
  }

  "Empty Checkpoint round trip" in {
    Random.nextBytes(signature)
    val c1    = Checkpoint(Seq.empty, signature)
    val bytes = CheckpointCodec.encode(c1)
    val r     = CheckpointCodec.decode(bytes)
    r.isRight shouldBe true
    val c2 = r.explicitGet().value
    c2.signature.sameElements(c1.signature) shouldBe true
    c2.items.length shouldBe 0
  }

  "Non-empty Checkpoint round trip" in {
    Random.nextBytes(signature)
    val c1    = Checkpoint(Seq(BlockCheckpoint(10, signature), BlockCheckpoint(20, signature)), signature)
    val bytes = CheckpointCodec.encode(c1)
    val r     = CheckpointCodec.decode(bytes)
    r.isRight shouldBe true
    val c2 = r.explicitGet().value
    c2.signature.sameElements(c1.signature) shouldBe true
    c2.items.length shouldBe c1.items.length
    c2.items.head.signature.sameElements(c1.items.head.signature) shouldBe true
    c2.items.head.height shouldBe c1.items.head.height
    c2.items.last.signature.sameElements(c1.items.last.signature) shouldBe true
    c2.items.last.height shouldBe c1.items.last.height
  }

  "Broken bytes should return left" in {
    Random.nextBytes(signature)
    val c1    = Checkpoint(Seq(BlockCheckpoint(1, signature), BlockCheckpoint(2, signature), BlockCheckpoint(3, signature)), signature)
    val bytes = CheckpointCodec.encode(c1)

    val r1 = CheckpointCodec.decode(bytes.take(bytes.length - 2))
    r1.isLeft shouldBe true

    val r2 = CheckpointCodec.decode(bytes.slice(2, bytes.length))
    r2.isLeft shouldBe true
  }

  "TupleCodec" in {
    val codec = Tuple2Codec[String, Short](StringCodec, ShortCodec)
    val x     = ("foo", 10: Short)
    codec.decode(codec.encode(x)).explicitGet().value shouldBe x
  }

  "OptionCodec" - {
    val codec = OptionCodec[String](StringCodec)

    "None" in {
      codec.decode(codec.encode(None)).explicitGet().value shouldBe None
    }

    "Some(x)" in {
      val x = Option("foo")
      codec.decode(codec.encode(x)).explicitGet().value shouldBe x
    }
  }

}

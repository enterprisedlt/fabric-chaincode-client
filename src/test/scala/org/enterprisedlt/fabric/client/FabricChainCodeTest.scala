package org.enterprisedlt.fabric.client

import java.util.concurrent.CompletableFuture

import com.google.protobuf.ByteString
import org.enterprisedlt.general.codecs.GsonCodec
import org.enterprisedlt.general.gson._
import org.enterprisedlt.spec.{ContractOperation, ContractResult, OperationType}
import org.hyperledger.fabric.protos.peer.FabricProposalResponse
import org.hyperledger.fabric.sdk.transaction.TransactionContext
import org.hyperledger.fabric.sdk._
import org.junit.runner.RunWith
import org.mockito.Mockito._
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

import scala.collection.JavaConverters._

/**
 * @author Andrew Pudovikov
 */
@RunWith(classOf[JUnitRunner])
class FabricChainCodeTest extends FunSuite {
    private val chaincodeId: ChaincodeID = ChaincodeID.newBuilder()
      .setName("channel")
      .setVersion("1.0")
      .setPath("/dev/null").build()
    private val gsonCodec = GsonCodec()
    private val typedCodec = GsonCodec(gsonOptions = _.encodeTypes(typeFieldName = "#TYPE#", typeNamesResolver = NamesResolver))
    private val NamesResolver = new TypeNameResolver() {
        override def resolveTypeByName(name: String): Class[_] = if ("dummy" equals name) classOf[Dummy] else throw new IllegalStateException(s"Unexpected class name: $name")

        override def resolveNameByType(clazz: Class[_]): String = "dummy"
    }

    trait TestContractSpec {
        @ContractOperation(OperationType.Invoke)
        def testReturnUnit(tokenType: Int): ContractResult[Unit]

        @ContractOperation(OperationType.Invoke)
        def testReturnDummy(tokenType: Int): ContractResult[Dummy]

    }

    test("test return Unit with gson codec") {
        val client = mock(classOf[HFClient])
        val usr = mock(classOf[User])
        when(client.getUserContext).thenReturn(usr)
        val channel = mock(classOf[Channel])
        val txCtx = mock(classOf[TransactionContext])
        when(txCtx.getTxID).thenReturn("12345")
        when(txCtx.getChannelID).thenReturn("channel")

        val propResponse = ProposalResponseFactory.newProposalResponse(txCtx, ChaincodeResponse.Status.SUCCESS.getStatus, "")
        val tranProReq = TransactionProposalRequest.newInstance(usr)
        val ccResult = gsonCodec.encode(())
        val protoResponse = FabricProposalResponse.Response.newBuilder()
          .setPayload(ByteString.copyFrom(ccResult))
          .build()

        val proposalResponse = FabricProposalResponse.ProposalResponse.newBuilder()
          .setPayload(ByteString.copyFrom(ccResult))
          .setResponse(protoResponse)
          .build()

        propResponse.setProposalResponse(proposalResponse)
        val responses = List(propResponse).asJava
        val cc: TestContractSpec = new FabricChainCode(client, channel, chaincodeId, gsonCodec).as[TestContractSpec]

        when(client.newTransactionProposalRequest()).thenReturn(tranProReq)
        when(channel.sendTransactionProposal(tranProReq)).thenReturn(responses)
        when(channel.sendTransaction(responses, usr))
          .thenReturn(CompletableFuture.completedFuture(null.asInstanceOf[BlockEvent#TransactionEvent]))

        assert(cc.testReturnUnit(1) == Right(()))
    }

    test("test return Unit with typed codec") {
        val client = mock(classOf[HFClient])
        val usr = mock(classOf[User])
        when(client.getUserContext).thenReturn(usr)
        val channel = mock(classOf[Channel])
        val txCtx = mock(classOf[TransactionContext])
        when(txCtx.getTxID).thenReturn("12345")
        when(txCtx.getChannelID).thenReturn("channel")

        val propResponse = ProposalResponseFactory.newProposalResponse(txCtx, ChaincodeResponse.Status.SUCCESS.getStatus, "")
        val tranProReq = TransactionProposalRequest.newInstance(usr)
        val ccResult = typedCodec.encode(())
        val protoResponse = FabricProposalResponse.Response.newBuilder()
          .setPayload(ByteString.copyFrom(ccResult))
          .build()

        val proposalResponse = FabricProposalResponse.ProposalResponse.newBuilder()
          .setPayload(ByteString.copyFrom(ccResult))
          .setResponse(protoResponse)
          .build()

        propResponse.setProposalResponse(proposalResponse)
        val responses = List(propResponse).asJava
        val cc: TestContractSpec = new FabricChainCode(client, channel, chaincodeId, typedCodec).as[TestContractSpec]

        when(client.newTransactionProposalRequest()).thenReturn(tranProReq)
        when(channel.sendTransactionProposal(tranProReq)).thenReturn(responses)
        when(channel.sendTransaction(responses, usr))
          .thenReturn(CompletableFuture.completedFuture(null.asInstanceOf[BlockEvent#TransactionEvent]))

        assert(cc.testReturnUnit(1) == Right(()))
    }

    test("test return Dummy asset with typed codec") {
        val client = mock(classOf[HFClient])
        val usr = mock(classOf[User])
        when(client.getUserContext).thenReturn(usr)
        val channel = mock(classOf[Channel])
        val txCtx = mock(classOf[TransactionContext])
        when(txCtx.getTxID).thenReturn("12345")
        when(txCtx.getChannelID).thenReturn("channel")

        val propResponse = ProposalResponseFactory.newProposalResponse(txCtx, ChaincodeResponse.Status.SUCCESS.getStatus, "")
        val tranProReq = TransactionProposalRequest.newInstance(usr)
        val dummy = Dummy("x","y")
        val ccResult = typedCodec.encode(dummy)
        val protoResponse = FabricProposalResponse.Response.newBuilder()
          .setPayload(ByteString.copyFrom(ccResult))
          .build()

        val proposalResponse = FabricProposalResponse.ProposalResponse.newBuilder()
          .setPayload(ByteString.copyFrom(ccResult))
          .setResponse(protoResponse)
          .build()

        propResponse.setProposalResponse(proposalResponse)
        val responses = List(propResponse).asJava
        val cc: TestContractSpec = new FabricChainCode(client, channel, chaincodeId, typedCodec).as[TestContractSpec]

        when(client.newTransactionProposalRequest()).thenReturn(tranProReq)
        when(channel.sendTransactionProposal(tranProReq)).thenReturn(responses)
        when(channel.sendTransaction(responses, usr))
          .thenReturn(CompletableFuture.completedFuture(null.asInstanceOf[BlockEvent#TransactionEvent]))

        assert(cc.testReturnDummy(1) == Right(Dummy("x","y")))
    }
}

case class Dummy(
    name: String,
    value: String
)
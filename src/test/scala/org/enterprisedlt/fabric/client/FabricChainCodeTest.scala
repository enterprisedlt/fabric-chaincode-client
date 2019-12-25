package org.enterprisedlt.fabric.client

import java.util
import java.util.concurrent.CompletableFuture

import com.google.protobuf.ByteString
import org.enterprisedlt.general.codecs.GsonCodec
import org.enterprisedlt.spec.{ContractOperation, ContractResult, OperationType}
import org.hyperledger.fabric.protos.peer.FabricProposalResponse
import org.hyperledger.fabric.sdk.transaction.TransactionContext
import org.hyperledger.fabric.sdk.{BlockEvent, ChaincodeID, ChaincodeResponse, Channel, HFClient, ProposalResponse, ProposalResponseFactory, SDKUtils, TransactionProposalRequest, User}
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.mockito.Mockito._
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
    private val codec = GsonCodec()

    //        private val NamesResolver = new TypeNameResolver() {
    //            override def resolveTypeByName(name: String): Class[_] = if ("dummy" equals name) classOf[Dummy] else throw new IllegalStateException(s"Unexpected class name: $name")
    //
    //            override def resolveNameByType(clazz: Class[_]): String = "dummy"
    //        }


    private val fabricChainCode = mock(classOf[FabricChainCode])

    trait TestContractSpec {
        @ContractOperation(OperationType.Invoke)
        def testPut(tokenType: Int): ContractResult[Unit]

    }


    test("test return Unit") {
        val client = mock(classOf[HFClient])
        val usr = mock(classOf[User])
        when(client.getUserContext).thenReturn(usr)
        val channel = mock(classOf[Channel])
        val txCtx = mock(classOf[TransactionContext])
        when(txCtx.getTxID).thenReturn("12345")
        when(txCtx.getChannelID).thenReturn("channel")

        val propResponse = ProposalResponseFactory.newProposalResponse(txCtx, ChaincodeResponse.Status.SUCCESS.getStatus, "")
        val tranProReq = TransactionProposalRequest.newInstance(usr)
        val ccResult = codec.encode(())
        val protoResponse = FabricProposalResponse.Response.newBuilder()
          .setPayload(ByteString.copyFrom(ccResult))
          .build()

        val proposalResponse = FabricProposalResponse.ProposalResponse.newBuilder()
          .setPayload(ByteString.copyFrom(ccResult))
          .setResponse(protoResponse)
          .build()

        propResponse.setProposalResponse(proposalResponse)
        val responses = List(propResponse).asJava
        val cc: TestContractSpec = new FabricChainCode(client, channel, chaincodeId, codec).as[TestContractSpec]

        when(client.newTransactionProposalRequest()).thenReturn(tranProReq)
        when(channel.sendTransactionProposal(tranProReq)).thenReturn(responses)
        when(channel.sendTransaction(responses, usr))
          .thenReturn(CompletableFuture.completedFuture(null.asInstanceOf[BlockEvent#TransactionEvent]))

        assert(cc.testPut(1) == Right(()))
    }

    test("test return Dummy") {
        val client = mock(classOf[HFClient])
        val usr = mock(classOf[User])
        when(client.getUserContext).thenReturn(usr)
        val channel = mock(classOf[Channel])
        val txCtx = mock(classOf[TransactionContext])
        when(txCtx.getTxID).thenReturn("12345")
        when(txCtx.getChannelID).thenReturn("channel")

        val propResponse = ProposalResponseFactory.newProposalResponse(txCtx, ChaincodeResponse.Status.SUCCESS.getStatus, "")
        val tranProReq = TransactionProposalRequest.newInstance(usr)
        val ccResult = codec.encode(())
        val protoResponse = FabricProposalResponse.Response.newBuilder()
          .setPayload(ByteString.copyFrom(ccResult))
          .build()

        val proposalResponse = FabricProposalResponse.ProposalResponse.newBuilder()
          .setPayload(ByteString.copyFrom(ccResult))
          .setResponse(protoResponse)
          .build()

        propResponse.setProposalResponse(proposalResponse)
        val responses = List(propResponse).asJava
        val cc: TestContractSpec = new FabricChainCode(client, channel, chaincodeId, codec).as[TestContractSpec]

        when(client.newTransactionProposalRequest()).thenReturn(tranProReq)
        when(channel.sendTransactionProposal(tranProReq)).thenReturn(responses)
        when(channel.sendTransaction(responses, usr))
          .thenReturn(CompletableFuture.completedFuture(null.asInstanceOf[BlockEvent#TransactionEvent]))

        assert(cc.testPut(1) == Right(()))
    }

}
package org.enterprisedlt.fabric.client

import java.util.concurrent.CompletableFuture

import com.google.protobuf.ByteString
import org.enterprisedlt.fabric.client.configuration.OSNConfig
import org.enterprisedlt.general.codecs.GsonCodec
import org.enterprisedlt.general.gson._
import org.enterprisedlt.spec.{BinaryCodec, ContractOperation, ContractResult, OperationType}
import org.hyperledger.fabric.protos.peer.FabricProposalResponse
import org.hyperledger.fabric.sdk._
import org.hyperledger.fabric.sdk.transaction.TransactionContext
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
    private val chainCodeId: ChaincodeID =
        ChaincodeID.newBuilder()
          .setName("channel")
          .setVersion("1.0")
          .setPath("/dev/null")
          .build()

    private val vanillaGSONCodec = GsonCodec()

    private val NamesResolver = new TypeNameResolver() {
        override def resolveTypeByName(name: String): Class[_] = if ("dummy" equals name) classOf[Dummy] else throw new IllegalStateException(s"Unexpected class name: $name")

        override def resolveNameByType(clazz: Class[_]): String = "dummy"
    }
    private val typedGSONCodec = GsonCodec(gsonOptions = _.encodeTypes(typeFieldName = "#TYPE#", typeNamesResolver = NamesResolver))

    private val discoveryForEndorsement = false
    private val discoveryForOrdering = false
    private val bootstrapOrderers: Array[OSNConfig] = Array(
        OSNConfig("osn1", "osn1.org1.example.com")
    )

    trait TestContractSpec {
        @ContractOperation(OperationType.Invoke)
        def testReturnUnit(arg: Int): ContractResult[Unit]

        @ContractOperation(OperationType.Invoke)
        def testReturnDummy(arg: Int): ContractResult[Dummy]

        @ContractOperation(OperationType.Invoke)
        def testInvokeReturnLong(arg: Int): Either[String, Long]

    }

    test("should work with Unit as return type [vanilla GSON]") {
        val expectedResult: Unit = ()
        val cc: TestContractSpec = mockChainCodeForInvoke(vanillaGSONCodec, expectedResult).as[TestContractSpec]
        assert(cc.testReturnUnit(1) == Right(expectedResult))
    }

    test("should work with Unit as return type [typed GSON]") {
        val expectedResult: Unit = ()
        val cc: TestContractSpec = mockChainCodeForInvoke(typedGSONCodec, expectedResult).as[TestContractSpec]
        assert(cc.testReturnUnit(1) == Right(expectedResult))
    }

    test("should work with case class as return type [typed GSON]") {
        val expectedResult = Dummy("x", "y")
        val cc: TestContractSpec = mockChainCodeForInvoke(typedGSONCodec, expectedResult).as[TestContractSpec]
        assert(cc.testReturnDummy(1) == Right(expectedResult))
    }

    test("should work with Long as return type [vanilla GSON]") {
        val expectedResult: Long = 10000000L
        val cc: TestContractSpec = mockChainCodeForInvoke(vanillaGSONCodec, expectedResult).as[TestContractSpec]
        assert(cc.testInvokeReturnLong(1) == Right(expectedResult))
    }

    test("should work with Long as return type [typed GSON]") {
        val expectedResult: Long = 10000000L
        val cc: TestContractSpec = mockChainCodeForInvoke(typedGSONCodec, expectedResult).as[TestContractSpec]
        assert(cc.testInvokeReturnLong(1) == Right(expectedResult))
    }


    private def mockChainCodeForInvoke(codec: BinaryCodec, result: Any): FabricChainCode = {
        val client = mock(classOf[HFClient])
        val usr = mock(classOf[User])
        when(client.getUserContext).thenReturn(usr)
        val channel = mock(classOf[Channel])
        val txCtx = mock(classOf[TransactionContext])
        when(txCtx.getTxID).thenReturn("12345")
        when(txCtx.getChannelID).thenReturn("channel")

        val propResponse = ProposalResponseFactory.newProposalResponse(txCtx, ChaincodeResponse.Status.SUCCESS.getStatus, "")
        val tranProReq = TransactionProposalRequest.newInstance(usr)
        val ccResult = codec.encode(result)
        val protoResponse = FabricProposalResponse.Response.newBuilder()
          .setPayload(ByteString.copyFrom(ccResult))
          .build()

        val proposalResponse = FabricProposalResponse.ProposalResponse.newBuilder()
          .setPayload(ByteString.copyFrom(ccResult))
          .setResponse(protoResponse)
          .build()

        propResponse.setProposalResponse(proposalResponse)
        val responses = List(propResponse).asJava

        when(client.newTransactionProposalRequest()).thenReturn(tranProReq)
        when(channel.sendTransactionProposal(tranProReq)).thenReturn(responses)
        when(channel.sendTransaction(responses, usr))
          .thenReturn(CompletableFuture.completedFuture(null.asInstanceOf[BlockEvent#TransactionEvent]))

        new FabricChainCode(client, channel, chainCodeId, codec, bootstrapOrderers, discoveryForEndorsement, discoveryForOrdering)
    }
}

case class Dummy(
    name: String,
    value: String
)

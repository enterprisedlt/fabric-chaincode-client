package org.enterprisedlt.fabric.client

import org.enterprisedlt.general.codecs.GsonCodec
import org.enterprisedlt.spec.{BinaryCodec, ContractOperation, ContractResult, OperationType}
import org.hyperledger.fabric.sdk.{ChaincodeID, Channel, HFClient, TransactionProposalRequest, User}
import org.scalatest.FunSuite
import org.mockito.Mockito._


/**
 * @author Andrew Pudovikov
 */
class FabricChainCodeTest extends FunSuite {
    private val chaincodeId = ChaincodeID.newBuilder().setName("d").build()
    private val codec = GsonCodec()

    private val NamesResolver = new TypeNameResolver() {
        override def resolveTypeByName(name: String): Class[_] = if ("dummy" equals name) classOf[Dummy] else throw new IllegalStateException(s"Unexpected class name: $name")

        override def resolveNameByType(clazz: Class[_]): String = "dummy"
    }


    private val fabricChainCode = mock(classOf[FabricChainCode])

    trait TestContractSpec {
        @ContractOperation(OperationType.Invoke)
        def testPut(tokenType: Int): ContractResult[Unit]

    }


    test("testAs") {
        val client = mock(classOf[HFClient])
        val channel = mock(classOf[Channel])
        val usr = mock(classOf[User])
        val tpr = TransactionProposalRequest.newInstance(usr)
        when(client.newTransactionProposalRequest()).thenReturn(tpr)
        when(client.getUserContext).thenReturn(usr)

        val cc = new FabricChainCode(client, channel, chaincodeId, codec).as[TestContractSpec]
        assert(cc.testPut(1) == Right(()))

    }

}


private val NamesResolver = new TypeNameResolver() {
    override def resolveTypeByName(name: String): Class[_] = if ("dummy" equals name) classOf[Dummy] else throw new IllegalStateException(s"Unexpected class name: $name")

    override def resolveNameByType(clazz: Class[_]): String = "dummy"
}

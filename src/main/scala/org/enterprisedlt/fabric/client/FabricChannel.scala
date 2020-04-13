package org.enterprisedlt.fabric.client

import org.enterprisedlt.spec.BinaryCodec
import org.hyperledger.fabric.sdk.{BlockListener, ChaincodeID, Channel, HFClient}

import scala.util.Try

/**
 * @author Alexey Polubelov
 */
class FabricChannel(
    fabricClient: HFClient,
    fabricChannel: Channel
) {
    def getChainCode(name: String, codec: BinaryCodec, chaincodeServiceDiscovery: Boolean = false /* TODO: , endorsementTimeout: Int = */): FabricChainCode =
        new FabricChainCode(
            fabricClient,
            fabricChannel,
            ChaincodeID.newBuilder().setName(name).build(),
            codec,
            chaincodeServiceDiscovery
        )

    //=========================================================================
    def setupBlockListener(listener: BlockListener): Try[String] = Try {
        fabricChannel.registerBlockListener(listener)
    }

}

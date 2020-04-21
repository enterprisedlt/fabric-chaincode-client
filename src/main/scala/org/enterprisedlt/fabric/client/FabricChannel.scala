package org.enterprisedlt.fabric.client

import org.enterprisedlt.spec.BinaryCodec
import org.hyperledger.fabric.sdk._

import scala.util.Try

/**
 * @author Alexey Polubelov
 */
class FabricChannel(
    fabricClient: HFClient,
    fabricChannel: Channel,
    bootstrapOrderers: java.util.Collection[Orderer]
) {
    def getChainCode(name: String, codec: BinaryCodec, discoveryForEndorsement: Boolean = false, discoveryForOrdering: Boolean = false /* TODO: , endorsementTimeout: Int = */): FabricChainCode =
        new FabricChainCode(
            fabricClient,
            fabricChannel,
            ChaincodeID.newBuilder().setName(name).build(),
            codec,
            bootstrapOrderers,
            discoveryForEndorsement,
            discoveryForOrdering
        )

    //=========================================================================
    def setupBlockListener(listener: BlockListener): Try[String] = Try {
        fabricChannel.registerBlockListener(listener)
    }

}

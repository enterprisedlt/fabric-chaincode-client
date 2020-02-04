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
    def getChainCode(name: String, codec: BinaryCodec /* TODO: , endorsementTimeout: Int = */): FabricChainCode =
        new FabricChainCode(
            fabricClient,
            fabricChannel,
            ChaincodeID.newBuilder().setName(name).build(),
            codec
        )

    //=========================================================================
    def setupBlockListener(listener: BlockListener): Either[String, String] = Try {
        fabricChannel.registerBlockListener(listener)
    }.toEither.left.map(_.getMessage)
}

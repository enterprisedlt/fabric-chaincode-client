package org.enterprisedlt.fabric.client

import org.enterprisedlt.spec.BinaryCodec
import org.hyperledger.fabric.protos.common.Common.Block
import org.hyperledger.fabric.sdk._
import org.slf4j.LoggerFactory

import scala.util.Try

/**
 * @author Alexey Polubelov
 */
class FabricChannel(
                       fabricClient: HFClient,
                       fabricChannel: Channel,
                       bootstrapOrderers: java.util.Collection[Orderer]
                   ) {

    private val logger = LoggerFactory.getLogger(this.getClass)

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


    def getLatestBlock: Either[String, Block] = Try {
        val getConfigurationBlockMethod = classOf[Channel].getDeclaredMethod("getLatestBlock", classOf[Orderer])
        getConfigurationBlockMethod.setAccessible(true)
        getConfigurationBlockMethod.invoke(fabricChannel, fabricChannel.getOrderers.toArray.head)
          .asInstanceOf[Block]
    }.toEither.left.map { err =>
        val msg = s"Error: ${err.getMessage}"
        logger.error(msg, err)
        msg
    }


    def getBlockByNumber(blockNumber: Long): Either[String, BlockInfo] =
        Try(fabricChannel.queryBlockByNumber(blockNumber))
          .toEither.left.map { err =>
            val msg = s"Error: ${err.getMessage}"
            logger.error(msg, err)
            msg
        }


}

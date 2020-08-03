package org.enterprisedlt.fabric.client

import java.util

import org.enterprisedlt.fabric.client.configuration.PeerConfig
import org.enterprisedlt.spec.BinaryCodec
import org.hyperledger.fabric.protos.common.Common.Block
import org.hyperledger.fabric.sdk.Channel.PeerOptions.createPeerOptions
import org.hyperledger.fabric.sdk._
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters.asJavaCollection
import scala.util.Try

/**
 * @author Alexey Polubelov
 */
class FabricChannel(
    fabricClient: FabricClient,
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


    def addPeer(config: PeerConfig): Either[String, String] = {
        Try {
            if (config.peerRoles.isEmpty) {
                fabricChannel.addPeer(fabricClient.mkPeer( config))
            } else {
                val peerRolesSet = util.EnumSet
                  .copyOf(
                      asJavaCollection(config.peerRoles)
                  )
                val peerOptions = createPeerOptions
                  .setPeerRoles(peerRolesSet)
                fabricChannel.addPeer(fabricClient.mkPeer( config), peerOptions)
            }
        }.toEither match {
            case Right(_) => Right("Success")
            case Left(err) =>
                val msg = s"Error: ${err.getMessage}"
                logger.error(msg, err)
                Left(msg)
        }
    }


}

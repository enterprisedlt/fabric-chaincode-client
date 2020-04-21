package org.enterprisedlt.fabric.client

import java.util

import org.enterprisedlt.fabric.client.configuration._
import org.hyperledger.fabric.sdk.Channel.PeerOptions.createPeerOptions
import org.hyperledger.fabric.sdk.security.CryptoSuite
import org.hyperledger.fabric.sdk.{HFClient, User}
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._

/**
 * @author Alexey Polubelov
 */
class FabricClient(
    user: User,
    network: Network
) {
    private val logger = LoggerFactory.getLogger(this.getClass)
    private val cryptoSuite = CryptoSuite.Factory.getCryptoSuite()
    private val fabricClient: HFClient = getHFClient(user)

    private def getHFClient(user: User): HFClient = {
        val client = HFClient.createNewInstance()
        client.setCryptoSuite(cryptoSuite)
        client.setUserContext(user)
        client
    }

    def channel(name: String): FabricChannel = {
        val channel = fabricClient.newChannel(name)
        network.ordering.foreach { config =>
            channel.addOrderer(Util.mkOSN(fabricClient, config))
        }
        network.peers.foreach { config =>
            if (config.peerRoles.isEmpty) {
                channel.addPeer(Util.mkPeer(fabricClient, config))
            } else {
                val peerRolesSet = util.EnumSet
                  .copyOf(
                      asJavaCollection(config.peerRoles)
                  )
                val peerOptions = createPeerOptions
                  .setPeerRoles(peerRolesSet)
                channel.addPeer(Util.mkPeer(fabricClient, config), peerOptions)
            }
        }
        channel.initialize()
        new FabricChannel(fabricClient, channel, network.ordering)
    }

}

package org.enterprisedlt.fabric.client

import java.util
import java.util.Properties

import org.enterprisedlt.fabric.client.configuration._
import org.hyperledger.fabric.sdk.Channel.PeerOptions.createPeerOptions
import org.hyperledger.fabric.sdk.Peer.PeerRole
import org.hyperledger.fabric.sdk.security.CryptoSuite
import org.hyperledger.fabric.sdk.{HFClient, Orderer, Peer, User}
import org.slf4j.LoggerFactory

/**
 * @author Alexey Polubelov
 */
class FabricClient(
    user: User,
    network: Network,
    serviceDiscovery: Boolean
) {
    private val logger = LoggerFactory.getLogger(this.getClass)
    private val cryptoSuite = CryptoSuite.Factory.getCryptoSuite()
    private val fabricClient = getHFClient(user)

    private def getHFClient(user: User): HFClient = {
        val client = HFClient.createNewInstance()
        client.setCryptoSuite(cryptoSuite)
        client.setUserContext(user)
        client
    }

    def channel(name: String): FabricChannel = {
        val channel = fabricClient.newChannel(name)
        network.ordering.foreach { config =>
            channel.addOrderer(mkOSN(config))
        }
        network.peers.foreach { config =>
            if (!serviceDiscovery) {
                channel.addPeer(mkPeer(config))
            } else {
                val peerOptions = createPeerOptions
                  .setPeerRoles(util.EnumSet.of(
                      PeerRole.SERVICE_DISCOVERY,
                      PeerRole.LEDGER_QUERY,
                      PeerRole.EVENT_SOURCE,
                      PeerRole.CHAINCODE_QUERY))
                channel.addPeer(mkPeer(config), peerOptions)
            }
        }
        channel.initialize()
        new FabricChannel(fabricClient, channel, serviceDiscovery)
    }

    //=========================================================================
    private def mkPeer(config: PeerConfig): Peer = {
        config.setting match {
            case Plain =>
                fabricClient.newPeer(config.name, config.address)
            case TLS(path) =>
                val properties = new Properties()
                properties.put("pemFile", path)
                fabricClient.newPeer(config.name, config.address, properties)
        }
    }

    //=========================================================================
    private def mkOSN(config: OSNConfig): Orderer = {
        config.setting match {
            case Plain =>
                fabricClient.newOrderer(config.name, config.address)
            case TLS(path) =>
                val properties = new Properties()
                properties.put("pemFile", path)
                fabricClient.newOrderer(config.name, config.address, properties)
        }
    }
}

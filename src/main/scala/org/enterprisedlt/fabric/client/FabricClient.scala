package org.enterprisedlt.fabric.client

import java.util.Properties

import org.enterprisedlt.fabric.client.configuration._
import org.hyperledger.fabric.sdk.security.CryptoSuite
import org.hyperledger.fabric.sdk.{HFClient, Orderer, Peer, User}
import org.slf4j.LoggerFactory

/**
 * @author Alexey Polubelov
 */
class FabricClient(
    user: User,
    network: Network
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
            channel.addPeer(mkPeer(config))
        }
        channel.initialize()
        new FabricChannel(fabricClient, channel)
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
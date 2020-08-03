package org.enterprisedlt.fabric.client

import java.util.Properties

import org.enterprisedlt.fabric.client.configuration.{OSNConfig, PeerConfig, Plain, TLS}
import org.hyperledger.fabric.sdk.{HFClient, Orderer, Peer}

/**
 * @author Maxim Fedin
 */
object Util {

    def mkPeer(fabricClient: HFClient, config: PeerConfig): Peer = {
        config.setting match {
            case Plain =>
                fabricClient.newPeer(config.name, config.address)
            case TLS(path) =>
                val properties = new Properties()
                properties.put("pemFile", path)
                fabricClient.newPeer(config.name, config.address, properties)
        }
    }

    def mkOSN(fabricClient: HFClient, config: OSNConfig): Orderer = {
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

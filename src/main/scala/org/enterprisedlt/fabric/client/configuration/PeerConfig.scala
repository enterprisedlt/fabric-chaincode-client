package org.enterprisedlt.fabric.client.configuration

/**
 * @author Alexey Polubelov
 */
case class PeerConfig(
    name: String,
    address: String,
    peerServiceDiscovery: Boolean,
    setting: ConnectionSettings = Plain
)

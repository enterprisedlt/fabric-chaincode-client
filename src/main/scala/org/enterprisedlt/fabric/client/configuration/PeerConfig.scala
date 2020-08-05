package org.enterprisedlt.fabric.client.configuration

import org.hyperledger.fabric.sdk.Peer.PeerRole

/**
 * @author Alexey Polubelov
 */
case class PeerConfig(
    name: String,
    host: String,
    port: Int,
    setting: ConnectionSettings = Plain,
    peerRoles: Array[PeerRole] = Array.empty
)

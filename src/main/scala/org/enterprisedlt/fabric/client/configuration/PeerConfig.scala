package org.enterprisedlt.fabric.client.configuration

import org.hyperledger.fabric.sdk.Peer.PeerRole

/**
 * @author Alexey Polubelov
 */
case class PeerConfig(
    name: String,
    address: String,
    peerRoles: Array[PeerRole] = Array.empty,
    setting: ConnectionSettings = Plain
)

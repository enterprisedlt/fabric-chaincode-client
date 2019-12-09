package org.enterprisedlt.fabric.client.configuration

/**
 * @author Alexey Polubelov
 */
case class Network(
    ordering: Array[OSNConfig],
    peers: Array[PeerConfig]
)

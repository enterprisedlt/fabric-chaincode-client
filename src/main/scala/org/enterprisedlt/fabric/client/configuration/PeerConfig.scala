package org.enterprisedlt.fabric.client.configuration

/**
 * @author Alexey Polubelov
 */
case class PeerConfig(
    name: String,
    address: String,
    setting: ConnectionSettings = Plain
)

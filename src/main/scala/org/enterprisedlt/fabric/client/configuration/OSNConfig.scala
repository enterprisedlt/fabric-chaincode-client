package org.enterprisedlt.fabric.client.configuration

/**
 * @author Alexey Polubelov
 */
case class OSNConfig(
    name: String,
    address: String,
    setting: ConnectionSettings = Plain
)

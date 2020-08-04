package org.enterprisedlt.fabric.client.configuration

/**
 * @author Alexey Polubelov
 */
case class OSNConfig(
    name: String,
    host: String,
    port: Int,
    setting: ConnectionSettings = Plain
)

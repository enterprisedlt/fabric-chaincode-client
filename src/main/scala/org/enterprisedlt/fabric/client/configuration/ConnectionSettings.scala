package org.enterprisedlt.fabric.client.configuration

/**
 * @author Alexey Polubelov
 */

sealed trait ConnectionSettings

case object Plain extends ConnectionSettings

case class TLSPath(
    certificatePath: String,
    hostnameOverride: Boolean = false
) extends ConnectionSettings

case class TLSPem(
    certificatePem: Array[Byte],
    hostnameOverride: Boolean = false
) extends ConnectionSettings

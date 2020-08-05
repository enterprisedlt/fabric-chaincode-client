package org.enterprisedlt.fabric.client.configuration

/**
 * @author Alexey Polubelov
 */

sealed trait ConnectionSettings

case object Plain extends ConnectionSettings

case class TLSPath(
    certificatePath: String,
    hostnameOverride: Option[String] = None
) extends ConnectionSettings

case class TLSPem(
    certificatePem: Option[Array[Byte]] = None,
    clientKeyPem: Option[Array[Byte]] = None,
    clientCertPem: Option[Array[Byte]] = None,
    hostnameOverride: Option[String] = None
) extends ConnectionSettings

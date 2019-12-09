package org.enterprisedlt.fabric.client.configuration

/**
 * @author Alexey Polubelov
 */

sealed trait ConnectionSettings

case object Plain extends ConnectionSettings

case class TLS(certificatePath: String) extends ConnectionSettings

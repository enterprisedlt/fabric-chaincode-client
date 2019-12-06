package org.enterprisedlt.client.assets

import org.hyperledger.fabric.sdk.{Channel, HFClient, User}

/**
  * @author Maxim Fedin
  */
case class FabricPlatform(
    client: HFClient,
    channel: Channel,
    user: User,
    endorsementTimeout: Int,
    operationRetryCount: Int
)

package org.enterprisedlt.fabric.client

import java.io.{File, FileReader}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import java.security.PrivateKey
import java.util.concurrent.CompletableFuture
import java.util.function.BiFunction
import java.util.{Collections, Properties}

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.enterprisedlt.client.assets.FabricPlatform
import org.enterprisedlt.spec.BinaryCodec
import org.hyperledger.fabric.sdk._
import org.hyperledger.fabric.sdk.identity.X509Enrollment
import org.hyperledger.fabric.sdk.security.CryptoSuite
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._

/**
  * @author Alexey Polubelov
  */
object FabricUtil {
    type TransactionEvent = BlockEvent#TransactionEvent
    //
    private val logger = LoggerFactory.getLogger(getClass)
    private val cryptoSuite = CryptoSuite.Factory.getCryptoSuite()

    def getHFClient(user: User): HFClient = {
        val client = HFClient.createNewInstance()
        client.setCryptoSuite(cryptoSuite)
        client.setUserContext(user)
        client
    }

    def getUser(orgName: String, mspPath: String): AppUser = {
        val signedCert = getSignedCertFromFile(mspPath)
        val privateKey = getPrivateKeyFromFile(mspPath)
        val adminEnrollment = new X509Enrollment(privateKey, signedCert)
        AppUser("user1", Collections.emptySet(), "", "", adminEnrollment, orgName)
    }

    def initChannel(client: HFClient, peerAddress: String, peerTlsCrtPath: String, ordererAddress: String, ordererTlsCrtPath: String): Channel = {
        val peerProps = new Properties()
        peerProps.put("pemFile", peerTlsCrtPath)
        val peer = client.newPeer("peer", peerAddress, peerProps)
        val ordererProps = new Properties()
        ordererProps.put("pemFile", ordererTlsCrtPath)
        val orderer = client.newOrderer("orderer", ordererAddress, ordererProps)
        val channel = client.newChannel("common")
        channel.addPeer(peer)
        channel.addOrderer(orderer)
        channel.initialize()
    }

    def getPrivateKeyFromFile(filePath: String): PrivateKey = {
        val fileName = new File(s"$filePath/keystore").listFiles(n => n.getAbsolutePath.endsWith("_sk"))(0)
        val pemReader = new FileReader(fileName)
        val pemParser = new PEMParser(pemReader)
        val pemPair = pemParser.readObject().asInstanceOf[PrivateKeyInfo]
        pemParser.close()
        pemReader.close()
        new JcaPEMKeyConverter().getPrivateKey(pemPair)
    }

    private def getSignedCertFromFile(filePath: String): String = {
        val fileName = new File(s"$filePath/signcerts").listFiles(n => n.getAbsolutePath.endsWith(".pem"))(0)
        val r = Files.readAllBytes(Paths.get(fileName.toURI))
        new String(r, StandardCharsets.UTF_8)
    }

    def invoke(client: HFClient, channel: Channel, chaincodeID: ChaincodeID, user: User, timeout: Int, function: String,  codec: BinaryCodec, args: Any*)
      (implicit transient: Option[Map[String, Array[Byte]]] = None)
    : Either[String, CompletableFuture[TransactionEvent]] = {
        val transactionProposalRequest = client.newTransactionProposalRequest()
        transactionProposalRequest.setChaincodeID(chaincodeID)
        transactionProposalRequest.setFcn(function)
        val arguments = args.map(codec.encode)
        transactionProposalRequest.setArgs(arguments: _*)
        transactionProposalRequest.setProposalWaitTime(timeout)
        transactionProposalRequest.setUserContext(user)
        transient.foreach(x => transactionProposalRequest.setTransientMap(x.asJava))
        logger.debug(s"Sending transaction proposal: $function${args.mkString("(", ",", ")")}")
        val invokePropResp = channel.sendTransactionProposal(transactionProposalRequest).asScala
        val byStatus = invokePropResp.groupBy { response => response.getStatus }
        val successful = byStatus.getOrElse(ChaincodeResponse.Status.SUCCESS, List.empty)
        val failed = byStatus.getOrElse(ChaincodeResponse.Status.FAILURE, List.empty)
        logger.debug(
            s"Received ${invokePropResp.size} transaction proposal responses. Successful: ${successful.size}. Failed: ${failed.size}"
        )
        if (failed.nonEmpty) {
            failed.foreach { errorResponse =>
                logger.error(s"Endorsement failed on [${errorResponse.getPeer.getName}] : ${errorResponse.getMessage}")
            }
            Left("Got endorsement errors.")
        } else {
            logger.debug("Successfully received transaction proposal responses.")
            val toSend = invokePropResp.asJavaCollection
            val proposalConsistencySets = SDKUtils.getProposalConsistencySets(toSend)
            if (proposalConsistencySets.size() != 1) {
                Left(s"Got inconsistent proposal responses [${proposalConsistencySets.size}]")
            } else {
                logger.debug("Sending transaction to orderer...")
                Right(channel.sendTransaction(toSend, user))
            }
        }
    }

    // with transient
    def invokeSync(platform: FabricPlatform, chaincodeID: ChaincodeID, function: String, codec: BinaryCodec, args: Any*)
      (implicit transient: Option[Map[String, Array[Byte]]] = None)
    : Either[String, Unit] = {
        val start = System.nanoTime()
        val invokeResult = invoke(platform.client, platform.channel, chaincodeID, platform.user, platform.endorsementTimeout, function, codec, args: _*)(transient)
        invokeResult match {
            case Right(taskResultFuture) =>
                try {
                    taskResultFuture.get()
                    val totalDuration = (System.nanoTime() - start) / 1000000d
                    logger.debug(s"Successfully processed transaction duration: ${"%.3f Ms".format(totalDuration)}")
                    Right(())
                } catch {
                    case t: Throwable =>
                        val totalDuration = (System.nanoTime() - start) / 1000000d
                        logger.error(s"Transaction rejected, duration: ${"%.3f Ms".format(totalDuration)}: ${t.getMessage}")
                        Left(t.getMessage)
                }
            case Left(errorMsg) =>
                logger.debug(errorMsg)
                Left(errorMsg)
        }
    }

    // with transient
    def invokeAsync(platform: FabricPlatform, chaincodeID: ChaincodeID, function: String, codec: BinaryCodec, args: Any*)
      (implicit transient: Option[Map[String, Array[Byte]]] = None)
    : Unit = {
        val start = System.nanoTime()
        val invokeResult = invoke(platform.client, platform.channel, chaincodeID, platform.user, platform.endorsementTimeout, function, codec, args: _*)(transient)
        invokeResult match {
            case Right(taskResultFuture) =>
                try {
                    handle(taskResultFuture, (te, ex) => {
                        val totalDuration = (System.nanoTime() - start) / 1000000d
                        if (ex != null) {
                            logger.error(s"Transaction rejected, duration: ${"%.3f Ms".format(totalDuration)}: ${ex.getMessage}")
                        } else {
                            logger.debug(s"Successfully processed transaction duration: ${"%.3f Ms".format(totalDuration)}")
                        }
                    })
                } catch {
                    case t: Throwable => logger.debug(t.getMessage)
                }
            case Left(errorMsg) =>
                logger.debug(errorMsg)
        }
    }

    private def handle(task: CompletableFuture[TransactionEvent], f: (TransactionEvent, Throwable) => Unit): CompletableFuture[Unit] = {
        task.handle(new BiFunction[TransactionEvent, Throwable, Unit] {
            override def apply(t: TransactionEvent, u: Throwable): Unit = f(t, u)
        })
    }

}

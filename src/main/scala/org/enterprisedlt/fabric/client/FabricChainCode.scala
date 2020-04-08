package org.enterprisedlt.fabric.client

import java.lang.reflect.{InvocationHandler, Method, ParameterizedType, Proxy => JProxy}
import java.util.concurrent.CompletableFuture

import org.enterprisedlt.spec._
import org.hyperledger.fabric.sdk.Channel.DiscoveryOptions.createDiscoveryOptions
import org.hyperledger.fabric.sdk._

import scala.collection.JavaConverters._
import scala.reflect.{ClassTag, classTag}
import scala.util.Try

/**
 * @author Alexey Polubelov
 */
class FabricChainCode(
    fabricClient: HFClient,
    fabricChannel: Channel,
    fabricChainCodeID: ChaincodeID,
    codec: BinaryCodec,
    chaincodeServiceDiscovery: Boolean
) {
    type TransactionEvent = BlockEvent#TransactionEvent

    def rawQuery(function: String, args: Array[Array[Byte]], transient: Map[String, Array[Byte]] = Map.empty): ContractResult[Array[Byte]] = {
        val request = fabricClient.newQueryProposalRequest()
        request.setChaincodeID(fabricChainCodeID)
        request.setFcn(function)
        request.setArgs(args: _*)
        if (transient.nonEmpty) {
            request.setTransientMap(transient.asJava)
        }
        val responses = fabricChannel.queryByChaincode(request).asScala
        val responsesByStatus = responses.groupBy { response => response.getStatus }
        val failed = responsesByStatus.getOrElse(ChaincodeResponse.Status.FAILURE, List.empty)
        if (failed.nonEmpty) {
            Left(extractErrorMessage(failed.head))
        } else {
            Right(extractPayload(responses.head))
        }
    }

    def rawInvoke(function: String, args: Array[Array[Byte]], transient: Map[String, Array[Byte]] = Map.empty): ContractResult[CompletableFuture[Array[Byte]]] = {
        val request = fabricClient.newTransactionProposalRequest()
        request.setChaincodeID(fabricChainCodeID)
        request.setFcn(function)
        request.setArgs(args: _*)
        if (transient.nonEmpty) {
            request.setTransientMap(transient.asJava)
        }
        val responses = if (chaincodeServiceDiscovery) {
            fabricChannel.sendTransactionProposalToEndorsers(
                request,
                createDiscoveryOptions()
                  .setEndorsementSelector(ServiceDiscovery.EndorsementSelector.ENDORSEMENT_SELECTION_RANDOM)
                  .setForceDiscovery(true))
        } else fabricChannel.sendTransactionProposal(request)
        val responsesConsistencySets = SDKUtils.getProposalConsistencySets(responses)
        if (responsesConsistencySets.size() != 1) {
            val responsesByStatus = responses.asScala.groupBy(_.getStatus)
            val failed = responsesByStatus.getOrElse(ChaincodeResponse.Status.FAILURE, List.empty)
            if (failed.nonEmpty) {
                Left(extractErrorMessage(failed.head))
            }
            else {
                Left(s"Got inconsistent proposal responses [${responsesConsistencySets.size}]")
            }
        } else {
            Right(
                fabricChannel
                  .sendTransaction(responses, fabricClient.getUserContext)
                  .thenApply(
                      new ResultOverwrite[TransactionEvent, Array[Byte]](extractPayload(responses.iterator().next()))
                  )
            )
        }
    }

    class ResultOverwrite[TransactionEvent, T](value: T) extends java.util.function.Function[TransactionEvent, T]() {
        override def apply(x: TransactionEvent): T = value
    }

    private def extractPayload(response: ProposalResponse): Array[Byte] =
            Option(response.getProposalResponse)
              .flatMap(r => Option(r.getResponse))
              .flatMap(r => Option(r.getPayload))
              .flatMap(r => Option(r.toByteArray))
              .getOrElse(Array.empty)

    private def extractErrorMessage(response: ProposalResponse): String =
        Option(response.getProposalResponse)
          .flatMap(r => Option(r.getResponse))
          .flatMap(r => Option(r.getMessage))
          .getOrElse("General error occurred")


    def as[T: ClassTag]: T = {
        val clz = classTag[T].runtimeClass.asInstanceOf[Class[T]]
        JProxy
          .newProxyInstance(clz.getClassLoader, Array(clz), new CCHandler())
          .asInstanceOf[T]
    }

    class CCHandler extends InvocationHandler {
        override def invoke(proxy: Any, method: Method, args: Array[AnyRef]): AnyRef = {
            val function = method.getName
            method.getGenericReturnType match {
                case parameterizedType: ParameterizedType =>
                    val returnTypes = parameterizedType.getActualTypeArguments
                    val ResultType = returnTypes(1)
                    val (parameters, transient) = parseArgs(method, Option(args).getOrElse(Array.empty))
                    method.getAnnotation(classOf[ContractOperation]).value() match {
                        case OperationType.Query =>
                            rawQuery(function, parameters.map(codec.encode), transient.mapValues(codec.encode))
                              .map(value => codec.decode[AnyRef](value, ResultType))

                        case OperationType.Invoke =>
                            rawInvoke(function, parameters.map(codec.encode), transient.mapValues(codec.encode))
                              .flatMap(value => Try(value.get()).toEither.left.map(_.getMessage))
                              .map(value => ResultType.getTypeName match {
                                  case "scala.runtime.BoxedUnit" => ()
                                  case _ => codec.decode[AnyRef](value, ResultType)
                              })
                    }

                case other =>
                    throw new Exception(s"Unsupported return type: ${other.getTypeName}")
            }
        }
    }

    def parseArgs(method: Method, args: Array[AnyRef]): (Array[AnyRef], Map[String, AnyRef]) =
        Option(method.getParameters)
          .getOrElse(Array.empty)
          .zip(args)
          .foldLeft((Array.empty[AnyRef], Map.empty[String, AnyRef])) {
              case ((arguments, transient), (parameter, value)) =>
                  if (parameter.isAnnotationPresent(classOf[Transient]))
                      (arguments, transient + (parameter.getName -> value)) // put transient to transient map
                  else
                      (arguments :+ value, transient) // put non transient to parameters
          }

}

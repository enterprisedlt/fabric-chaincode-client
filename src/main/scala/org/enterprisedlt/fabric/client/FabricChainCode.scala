package org.enterprisedlt.fabric.client

import java.lang
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
 * @author Maxim Fedin
 *
 *
 */
class FabricChainCode(
    fabricClient: FabricClient,
    fabricChannel: Channel,
    fabricChainCodeID: ChaincodeID,
    codec: BinaryCodec,
    bootstrapOrderers: java.util.Collection[Orderer],
    discoveryForEndorsement: Boolean,
    discoveryForOrdering: Boolean
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
        val responsesByStatus = responses.groupBy(_.getStatus)
        val failed = responsesByStatus.getOrElse(ChaincodeResponse.Status.FAILURE, List.empty)
        val succeeded = responsesByStatus.getOrElse(ChaincodeResponse.Status.SUCCESS, List.empty)
        if (failed.nonEmpty && succeeded.isEmpty) {
            Left(extractErrorMessage(failed.head))
        } else {
            val succeddedConsistencySet = SDKUtils.getProposalConsistencySets(succeeded.asJavaCollection)
            if (succeddedConsistencySet.size() != 1) {
                Left(s"Got inconsistent proposal responses [${succeddedConsistencySet.size}]")
            } else {
                Right(extractPayload(succeeded.head))
            }
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
        val responses = if (discoveryForEndorsement) {
            fabricChannel.sendTransactionProposalToEndorsers(
                request,
                createDiscoveryOptions()
                  .setEndorsementSelector(ServiceDiscovery.EndorsementSelector.ENDORSEMENT_SELECTION_RANDOM)
                  .setForceDiscovery(true)
                  .setInspectResults(true)
            )
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
            val orderersToCommit = if (discoveryForOrdering) {
                fabricChannel.getOrderers
            } else {
                bootstrapOrderers
            }
            Right(
                fabricChannel
                  .sendTransaction(responses, orderersToCommit, fabricClient.getUserContext)
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

    /**
     * An instance of proxy is always new, not cached,
     * the maximum number of instances can be produced is only int max value
     **/
    def as[T: ClassTag]: T = {
        val clz = classTag[T].runtimeClass.asInstanceOf[Class[T]]
        val code = Counter.get()
        JProxy
          .newProxyInstance(clz.getClassLoader, Array(clz), new CCHandler(code))
          .asInstanceOf[T]
    }

    private def asBoolean(x: Boolean): lang.Boolean = java.lang.Boolean.valueOf(x)

    class CCHandler(code: Int) extends InvocationHandler {
        override def invoke(proxy: Any, method: Method, args: Array[AnyRef]): AnyRef = method.getName match {

            case f if f == "hashCode" => code.underlying()

            case f if f == "equals" => if (proxy == null || args(0) == null) {
                asBoolean(false)
            }

            else asBoolean(proxy.hashCode == args(0).hashCode)

            case function =>
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

package org.enterprisedlt.fabric.client

import java.lang.reflect.{InvocationHandler, Method, ParameterizedType, Proxy => JProxy}
import java.util.concurrent.CompletableFuture

import org.enterprisedlt.spec._
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
    codec: BinaryCodec
) {
    type TransactionEvent = BlockEvent#TransactionEvent

    def rawQuery(function: String, args: Array[Array[Byte]], transient: Map[String, Array[Byte]] = Map.empty): ContractResult[Array[Byte], Array[Byte]] = {
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
            ErrorResult(extractPayload(failed.head))
        } else {
            Success(extractPayload(responses.head))
        }
    }

    def rawInvoke(function: String, args: Array[Array[Byte]], transient: Map[String, Array[Byte]] = Map.empty): ContractResult[Array[Byte], CompletableFuture[Array[Byte]]] = {
        val request = fabricClient.newTransactionProposalRequest()
        request.setChaincodeID(fabricChainCodeID)
        request.setFcn(function)
        request.setArgs(args: _*)
        if (transient.nonEmpty) {
            request.setTransientMap(transient.asJava)
        }
        val responses = fabricChannel.sendTransactionProposal(request)
        val responsesConsistencySets = SDKUtils.getProposalConsistencySets(responses)
        if (responsesConsistencySets.size() != 1) {
            val responsesByStatus = responses.asScala.groupBy(_.getStatus)
            val failed = responsesByStatus.getOrElse(ChaincodeResponse.Status.FAILURE, List.empty)
            if (failed.nonEmpty) {
                ErrorResult(extractPayload(failed.head))
            }
            else {
                ExecutionError(s"Got inconsistent proposal responses [${responsesConsistencySets.size}]")
            }
        } else {
            Success(
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
          .flatMap(r => Option(r.getPayload))
          .flatMap(r => Option(r.toByteArray))
          .getOrElse(Array.empty)

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
                    val ErrorType = returnTypes(0).asInstanceOf[Class[_]]
                    val ResultType = returnTypes(1).asInstanceOf[Class[_]]
                    method.getAnnotation(classOf[ContractOperation]).value() match {
                        case OperationType.Query =>
                            rawQuery(function, args.map(codec.encode)) match {
                                case ee: ExecutionError[_, _] => ee
                                case ErrorResult(error) => ErrorResult(codec.decode(error, ErrorType))
                                case Success(value) => Success(codec.decode(value, ResultType))
                            }
                        case OperationType.Invoke =>
                            rawInvoke(function, args.map(codec.encode)) match {
                                case ee: ExecutionError[_, _] => ee
                                case ErrorResult(error) => ErrorResult(codec.decode(error, ErrorType))
                                case Success(value) =>
                                    ContractResultConversions.Try2QueryResult(
                                        Try(value.get()) // await for result
                                          .map(x => codec.decode(x, ResultType))
                                    )
                            }
                    }
                case other =>
                    throw new Exception(s"Unsupported return type: ${other.getTypeName}")
            }
        }
    }

}

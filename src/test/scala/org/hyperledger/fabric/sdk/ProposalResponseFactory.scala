package org.hyperledger.fabric.sdk

import org.hyperledger.fabric.sdk.transaction.TransactionContext

object ProposalResponseFactory {
    def newProposalResponse(transactionContext: TransactionContext, status: Int, message: String): ProposalResponse = new ProposalResponse(transactionContext, status, message)
}
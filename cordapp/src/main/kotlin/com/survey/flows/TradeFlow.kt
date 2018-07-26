import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.*

// *********
// * Trade flow *
// The mechanism for transferring ownership of survey
// in return for a payment
// *********
@InitiatingFlow
@StartableByRPC
class TradeFlow : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        // Flow impl
    }
}


package com.hemant.myapplication.pipeline

import android.util.Log
import com.hemant.myapplication.domain.DomainRegistry
import com.hemant.myapplication.model.RuntimeSnapshot
import com.hemant.myapplication.model.WidgetDocument

class Orchestrator(private val apiKey: String) {
    companion object {
        private const val TAG = "HEMANT_DBG"
    }

    @Throws(Exception::class)
    suspend fun generate(userQuery: String): Pair<WidgetDocument, RuntimeSnapshot> {
        try {
            Log.d(TAG, "Starting Orchestrator pipeline for user query: \"$userQuery\"")

            // 1. LLM-1 Capability Router (dynamically lists registry plugin definitions in the prompt)
            Log.d(TAG, "Phase 1: Running Capability Router (LLM-1)")
            val router = Llm1CapabilityRouter(apiKey)
            val routerOutput = router.route(userQuery)
            Log.d(TAG, "LLM-1 Router Output: domain='${routerOutput.domain}', skills=${routerOutput.skills}, tools=${routerOutput.tools}, decisionQuery=${routerOutput.isDecisionQuery}")

            // 2. Fetch the corresponding domain adapter from registry
            Log.d(TAG, "Phase 2: Fetching adapter for domain: '${routerOutput.domain}'")
            val adapter = DomainRegistry.getAdapter(routerOutput.domain)
            Log.d(TAG, "Loaded Domain Adapter: ${adapter.domainName()} (category: ${adapter.categoryName()})")

            // 3. LLM-2 Variable Planner (running tool loop with registry and domain tools)
            Log.d(TAG, "Phase 3: Running Variable Planner (LLM-2) tool loop...")
            val planner = Llm2VariablePlanner(apiKey)
            val variablePlan = planner.plan(userQuery, routerOutput, adapter)
            Log.d(TAG, "LLM-2 Variable Plan output: status='${variablePlan.status}', variables=${variablePlan.variables.length()} items, assets=${variablePlan.assets}")

            // 4. Variable Resolver (delegates to the active DomainAdapter tool execution)
            Log.d(TAG, "Phase 4: Resolving tool-backed variables...")
            val resolver = VariableResolver(adapter)
            val resolvedValues = resolver.resolve(variablePlan)
            Log.d(TAG, "Resolved values: $resolvedValues")

            // 5. LLM-3 Layout Generator (produces dynamic A2UI layoutspec)
            Log.d(TAG, "Phase 5: Running Layout Generator (LLM-3)")
            val generator = Llm3LayoutGenerator(apiKey)
            val document = generator.generateLayout(userQuery, resolvedValues, routerOutput.domain)
            Log.d(TAG, "LLM-3 Layout generated: ID='${document.id}', Title='${document.title}'")

            val snapshot = RuntimeSnapshot("ready", "4x3", resolvedValues)
            Log.d(TAG, "Orchestrator pipeline completed successfully.")

            return Pair(document, snapshot)
        } catch (e: Exception) {
            Log.e(TAG, "Orchestrator generation failed", e)
            throw e
        }
    }
}

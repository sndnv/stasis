package stasis.client_android.activities.helpers

import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import stasis.client_android.lib.model.server.datasets.DatasetDefinitionId
import stasis.client_android.persistence.rules.RuleViewModel
import stasis.client_android.providers.ProviderContext
import stasis.client_android.utils.LiveDataExtensions.liveData
import stasis.client_android.utils.LiveDataExtensions.observeOnce
import stasis.client_android.utils.Permissions.needsExtraPermissions
import stasis.client_android.utils.Permissions.requestMissingPermissions

object Backups {
    fun Fragment.startBackup(
        rulesModel: RuleViewModel,
        providerContext: ProviderContext,
        definitionId: DatasetDefinitionId,
        onOperationsPending: () -> Unit,
        onOperationStarted: (Int) -> Unit,
        onOperationCompleted: (Int, Throwable?) -> Unit,
    ) {
        if (activity.needsExtraPermissions()) {
            activity?.requestMissingPermissions()
        } else {
            rulesModel.rules.observeOnce(this) { rulesList ->
                val rules = rulesList.groupBy { it.definition }

                liveData { providerContext.executor.active().isNotEmpty() }
                    .observe(viewLifecycleOwner) { operationsPending ->
                        if (operationsPending) {
                            onOperationsPending()
                        } else {
                            val backupId = -2

                            onOperationStarted(backupId)

                            lifecycleScope.launch {
                                providerContext.executor.startBackupWithRules(
                                    definition = definitionId,
                                    rules = rules.getOrElse(definitionId) { rules.getOrElse(null) { emptyList() } }
                                ) { e ->
                                    onOperationCompleted(backupId, e)
                                }
                            }
                        }
                    }
            }
        }
    }
}
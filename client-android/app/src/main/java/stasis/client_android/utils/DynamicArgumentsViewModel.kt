package stasis.client_android.utils

import androidx.lifecycle.ViewModel

class DynamicArgumentsViewModel : ViewModel() {
    val arguments: DynamicArguments.Provider.Arguments = DynamicArguments.Provider.Arguments()
}

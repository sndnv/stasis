package stasis.client_android.persistence.rules

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import stasis.client_android.lib.collection.rules.Rule
import javax.inject.Inject

class RuleViewModel @Inject constructor(application: Application) : AndroidViewModel(application) {
    private val repo: RuleRepository = RuleRepository(application)

    val rules: LiveData<List<Rule>> = repo.rules

    fun put(rule: Rule): CompletableDeferred<Long> {
        val response = CompletableDeferred<Long>()
        viewModelScope.launch(Dispatchers.IO) {
            response.complete(repo.put(rule))
        }
        return response
    }

    fun put(rule: RuleEntity): CompletableDeferred<Long> {
        val response = CompletableDeferred<Long>()
        viewModelScope.launch(Dispatchers.IO) {
            response.complete(repo.put(rule))
        }
        return response
    }

    fun delete(id: Long): CompletableDeferred<Unit> {
        val response = CompletableDeferred<Unit>()
        viewModelScope.launch(Dispatchers.IO) {
            repo.delete(id)
            response.complete(Unit)
        }
        return response
    }

    fun bootstrap(): CompletableDeferred<Unit> {
        val response = CompletableDeferred<Unit>()
        viewModelScope.launch(Dispatchers.IO) {
            repo.bootstrap()
            response.complete(Unit)
        }
        return response
    }

    fun clear(): CompletableDeferred<Unit> {
        val response = CompletableDeferred<Unit>()
        viewModelScope.launch(Dispatchers.IO) {
            repo.clear()
            response.complete(Unit)
        }
        return response
    }
}

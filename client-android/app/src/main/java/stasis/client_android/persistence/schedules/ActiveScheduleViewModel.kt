package stasis.client_android.persistence.schedules

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import stasis.client_android.lib.ops.scheduling.ActiveSchedule
import javax.inject.Inject

class ActiveScheduleViewModel @Inject constructor(application: Application) : AndroidViewModel(application) {
    private val repo: ActiveScheduleRepository = ActiveScheduleRepository(application)

    val configured: LiveData<List<ActiveSchedule>> = repo.schedules

    fun put(schedule: ActiveSchedule): CompletableDeferred<Long> {
        val response = CompletableDeferred<Long>()
        viewModelScope.launch(Dispatchers.IO) {
            response.complete(repo.put(schedule))
        }
        return response
    }

    fun put(schedule: ActiveScheduleEntity): CompletableDeferred<Long> {
        val response = CompletableDeferred<Long>()
        viewModelScope.launch(Dispatchers.IO) {
            response.complete(repo.put(schedule))
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

    fun clear(): CompletableDeferred<Unit> {
        val response = CompletableDeferred<Unit>()
        viewModelScope.launch(Dispatchers.IO) {
            repo.clear()
            response.complete(Unit)
        }
        return response
    }
}

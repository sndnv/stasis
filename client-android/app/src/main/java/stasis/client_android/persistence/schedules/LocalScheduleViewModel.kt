package stasis.client_android.persistence.schedules

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import stasis.client_android.lib.model.server.schedules.Schedule
import stasis.client_android.lib.model.server.schedules.ScheduleId
import javax.inject.Inject

class LocalScheduleViewModel @Inject constructor(application: Application) : AndroidViewModel(application) {
    private val repo: LocalScheduleRepository = LocalScheduleRepository(application)

    val configured: LiveData<List<Schedule>> = repo.schedules

    fun put(schedule: Schedule): CompletableDeferred<Unit> {
        val response = CompletableDeferred<Unit>()
        viewModelScope.launch(Dispatchers.IO) {
            response.complete(repo.put(schedule))
        }
        return response
    }

    fun put(schedule: LocalScheduleEntity): CompletableDeferred<Unit> {
        val response = CompletableDeferred<Unit>()
        viewModelScope.launch(Dispatchers.IO) {
            response.complete(repo.put(schedule))
        }
        return response
    }

    fun delete(id: ScheduleId): CompletableDeferred<Unit> {
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

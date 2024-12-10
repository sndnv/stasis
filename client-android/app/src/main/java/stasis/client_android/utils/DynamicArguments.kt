package stasis.client_android.utils

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import java.util.concurrent.ConcurrentHashMap

object DynamicArguments {
    interface ArgumentSet

    interface Provider {
        val providedArguments: Arguments

        class Arguments {
            val storage: ConcurrentHashMap<String, MutableLiveData<ArgumentSet>> = ConcurrentHashMap()

            fun <T : ArgumentSet> put(key: String, arguments: T) {
                storage.getOrPut(key) { MutableLiveData<ArgumentSet>() }.postValue(arguments)
            }

            inline fun <reified T : ArgumentSet> get(key: String): LiveData<T> =
                storage.getOrPut(key) { MutableLiveData<ArgumentSet>() }.map { args ->
                    when (args) {
                        is T -> args
                        else -> throw IllegalArgumentException(
                            "Argument set of type [${T::class.java.name}] requested " +
                                    "but [${args.javaClass.name}] found for key [$key]"
                        )
                    }
                }
        }
    }

    interface Receiver {
        val argumentsKey: String
        val receiver: Fragment
    }

    inline fun <reified T : Receiver> Receiver.withArgumentsId(id: String): T {
        receiver.arguments = Bundle().apply { putString(argumentsKey, id) }
        return this as T
    }

    inline fun <reified T : ArgumentSet> Receiver.pullArguments(): LiveData<T> =
        when (val provider = receiver.parentFragment as? Provider) {
            null -> throw IllegalArgumentException(
                "Parent fragment [${receiver.parentFragment?.javaClass?.simpleName}] is not a valid argument provider"
            )

            else -> when (val k = receiver.requireArguments().getString(argumentsKey)) {
                null -> throw IllegalArgumentException("Arguments key with ID [${argumentsKey}] not found")
                else -> provider.providedArguments.get<T>(k)
            }
        }
}
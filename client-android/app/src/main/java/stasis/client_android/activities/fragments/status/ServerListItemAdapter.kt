package stasis.client_android.activities.fragments.status

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import stasis.client_android.R
import stasis.client_android.activities.helpers.DateTimeExtensions.formatAsFullDateTime
import stasis.client_android.databinding.ListItemServerConnectionBinding
import stasis.client_android.tracking.TrackerView

class ServerListItemAdapter : RecyclerView.Adapter<ServerListItemAdapter.ItemViewHolder>() {
    private var servers = emptyList<Pair<String, TrackerView.ServerState>>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ListItemServerConnectionBinding.inflate(inflater, parent, false)
        return ItemViewHolder(parent.context, binding)
    }

    override fun getItemCount(): Int = servers.size

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val (server, state) = servers[position]
        holder.bind(server, state)
    }

    class ItemViewHolder(
        private val context: Context,
        private val binding: ListItemServerConnectionBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(server: String, state: TrackerView.ServerState) {
            binding.serverInfo.text = context.getString(
                if (state.reachable) R.string.server_field_content_info_reachable
                else R.string.server_field_content_info_unreachable,
                server
            )

            binding.serverLastUpdate.text = context.getString(
                R.string.server_field_content_last_update,
                state.timestamp.formatAsFullDateTime(context)
            )
        }
    }

    internal fun setServers(servers: Map<String, TrackerView.ServerState>) {
        this.servers = servers.toList()

        notifyDataSetChanged()
    }
}
